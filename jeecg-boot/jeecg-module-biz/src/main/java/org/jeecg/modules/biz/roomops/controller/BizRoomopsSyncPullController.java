package org.jeecg.modules.biz.roomops.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsDingtalkUser;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsPhoto;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsRecord;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsSyncLog;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsDingtalkUserService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsPhotoService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsRecordService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsSyncLogService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/roomops/sync")
public class BizRoomopsSyncPullController {
  private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Autowired
  private IBizRoomopsRecordService recordService;

  @Autowired
  private IBizRoomopsPhotoService photoService;

  @Autowired
  private IBizRoomopsSyncLogService syncLogService;

  @Autowired
  private IBizRoomopsDingtalkUserService dingtalkUserService;

  @Autowired
  private IBizRoomopsTaskService taskService;

  @Value("${jeecg.roomops.sync.vpsBaseUrl:}")
  private String vpsBaseUrl;

  @Value("${jeecg.roomops.sync.pullToken:}")
  private String pullToken;

  @Value("${jeecg.roomops.sync.notifyToken:}")
  private String notifyToken;

  @Value("${jeecg.minio.minio_url:}")
  private String minioUrl;

  @Value("${jeecg.minio.minio_name:}")
  private String minioName;

  @Value("${jeecg.minio.minio_pass:}")
  private String minioPass;

  @Value("${jeecg.roomops.minio.bucketName:room-check-docs}")
  private String minioBucketName;

  @IgnoreAuth
  @PostMapping("/pull")
  public Result<PullResult> pull(@RequestParam(name = "limit", defaultValue = "20") Integer limit,
                                 @RequestHeader(value = "X-Roomops-Notify-Token", required = false) String token) {
    Result<PullResult> tokenCheck = checkNotifyToken(token);
    if (tokenCheck != null) {
      return tokenCheck;
    }
    return doPull(limit);
  }

  @IgnoreAuth
  @PostMapping("/notify")
  public Result<PullResult> notify(@RequestHeader(value = "X-Roomops-Notify-Token", required = false) String token) {
    Result<PullResult> tokenCheck = checkNotifyToken(token);
    if (tokenCheck != null) {
      return tokenCheck;
    }
    return doPull(20);
  }

  public Result<PullResult> pullFromVps(Integer limit) {
    Result<PullResult> result = doPull(limit);
    try {
      taskService.pullTaskUpdatesFromVps();
    } catch (Exception e) {
      log.error("Task pull from VPS failed", e);
    }
    return result;
  }

  private Result<PullResult> checkNotifyToken(String token) {
    if (notifyToken == null || notifyToken.isEmpty()) {
      return Result.error("未配置 jeecg.roomops.sync.notifyToken");
    }
    if (!notifyToken.equals(token)) {
      return Result.error("同步通知密钥不正确");
    }
    return null;
  }

  private Result<PullResult> doPull(Integer limit) {
    if (vpsBaseUrl == null || vpsBaseUrl.isEmpty()) {
      return Result.error("未配置 jeecg.roomops.sync.vpsBaseUrl");
    }
    if (pullToken == null || pullToken.isEmpty()) {
      return Result.error("未配置 jeecg.roomops.sync.pullToken");
    }

    PullResult result = new PullResult();
    String batchId = "ROOMOPS-PULL-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now(SHANGHAI_ZONE));
    try {
      JSONObject pending = getJson("/api/sync/pending?limit=" + Math.min(Math.max(limit, 1), 100));
      JSONArray records = pending.getJSONArray("records");
      if (records == null || records.isEmpty()) {
        result.setSyncBatchId(batchId);
        result.setPulled(0);
        result.setSucceeded(0);
        result.setFailed(0);
        return Result.ok(result);
      }

      int succeeded = 0;
      int failed = 0;
      for (int i = 0; i < records.size(); i++) {
        JSONObject recordJson = records.getJSONObject(i);
        String recordId = text(recordJson, "record_id", "recordId");
        try {
          syncOne(batchId, recordJson);
          ack(recordId, "synced", "");
          succeeded++;
        } catch (Exception e) {
          log.error("Roomops pull failed, recordId={}", recordId, e);
          writeSyncLog(batchId, recordId, text(recordJson, "business_type", "businessType"), "failed", e.getMessage());
          ack(recordId, "failed", e.getMessage());
          failed++;
        }
      }

      result.setSyncBatchId(batchId);
      result.setPulled(records.size());
      result.setSucceeded(succeeded);
      result.setFailed(failed);
      return Result.ok(result);
    } catch (Exception e) {
      log.error("Roomops pull batch failed", e);
      return Result.error("拉取失败：" + e.getMessage());
    }
  }

