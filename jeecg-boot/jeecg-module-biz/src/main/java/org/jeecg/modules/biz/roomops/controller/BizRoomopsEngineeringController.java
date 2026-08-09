package org.jeecg.modules.biz.roomops.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsEngineeringAttachment;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsEngineeringProject;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTask;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsEngineeringAttachmentService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsEngineeringProjectService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/roomops/engineering")
public class BizRoomopsEngineeringController {

  @Autowired
  private IBizRoomopsEngineeringProjectService projectService;

  @Autowired
  private IBizRoomopsEngineeringAttachmentService attachmentService;

  @Autowired
  private IBizRoomopsTaskService taskService;

  @Value("${jeecg.minio.minio_url:}")
  private String minioUrl;

  @Value("${jeecg.minio.minio_name:}")
  private String minioName;

  @Value("${jeecg.minio.minio_pass:}")
  private String minioPass;

  @Value("${jeecg.roomops.minio.bucketName:room-check-docs}")
  private String minioBucketName;

  @GetMapping("/project/list")
  public Result<?> queryProjectPage(BizRoomopsEngineeringProject entity,
                                    @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                    @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                    HttpServletRequest req) {
    QueryWrapper<BizRoomopsEngineeringProject> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("archived", "create_time");
    IPage<BizRoomopsEngineeringProject> pageList = projectService.page(new Page<>(pageNo, pageSize), queryWrapper);
    pageList.getRecords().forEach(this::fillProjectCounts);
    return Result.ok(pageList);
  }

  @GetMapping("/project/queryById")
  public Result<?> queryById(@RequestParam(name = "projectId", required = false) String projectId,
                             @RequestParam(name = "id", required = false) String id) {
    BizRoomopsEngineeringProject project;
    if (id != null && !id.isEmpty()) {
      project = projectService.getById(id);
    } else if (projectId != null && !projectId.isEmpty()) {
      project = projectService.getOne(new QueryWrapper<BizRoomopsEngineeringProject>()
          .eq("project_id", projectId.trim()).last("limit 1"), false);
    } else {
      return Result.error("缺少 projectId 或 id");
    }
    if (project == null) {
      return Result.error("工程不存在");
    }
    project.setAttachments(listAttachments(project.getProjectId()));
    fillProjectCounts(project);
    return Result.ok(project);
  }

  @PostMapping("/project/add")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> add(@RequestBody BizRoomopsEngineeringProject entity) {
    if (blank(entity.getProjectName())) {
      return Result.error("请填写工程名称");
    }
    String operatorName = currentUserName();
    Date now = new Date();
    if (blank(entity.getProjectId())) {
      entity.setProjectId(generateProjectId(entity));
    } else {
      entity.setProjectId(entity.getProjectId().trim());
    }
    entity.setStatus(defaultText(entity.getStatus(), "NOT_STARTED"));
    entity.setArchived(defaultInt(entity.getArchived(), 0));
    entity.setCreateBy(operatorName);
    entity.setUpdateBy(operatorName);
    entity.setCreateTime(now);
    entity.setUpdateTime(now);
    projectService.save(entity);
    return Result.ok(entity);
  }

  @PutMapping("/project/edit")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> edit(@RequestBody BizRoomopsEngineeringProject entity) {
    BizRoomopsEngineeringProject existing = projectService.getById(entity.getId());
    if (existing == null) {
      return Result.error("工程不存在");
    }
    entity.setProjectId(existing.getProjectId());
    entity.setCreateBy(existing.getCreateBy());
    entity.setCreateTime(existing.getCreateTime());
    entity.setArchived(defaultInt(existing.getArchived(), 0));
    entity.setArchivedAt(existing.getArchivedAt());
    entity.setArchivedBy(existing.getArchivedBy());
    entity.setUpdateBy(currentUserName());
    entity.setUpdateTime(new Date());
    projectService.updateById(entity);
    return Result.ok(entity);
  }

  @PostMapping("/project/status")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> updateStatus(@RequestBody JSONObject body) {
    String projectId = body.getString("projectId");
    String status = body.getString("status");
    if (blank(projectId) || blank(status)) {
      return Result.error("缺少 projectId 或 status");
    }
    BizRoomopsEngineeringProject project = getByProjectId(projectId.trim());
    if (project == null) {
      return Result.error("工程不存在");
    }
    project.setStatus(status.trim());
    project.setUpdateBy(currentUserName());
    project.setUpdateTime(new Date());
    projectService.updateById(project);
    return Result.ok(project);
  }

