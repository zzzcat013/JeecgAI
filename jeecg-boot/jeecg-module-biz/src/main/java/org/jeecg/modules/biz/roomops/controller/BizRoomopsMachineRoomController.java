package org.jeecg.modules.biz.roomops.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsMachineRoom;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsMachineRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/roomops/machineRoom")
public class BizRoomopsMachineRoomController {
  @Autowired
  private IBizRoomopsMachineRoomService bizRoomopsMachineRoomService;

  @Value("${jeecg.roomops.sync.vpsBaseUrl:}")
  private String vpsBaseUrl;

  @Value("${jeecg.roomops.sync.pullToken:}")
  private String pullToken;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsMachineRoom entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsMachineRoom> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("create_time");
    IPage<BizRoomopsMachineRoom> pageList = bizRoomopsMachineRoomService.page(new Page<>(pageNo, pageSize), queryWrapper);
    return Result.ok(pageList);
  }

  @PostMapping(value = "/add")
  @RequiresPermissions("roomops:machineRoom:edit")
  public Result<?> add(@RequestBody BizRoomopsMachineRoom entity) {
    bizRoomopsMachineRoomService.save(entity);
    tryPushAll();
    return Result.ok("添加成功！");
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:machineRoom:edit")
  public Result<?> edit(@RequestBody BizRoomopsMachineRoom entity) {
    bizRoomopsMachineRoomService.updateById(entity);
    tryPushAll();
    return Result.ok("修改成功!");
  }

  @DeleteMapping(value = "/delete")
  @RequiresPermissions("roomops:machineRoom:edit")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsMachineRoomService.removeById(id);
    tryPushAll();
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  @RequiresPermissions("roomops:machineRoom:edit")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsMachineRoomService.removeByIds(Arrays.asList(ids.split(",")));
    tryPushAll();
    return Result.ok("批量删除成功！");
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    return Result.ok(bizRoomopsMachineRoomService.getById(id));
  }

  @PostMapping(value = "/push")
  @RequiresPermissions("roomops:machineRoom:edit")
  public Result<?> push() {
    pushAll();
    return Result.ok("机房配置已同步到小程序前置服务");
  }

  private void tryPushAll() {
    try {
      pushAll();
    } catch (Exception e) {
      log.warn("机房配置同步到 VPS 失败", e);
    }
  }

  private void pushAll() {
    if (vpsBaseUrl == null || vpsBaseUrl.isEmpty() || pullToken == null || pullToken.isEmpty()) {
      throw new IllegalStateException("VPS 同步地址或密钥未配置");
    }
    List<BizRoomopsMachineRoom> rooms = bizRoomopsMachineRoomService.list(
        new QueryWrapper<BizRoomopsMachineRoom>().eq("status", "1").orderByAsc("room_id"));
    JSONArray roomArray = new JSONArray();
    for (BizRoomopsMachineRoom room : rooms) {
      JSONObject item = new JSONObject();
      item.put("roomId", room.getRoomId());
      item.put("roomName", room.getRoomName());
      item.put("remark", room.getRemark());
      item.put("qrCode", room.getQrCode());
      item.put("latitude", room.getLatitude());
      item.put("longitude", room.getLongitude());
      item.put("allowedRadiusM", room.getAllowedRadiusM());
      item.put("maxAccuracyM", room.getMaxAccuracyM());
      roomArray.add(item);
    }
    JSONObject payload = new JSONObject();
    payload.put("rooms", roomArray);
    try {
      String base = vpsBaseUrl.endsWith("/") ? vpsBaseUrl.substring(0, vpsBaseUrl.length() - 1) : vpsBaseUrl;
      HttpURLConnection connection = (HttpURLConnection) new URL(base + "/api/machine-rooms/push")
          .openConnection(java.net.Proxy.NO_PROXY);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      connection.setRequestProperty("X-Roomops-Pull-Token", pullToken);
      connection.setConnectTimeout(10000);
      connection.setReadTimeout(30000);
      connection.setDoOutput(true);
      connection.getOutputStream().write(payload.toJSONString().getBytes(StandardCharsets.UTF_8));
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) {
        try (InputStream input = connection.getErrorStream()) {
          String body = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
          throw new IllegalStateException("VPS 返回 " + status + ": " + body);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("机房配置同步失败：" + e.getMessage(), e);
    }
  }
}