  private void syncOne(String batchId, JSONObject recordJson) throws Exception {
    String recordId = text(recordJson, "record_id", "recordId");
    String businessType = defaultText(text(recordJson, "business_type", "businessType"), "inspection");
    BizRoomopsRecord record = buildRecord(recordJson, recordId, businessType);
    upsertRecord(record);
    taskService.markSubmitted(recordId, record.getInspectorName(), record.getDingtalkUserid());
    upsertDingtalkUser(record);

    JSONArray photos = recordJson.getJSONArray("photos");
    if (photos != null) {
      for (int i = 0; i < photos.size(); i++) {
        JSONObject photoJson = photos.getJSONObject(i);
        BizRoomopsPhoto photo = buildPhoto(photoJson, recordJson, recordId);
        String downloadUrl = text(photoJson, "download_url", "downloadUrl");
        if (!downloadUrl.isEmpty()) {
          byte[] bytes = getBytes(downloadUrl);
          String objectName = buildObjectName(recordId, photo.getStoredFilename());
          uploadToMinio(objectName, bytes, defaultText(photo.getContentType(), "image/jpeg"));
          photo.setStoragePath(objectName);
          photo.setFileSize((long) bytes.length);
        }
        upsertPhoto(photo);
      }
    }

    writeSyncLog(batchId, recordId, businessType, "success", "");
  }

  private BizRoomopsRecord buildRecord(JSONObject payload, String recordId, String businessType) {
    BizRoomopsRecord record = new BizRoomopsRecord();
    record.setRecordId(recordId);
    record.setBusinessType(businessType);
    record.setDomainCode("core_network");
    record.setDomainShortCode("CORE");
    record.setDomainName("核心网");
    record.setRegionCode("TY");
    record.setRegionName("太原");
    record.setRoomId(text(payload, "room_id", "roomId"));
    record.setRoomName(text(payload, "room_name", "roomName"));
    record.setInspectorName(text(payload, "inspector_name", "inspectorName"));
    record.setDingtalkUserid(text(payload, "dingtalk_userid", "dingtalkUserid"));
    record.setDingtalkUnionid(text(payload, "dingtalk_unionid", "dingtalkUnionid"));
    record.setLatitude(decimal(text(payload, "latitude")));
    record.setLongitude(decimal(text(payload, "longitude")));
    record.setAccuracy(decimal(text(payload, "accuracy")));
    record.setCapturedAt(date(text(payload, "captured_at", "capturedAt")));
    record.setSubmittedAt(defaultDate(date(text(payload, "submitted_at", "submittedAt")), now()));
    record.setEnvironmentStatus(text(payload, "environment_status", "environmentStatus"));
    record.setDeviceStatus(text(payload, "device_status", "deviceStatus"));
    record.setExceptionDesc(text(payload, "exception_desc", "exceptionDesc"));
    record.setUploadMode(text(payload, "upload_mode", "uploadMode"));
    record.setSource(defaultText(text(payload, "source"), "vps"));
    record.setFaultOrderNo(text(payload, "fault_order_no", "faultOrderNo"));
    record.setHandlingResult(text(payload, "handling_result", "handlingResult"));
    record.setConstructionContent(text(payload, "construction_content", "constructionContent"));
    record.setRemainingIssues(text(payload, "remaining_issues", "remainingIssues"));
    record.setRemarkNote(text(payload, "remark_note", "remarkNote"));
    record.setRawFormJson(defaultText(text(payload, "raw_form_json", "rawFormJson"), payload.toJSONString()));
    record.setUpdateTime(now());
    return record;
  }

  private BizRoomopsPhoto buildPhoto(JSONObject payload, JSONObject recordPayload, String recordId) {
    BizRoomopsPhoto photo = new BizRoomopsPhoto();
    photo.setRecordId(recordId);
    photo.setPhotoIndex(integer(text(payload, "photo_index", "photoIndex"), 1));
    photo.setPhotoTotal(integer(text(payload, "photo_total", "photoTotal"), 1));
    photo.setOriginalFilename(text(payload, "original_filename", "originalFilename"));
    photo.setStoredFilename(defaultText(text(payload, "stored_filename", "storedFilename"), buildStoredFilename(recordId, photo.getPhotoIndex(), photo.getOriginalFilename())));
    photo.setStoragePath(text(payload, "storage_path", "storagePath"));
    photo.setContentType(text(payload, "content_type", "contentType"));
    photo.setFileSize(longValue(text(payload, "file_size", "fileSize"), null));
    photo.setPhotoCapturedAt(defaultDate(
        date(text(payload, "photo_captured_at", "photoCapturedAt")),
        date(text(recordPayload, "captured_at", "capturedAt"))));
    photo.setPhotoLatitude(defaultDecimal(
        decimal(text(payload, "photo_latitude", "photoLatitude")),
        decimal(text(recordPayload, "latitude"))));
    photo.setPhotoLongitude(defaultDecimal(
        decimal(text(payload, "photo_longitude", "photoLongitude")),
        decimal(text(recordPayload, "longitude"))));
    photo.setPhotoAccuracy(defaultDecimal(
        decimal(text(payload, "photo_accuracy", "photoAccuracy")),
        decimal(text(recordPayload, "accuracy"))));
    photo.setPhotoRemark(defaultText(
        text(payload, "photo_remark", "photoRemark"),
        defaultText(text(recordPayload, "exception_desc", "exceptionDesc"), text(recordPayload, "remark_note", "remarkNote"))));
    photo.setWatermarked(integer(text(payload, "watermarked"), 0));
    photo.setUploadedAt(defaultDate(date(text(payload, "uploaded_at", "uploadedAt")), now()));
    photo.setUpdateTime(now());
    return photo;
  }

