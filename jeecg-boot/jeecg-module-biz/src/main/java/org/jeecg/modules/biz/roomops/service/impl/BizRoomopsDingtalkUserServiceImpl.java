package org.jeecg.modules.biz.roomops.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsDingtalkUser;
import org.jeecg.modules.biz.roomops.mapper.BizRoomopsDingtalkUserMapper;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsDingtalkUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
public class BizRoomopsDingtalkUserServiceImpl extends ServiceImpl<BizRoomopsDingtalkUserMapper, BizRoomopsDingtalkUser> implements IBizRoomopsDingtalkUserService {

  @Value("${jeecg.roomops.sync.vpsBaseUrl:}")
  private String vpsBaseUrl;

  @Value("${jeecg.roomops.sync.pullToken:}")
  private String pullToken;

  @Override
  public DingtalkUserSyncResult syncFromDingtalk() {
    if (vpsBaseUrl == null || vpsBaseUrl.isEmpty()) {
      throw new IllegalStateException("未配置 jeecg.roomops.sync.vpsBaseUrl");
    }
    if (pullToken == null || pullToken.isEmpty()) {
      throw new IllegalStateException("未配置 jeecg.roomops.sync.pullToken");
    }

    DingtalkUserSyncResult result = new DingtalkUserSyncResult();
    try {
      JSONObject response = postVps("/api/dingtalk/users/sync");
      if (!Boolean.TRUE.equals(response.getBoolean("ok"))) {
        throw new IllegalStateException("小程序端通讯录同步失败：" + response.toJSONString());
      }
      result.setDepartmentCount(response.getIntValue("departmentCount"));
      JSONArray users = response.getJSONArray("users");
      if (users == null) {
        return result;
      }
      result.setFetchedUserCount(users.size());
      for (int i = 0; i < users.size(); i++) {
        upsertUser(users.getJSONObject(i), result);
      }
      return result;
    } catch (Exception e) {
      log.error("Roomops sync dingtalk users failed", e);
      throw new IllegalStateException("同步钉钉用户失败：" + e.getMessage(), e);
    }
  }

  private void upsertUser(JSONObject payload, DingtalkUserSyncResult result) {
    String userid = text(payload, "userid", "userId", "dingtalk_userid");
    String name = text(payload, "name", "realName");
    if (userid.isEmpty() || name.isEmpty()) {
      result.setSkippedCount(result.getSkippedCount() + 1);
      return;
    }

    BizRoomopsDingtalkUser existing = getOne(new QueryWrapper<BizRoomopsDingtalkUser>().eq("dingtalk_userid", userid), false);
    if (existing == null) {
      existing = getOne(new QueryWrapper<BizRoomopsDingtalkUser>().eq("name", name).likeRight("dingtalk_userid", "name:"), false);
    }

    Date now = new Date();
    BizRoomopsDingtalkUser user = existing == null ? new BizRoomopsDingtalkUser() : existing;
    user.setDingtalkUserid(userid);
    user.setDingtalkUnionid(defaultText(text(payload, "unionid", "unionId"), user.getDingtalkUnionid()));
    user.setName(name);
    user.setMobile(defaultText(text(payload, "mobile"), user.getMobile()));
    user.setAvatar(defaultText(text(payload, "avatar"), user.getAvatar()));
    user.setDeptId(defaultText(text(payload, "deptId", "dept_id"), user.getDeptId()));
    user.setDeptName(defaultText(text(payload, "deptName", "dept_name"), user.getDeptName()));
    user.setDefaultDomainCode(defaultText(user.getDefaultDomainCode(), "core_network"));
    user.setDefaultDomainShortCode(defaultText(user.getDefaultDomainShortCode(), "CORE"));
    user.setDefaultDomainName(defaultText(user.getDefaultDomainName(), "核心网"));
    user.setDefaultRegionCode(defaultText(user.getDefaultRegionCode(), "TY"));
    user.setDefaultRegionName(defaultText(user.getDefaultRegionName(), "太原"));
    user.setActive("1");
    user.setDingtalkSynced(1);
    user.setLastSyncTime(now);
    user.setUpdateTime(now);

    if (existing == null) {
      user.setCreateTime(now);
      save(user);
      result.setCreatedCount(result.getCreatedCount() + 1);
    } else {
      updateById(user);
      result.setUpdatedCount(result.getUpdatedCount() + 1);
    }
  }

  private JSONObject postVps(String path) throws Exception {
    String normalizedBase = vpsBaseUrl.endsWith("/") ? vpsBaseUrl.substring(0, vpsBaseUrl.length() - 1) : vpsBaseUrl;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    HttpURLConnection connection = (HttpURLConnection) new URL(normalizedBase + normalizedPath).openConnection(java.net.Proxy.NO_PROXY);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("X-Roomops-Pull-Token", pullToken);
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(60000);
    connection.setDoOutput(true);
    try (OutputStream outputStream = connection.getOutputStream()) {
      outputStream.write("{}".getBytes(StandardCharsets.UTF_8));
    }

    int status = connection.getResponseCode();
    try (InputStream inputStream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      if (inputStream != null) {
        inputStream.transferTo(outputStream);
      }
      String body = outputStream.toString(StandardCharsets.UTF_8);
      JSONObject response = null;
      try {
        response = JSON.parseObject(body);
      } catch (Exception ignored) {
        // keep null and report the raw body in the error below
      }
      if (status < 200 || status >= 300) {
        throw new IllegalStateException("小程序端通讯录同步请求失败：" + status + " " + body);
      }
      if (response == null) {
        throw new IllegalStateException("小程序端通讯录同步返回格式错误：" + body);
      }
      return response;
    }
  }

  private String text(JSONObject object, String... keys) {
    if (object == null || keys == null) {
      return "";
    }
    for (String key : keys) {
      Object value = object.get(key);
      if (value != null) {
        return String.valueOf(value).trim();
      }
    }
    return "";
  }

  private String defaultText(String value, String defaultValue) {
    return value == null || value.isEmpty() ? defaultValue : value;
  }
}
