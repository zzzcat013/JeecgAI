package org.jeecg.modules.biz.roomops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsPhoto;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsPhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/roomops/photo")
public class BizRoomopsPhotoController {
  @Autowired
  private IBizRoomopsPhotoService bizRoomopsPhotoService;

  @Value("${jeecg.minio.minio_url:}")
  private String minioUrl;

  @Value("${jeecg.minio.minio_name:}")
  private String minioName;

  @Value("${jeecg.minio.minio_pass:}")
  private String minioPass;

  @Value("${jeecg.roomops.minio.bucketName:room-check-docs}")
  private String minioBucketName;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsPhoto entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsPhoto> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("uploaded_at", "create_time");
    IPage<BizRoomopsPhoto> pageList = bizRoomopsPhotoService.page(new Page<>(pageNo, pageSize), queryWrapper);
    return Result.ok(pageList);
  }

  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody BizRoomopsPhoto entity) {
    bizRoomopsPhotoService.save(entity);
    return Result.ok("添加成功！");
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:photo:edit")
  public Result<?> edit(@RequestBody BizRoomopsPhoto entity) {
    bizRoomopsPhotoService.updateById(entity);
    return Result.ok("修改成功!");
  }

  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsPhotoService.removeById(id);
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsPhotoService.removeByIds(Arrays.asList(ids.split(",")));
    return Result.ok("批量删除成功！");
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    return Result.ok(bizRoomopsPhotoService.getById(id));
  }

  @GetMapping(value = "/preview/{id}")
  public ResponseEntity<InputStreamResource> preview(@PathVariable("id") String id) throws Exception {
    BizRoomopsPhoto photo = bizRoomopsPhotoService.getById(id);
    if (photo == null || photo.getStoragePath() == null || photo.getStoragePath().isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    String objectName = normalizeObjectName(photo.getStoragePath());
    MinioClient client = buildMinioClient();
    InputStreamResource resource = new InputStreamResource(client.getObject(
        GetObjectArgs.builder().bucket(minioBucketName).object(objectName).build()));
    String contentType = photo.getContentType() == null || photo.getContentType().isEmpty() ? "image/jpeg" : photo.getContentType();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .body(resource);
  }

  private MinioClient buildMinioClient() {
    okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .build();
    return MinioClient.builder()
        .endpoint(minioUrl)
        .credentials(minioName, minioPass)
        .httpClient(httpClient)
        .build();
  }

  private String normalizeObjectName(String value) {
    String objectName = value.trim();
    String bucketPrefix = "/" + minioBucketName + "/";
    int bucketIndex = objectName.indexOf(bucketPrefix);
    if (bucketIndex >= 0) {
      objectName = objectName.substring(bucketIndex + bucketPrefix.length());
    }
    if (objectName.startsWith(minioBucketName + "/")) {
      objectName = objectName.substring(minioBucketName.length() + 1);
    }
    while (objectName.startsWith("/")) {
      objectName = objectName.substring(1);
    }
    return objectName;
  }
}