  private void upsertRecord(BizRoomopsRecord record) {
    BizRoomopsRecord existing = recordService.getOne(new QueryWrapper<BizRoomopsRecord>().eq("record_id", record.getRecordId()), false);
    if (existing == null) {
      record.setCreateTime(now());
      recordService.save(record);
    } else {
      record.setId(existing.getId());
      record.setCreateTime(existing.getCreateTime());
      recordService.updateById(record);
    }
  }

  private void upsertPhoto(BizRoomopsPhoto photo) {
    BizRoomopsPhoto existing = photoService.getOne(new QueryWrapper<BizRoomopsPhoto>().eq("record_id", photo.getRecordId()).eq("photo_index", photo.getPhotoIndex()), false);
    if (existing == null) {
      photo.setCreateTime(now());
      photoService.save(photo);
    } else {
      photo.setId(existing.getId());
      photo.setCreateTime(existing.getCreateTime());
      photoService.updateById(photo);
    }
  }

  private void upsertDingtalkUser(BizRoomopsRecord record) {
    String name = defaultText(record.getInspectorName(), "");
    String userid = defaultText(record.getDingtalkUserid(), "");
    String unionid = defaultText(record.getDingtalkUnionid(), "");
    if (name.isEmpty() && userid.isEmpty() && unionid.isEmpty()) {
      return;
    }

    QueryWrapper<BizRoomopsDingtalkUser> queryWrapper = new QueryWrapper<>();
    if (!userid.isEmpty()) {
      queryWrapper.eq("dingtalk_userid", userid);
    } else if (!unionid.isEmpty()) {
      queryWrapper.eq("dingtalk_unionid", unionid);
    } else {
      queryWrapper.eq("name", name);
    }
    BizRoomopsDingtalkUser existing = dingtalkUserService.getOne(queryWrapper, false);
    if (existing == null && !name.isEmpty()) {
      existing = dingtalkUserService.getOne(new QueryWrapper<BizRoomopsDingtalkUser>().eq("name", name), false);
    }

    BizRoomopsDingtalkUser user = existing == null ? new BizRoomopsDingtalkUser() : existing;
    user.setDingtalkUserid(defaultText(userid, defaultText(user.getDingtalkUserid(), "name:" + name)));
    user.setDingtalkUnionid(defaultText(unionid, user.getDingtalkUnionid()));
    user.setName(defaultText(name, defaultText(user.getName(), userid)));
    user.setDefaultDomainCode(defaultText(user.getDefaultDomainCode(), "core_network"));
    user.setDefaultDomainShortCode(defaultText(user.getDefaultDomainShortCode(), "CORE"));
    user.setDefaultDomainName(defaultText(user.getDefaultDomainName(), "核心网"));
    user.setDefaultRegionCode(defaultText(user.getDefaultRegionCode(), "TY"));
    user.setDefaultRegionName(defaultText(user.getDefaultRegionName(), "太原"));
    user.setActive(defaultText(user.getActive(), "1"));
    user.setDingtalkSynced(user.getDingtalkSynced() == null ? 0 : user.getDingtalkSynced());
    user.setLastSyncTime(now());
    user.setUpdateTime(now());
    if (existing == null) {
      user.setCreateTime(now());
      dingtalkUserService.save(user);
    } else {
      dingtalkUserService.updateById(user);
    }
  }