  @PostMapping("/project/archive")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> archive(@RequestBody JSONObject body) {
    String projectId = body.getString("projectId");
    if (blank(projectId)) {
      return Result.error("缺少 projectId");
    }
    boolean archived = Boolean.TRUE.equals(body.getBoolean("archived"));
    BizRoomopsEngineeringProject project = getByProjectId(projectId.trim());
    if (project == null) {
      return Result.error("工程不存在");
    }
    project.setArchived(archived ? 1 : 0);
    project.setArchivedAt(archived ? new Date() : null);
    project.setArchivedBy(archived ? currentUserName() : null);
    project.setUpdateBy(currentUserName());
    project.setUpdateTime(new Date());
    projectService.updateById(project);
    return Result.ok(archived ? "工程已归档" : "工程已恢复");
  }

  @DeleteMapping("/project/delete")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> deleteProject(@RequestParam(name = "id", required = true) String id) {
    projectService.removeById(id);
    return Result.ok("删除成功!");
  }

  @GetMapping("/project/tasks")
  public Result<?> listProjectTasks(@RequestParam(name = "projectId", required = true) String projectId) {
    return Result.ok(taskService.list(new QueryWrapper<BizRoomopsTask>()
        .eq("project_id", projectId.trim())
        .orderByAsc("create_time")));
  }

  @PostMapping("/attachment/upload")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> uploadAttachment(@RequestParam("file") MultipartFile file,
                                    @RequestParam("projectId") String projectId,
                                    @RequestParam(name = "docType", defaultValue = "OTHER") String docType) {
    if (getByProjectId(projectId.trim()) == null) {
      return Result.error("工程不存在");
    }
    if (file == null || file.isEmpty()) {
      return Result.error("上传文件为空");
    }
    String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
    int slash = original.lastIndexOf('/');
    if (slash >= 0) {
      original = original.substring(slash + 1);
    }
    String stored = UUID.randomUUID().toString().replace("-", "") + "_" + safeName(original);
    String objectName = "engineering/" + safePath(projectId.trim()) + "/" + stored;
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (Exception e) {
      log.error("工程附件读取失败", e);
      return Result.error("附件上传失败：" + e.getMessage());
    }
    String fileMd5 = DigestUtils.md5DigestAsHex(bytes);
    BizRoomopsEngineeringAttachment existed = attachmentService.getOne(new QueryWrapper<BizRoomopsEngineeringAttachment>()
        .eq("project_id", projectId.trim())
        .eq("doc_type", defaultText(docType, "OTHER"))
        .eq("file_md5", fileMd5)
        .last("limit 1"), false);
    if (existed != null) {
      existed.setDuplicate(true);
      Result<BizRoomopsEngineeringAttachment> result = Result.ok(existed);
      result.setMessage("文件已存在，未重复上传");
      return result;
    }
    try {
      uploadToMinio(objectName, bytes, defaultText(file.getContentType(), "application/octet-stream"));
    } catch (Exception e) {
      log.error("工程附件上传 MinIO 失败", e);
      return Result.error("附件上传失败：" + e.getMessage());
    }
    BizRoomopsEngineeringAttachment attachment = new BizRoomopsEngineeringAttachment();
    attachment.setProjectId(projectId.trim());
    attachment.setDocType(defaultText(docType, "OTHER"));
    attachment.setOriginalFilename(original);
    attachment.setStoredFilename(stored);
    attachment.setStoragePath(objectName);
    attachment.setContentType(defaultText(file.getContentType(), "application/octet-stream"));
    attachment.setFileMd5(fileMd5);
    attachment.setFileSize(file.getSize());
    attachment.setUploaderUserid(currentUserId());
    attachment.setUploaderName(currentUserName());
    attachment.setUploadedAt(new Date());
    attachment.setCreateTime(new Date());
    attachment.setUpdateTime(new Date());
    attachmentService.save(attachment);
    return Result.ok(attachment);
  }

  @GetMapping("/attachment/list")
  public Result<?> listAttachment(@RequestParam(name = "projectId", required = true) String projectId) {
    return Result.ok(listAttachments(projectId.trim()));
  }