  private void writeSyncLog(String batchId, String recordId, String businessType, String status, String error) {
    BizRoomopsSyncLog syncLog = new BizRoomopsSyncLog();
    syncLog.setSyncBatchId(batchId);
    syncLog.setSourceRecordId(recordId);
    syncLog.setRecordId(recordId);
    syncLog.setBusinessType(businessType);
    syncLog.setSyncDirection("vps_to_jeecg");
    syncLog.setSyncStatus(status);
    syncLog.setRetryCount(0);
    syncLog.setErrorMessage(error);
    syncLog.setStartedAt(now());
    syncLog.setFinishedAt(now());
    syncLog.setCreateTime(now());
    syncLogService.save(syncLog);
  }

  private JSONObject getJson(String path) throws Exception {
    return JSON.parseObject(new String(getBytes(path), StandardCharsets.UTF_8));
  }

  private byte[] getBytes(String path) throws Exception {
    HttpURLConnection connection = openConnection(path, "GET");
    int status = connection.getResponseCode();
    if (status < 200 || status >= 300) {
      throw new IllegalStateException("VPS请求失败：" + status + " " + path);
    }
    try (InputStream inputStream = connection.getInputStream(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      inputStream.transferTo(outputStream);
      return outputStream.toByteArray();
    }
  }

  private void ack(String recordId, String status, String error) throws Exception {
    JSONObject payload = new JSONObject();
    payload.put("recordId", recordId);
    payload.put("status", status);
    payload.put("error", error == null ? "" : error);
    byte[] body = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = openConnection("/api/sync/ack", "POST");
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setDoOutput(true);
    connection.getOutputStream().write(body);
    int responseCode = connection.getResponseCode();
    if (responseCode < 200 || responseCode >= 300) {
      throw new IllegalStateException("VPS确认同步失败：" + responseCode);
    }
  }

  private HttpURLConnection openConnection(String path, String method) throws Exception {
    String normalizedBase = vpsBaseUrl.endsWith("/") ? vpsBaseUrl.substring(0, vpsBaseUrl.length() - 1) : vpsBaseUrl;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    HttpURLConnection connection = (HttpURLConnection) new URL(normalizedBase + normalizedPath).openConnection(java.net.Proxy.NO_PROXY);
    connection.setRequestMethod(method);
    connection.setRequestProperty("X-Roomops-Pull-Token", pullToken);
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(60000);
    return connection;
  }

  private void uploadToMinio(String objectName, byte[] bytes, String contentType) throws Exception {
    MinioClient client = MinioClient.builder()
        .endpoint(minioUrl)
        .credentials(minioName, minioPass)
        .httpClient(new okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build())
        .build();
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(minioBucketName).build())) {
      client.makeBucket(MakeBucketArgs.builder().bucket(minioBucketName).build());
    }
    try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
      client.putObject(PutObjectArgs.builder()
          .bucket(minioBucketName)
          .object(objectName)
          .stream(inputStream, (long) bytes.length, -1L)
          .contentType(contentType)
          .build());
    }
  }

  private String buildObjectName(String recordId, String storedFilename) {
    return "records/" + safePath(recordId) + "/" + safePath(storedFilename);
  }

  private String buildStoredFilename(String recordId, Integer photoIndex, String originalFilename) {
    String ext = "jpg";
    if (originalFilename != null && originalFilename.contains(".")) {
      ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
    }
    return safePath(recordId) + "-" + String.format("%02d", photoIndex == null ? 1 : photoIndex) + "." + ext;
  }

  private String safePath(String value) {
    if (value == null || value.isEmpty()) {
      return UUID.randomUUID().toString().replace("-", "");
    }
    return value.replaceAll("[^A-Za-z0-9._-]", "_");
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

  private BigDecimal decimal(String value) {
    try {
      return value == null || value.isEmpty() ? null : new BigDecimal(value);
    } catch (Exception e) {
      return null;
    }
  }

  private Integer integer(String value, Integer defaultValue) {
    try {
      return value == null || value.isEmpty() ? defaultValue : Integer.valueOf(value);
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private Long longValue(String value, Long defaultValue) {
    try {
      return value == null || value.isEmpty() ? defaultValue : Long.valueOf(value);
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private Date date(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Date.from(LocalDateTime.parse(value.replace("T", " ").substring(0, 19), DATE_TIME_FORMATTER).atZone(SHANGHAI_ZONE).toInstant());
    } catch (Exception e) {
      return null;
    }
  }

  private Date defaultDate(Date value, Date defaultValue) {
    return value == null ? defaultValue : value;
  }

  private BigDecimal defaultDecimal(BigDecimal value, BigDecimal defaultValue) {
    return value == null ? defaultValue : value;
  }

  private Date now() {
    return new Date();
  }

  @Data
  public static class PullResult {
    private String syncBatchId;
    private Integer pulled;
    private Integer succeeded;
    private Integer failed;
  }
}