  @GetMapping("/attachment/preview/{id}")
  public ResponseEntity<InputStreamResource> previewAttachment(@PathVariable("id") String id) throws Exception {
    return buildAttachmentResponse(id, false);
  }

  @GetMapping("/attachment/download/{id}")
  public ResponseEntity<InputStreamResource> downloadAttachment(@PathVariable("id") String id) throws Exception {
    return buildAttachmentResponse(id, true);
  }

  @DeleteMapping("/attachment/delete")
  @RequiresPermissions("roomops:engineering:edit")
  public Result<?> deleteAttachment(@RequestParam(name = "id", required = true) String id) {
    BizRoomopsEngineeringAttachment attachment = attachmentService.getById(id);
    if (attachment != null && !blank(attachment.getStoragePath())) {
      try {
        MinioClient client = buildMinioClient();
        client.removeObject(RemoveObjectArgs.builder()
            .bucket(minioBucketName)
            .object(normalizeObjectName(attachment.getStoragePath()))
            .build());
      } catch (Exception e) {
        log.warn("删除工程附件 MinIO 对象失败，仅删除数据库记录，id={}", id, e);
      }
    }
    attachmentService.removeById(id);
    return Result.ok("删除成功!");
  }

  private ResponseEntity<InputStreamResource> buildAttachmentResponse(String id, boolean download) throws Exception {
    BizRoomopsEngineeringAttachment attachment = attachmentService.getById(id);
    if (attachment == null || blank(attachment.getStoragePath())) {
      return ResponseEntity.notFound().build();
    }
    if (!download && isOfficeFile(attachment)) {
      ResponseEntity<InputStreamResource> pdfResponse = buildOfficePdfResponse(attachment);
      if (pdfResponse != null) {
        return pdfResponse;
      }
    }
    MinioClient client = buildMinioClient();
    InputStreamResource resource = new InputStreamResource(client.getObject(
        GetObjectArgs.builder().bucket(minioBucketName).object(normalizeObjectName(attachment.getStoragePath())).build()));
    String contentType = defaultText(attachment.getContentType(), "application/octet-stream");
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType));
    if (download) {
      builder.header(HttpHeaders.CONTENT_DISPOSITION,
          "attachment; filename*=UTF-8''" + URLEncoder.encode(attachment.getOriginalFilename(), StandardCharsets.UTF_8));
    } else {
      builder.header(HttpHeaders.CONTENT_DISPOSITION, "inline");
    }
    return builder.body(resource);
  }

  private ResponseEntity<InputStreamResource> buildOfficePdfResponse(BizRoomopsEngineeringAttachment attachment) {
    java.io.File workDir = null;
    try {
      workDir = Files.createTempDirectory("roomops-eng-preview-").toFile();
      String stored = blank(attachment.getStoredFilename()) ? "source." + fileExt(attachment) : attachment.getStoredFilename();
      java.io.File src = new java.io.File(workDir, safeName(stored));
      MinioClient client = buildMinioClient();
      try (InputStream in = client.getObject(GetObjectArgs.builder()
          .bucket(minioBucketName)
          .object(normalizeObjectName(attachment.getStoragePath()))
          .build())) {
        Files.copy(in, src.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      java.io.File pdfFile = new java.io.File(workDir, src.getName().replaceAll("\\.[^./\\\\]+$", "") + ".pdf");
      boolean converted = trySofficeCliConvert(src, pdfFile);
      log.info("工程附件 Office 转 PDF 预览：converted={}, source={}, pdf={}", converted, src.getAbsolutePath(), pdfFile.getAbsolutePath());
      if (!converted || !pdfFile.exists()) {
        return null;
      }
      byte[] data = Files.readAllBytes(pdfFile.toPath());
      String baseName = attachment.getOriginalFilename().replaceAll("\\.[^./\\\\]+$", "");
      String pdfName = baseName + ".pdf";
      ByteArrayResource resource = new ByteArrayResource(data);
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .contentLength(data.length)
          .header(HttpHeaders.CONTENT_DISPOSITION,
              "inline; filename*=UTF-8''" + URLEncoder.encode(pdfName, StandardCharsets.UTF_8))
          .body(new InputStreamResource(resource.getInputStream()));
    } catch (Exception e) {
      log.error("工程附件 Office 预览转换失败", e);
      return null;
    } finally {
      if (workDir != null && workDir.exists()) {
        try {
          org.apache.commons.io.FileUtils.deleteDirectory(workDir);
        } catch (Exception e) {
          log.warn("清理工程附件预览临时目录失败: {}", workDir.getAbsolutePath(), e);
        }
      }
    }
  }

  private boolean trySofficeCliConvert(java.io.File src, java.io.File pdfFile) {
    try {
      List<String> command = new ArrayList<>();
      command.add("soffice");
      command.add("--headless");
      command.add("--convert-to");
      command.add("pdf");
      command.add("--outdir");
      command.add(src.getParentFile().getAbsolutePath());
      command.add(src.getAbsolutePath());
      Process process = new ProcessBuilder(command).start();
      boolean finished = process.waitFor(90, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0 && pdfFile.exists();
    } catch (Exception e) {
      log.warn("soffice 转换 PDF 失败", e);
      return false;
    }
  }

  private boolean isOfficeFile(BizRoomopsEngineeringAttachment attachment) {
    String ext = fileExt(attachment);
    return Arrays.asList("doc", "docx", "ppt", "pptx", "xls", "xlsx").contains(ext);
  }

  private String fileExt(BizRoomopsEngineeringAttachment attachment) {
    String name = blank(attachment.getOriginalFilename()) ? attachment.getStoredFilename() : attachment.getOriginalFilename();
    if (blank(name)) {
      return "";
    }
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
  }

  private java.util.List<BizRoomopsEngineeringAttachment> listAttachments(String projectId) {
    return attachmentService.list(new QueryWrapper<BizRoomopsEngineeringAttachment>()
        .eq("project_id", projectId)
        .orderByAsc("doc_type")
        .orderByAsc("uploaded_at"));
  }

  private void fillProjectCounts(BizRoomopsEngineeringProject project) {
    project.setAttachmentCount((int) attachmentService.count(new QueryWrapper<BizRoomopsEngineeringAttachment>()
        .eq("project_id", project.getProjectId())));
    project.setTaskCount((int) taskService.count(new QueryWrapper<BizRoomopsTask>()
        .eq("project_id", project.getProjectId())));
  }

  private BizRoomopsEngineeringProject getByProjectId(String projectId) {
    return projectService.getOne(new QueryWrapper<BizRoomopsEngineeringProject>()
        .eq("project_id", projectId).last("limit 1"), false);
  }

  private String generateProjectId(BizRoomopsEngineeringProject project) {
    String domain = defaultText(project.getDomainShortCode(), "CORE");
    String region = defaultText(project.getRegionCode(), "TY");
    String room = blank(project.getRoomId()) ? "ROOM" : project.getRoomId().trim().replaceAll("[^A-Za-z0-9_-]", "_");
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
    return "EG-" + domain + "-" + region + "-" + room + "-" + random;
  }

  private void uploadToMinio(String objectName, byte[] bytes, String contentType) throws Exception {
    MinioClient client = buildMinioClient();
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(minioBucketName).build())) {
      client.makeBucket(MakeBucketArgs.builder().bucket(minioBucketName).build());
    }
    client.putObject(PutObjectArgs.builder()
        .bucket(minioBucketName)
        .object(objectName)
        .stream(new java.io.ByteArrayInputStream(bytes), (long) bytes.length, -1L)
        .contentType(contentType)
        .build());
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

  private String safePath(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private String safeName(String value) {
    return value.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
  }

  private String currentUserId() {
    Object principal = getPrincipal();
    return principal instanceof LoginUser ? ((LoginUser) principal).getUsername() : "";
  }

  private String currentUserName() {
    Object principal = getPrincipal();
    if (!(principal instanceof LoginUser loginUser)) {
      return "";
    }
    return loginUser.getRealname() == null || loginUser.getRealname().isEmpty()
        ? loginUser.getUsername()
        : loginUser.getRealname();
  }

  private Object getPrincipal() {
    try {
      return SecurityUtils.getSubject().getPrincipal();
    } catch (Exception e) {
      return null;
    }
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String defaultText(String value, String defaultValue) {
    return blank(value) ? defaultValue : value.trim();
  }

  private int defaultInt(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
  }
}
