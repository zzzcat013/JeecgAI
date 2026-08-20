package org.jeecg.modules.biz.ai5g.controller;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import org.jeecg.modules.biz.ai5g.util.KnowledgePortalTokenUtil;
import org.jeecg.modules.biz.ai5g.util.MineruClientUtil;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.common.util.oConvertUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.modules.biz.ai5g.entity.BizDocFile;
import org.jeecg.modules.biz.ai5g.service.IBizDocFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/ai5g/doc")
public class Ai5gDocumentController {

  @Value(value = "${jeecg.uploadType}")
  private String uploadType;

  @Value(value = "${jeecg.path.upload}")
  private String uploadpath;

  @Value(value = "${jeecg.ai5g.baseDir:doc}")
  private String baseDir;

  @Value(value = "${jeecg.minio.minio_url:}")
  private String minioUrl;

  @Value(value = "${jeecg.minio.minio_name:}")
  private String minioName;

  @Value(value = "${jeecg.minio.minio_pass:}")
  private String minioPass;

  @Value(value = "${jeecg.minio.bucketName:}")
  private String minioBucketName;

  @Value(value = "${jeecg.ai5g.mineru-fast-mode:true}")
  private boolean mineruFastMode;

  @Autowired
  private IBizDocFileService bizDocFileService;
  @Autowired
  private org.jeecg.modules.biz.ai5g.service.IBizDocTypeService bizDocTypeService;
  @Autowired
  private org.springframework.core.env.Environment environment;
  @Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private static final Set<String> ALLOW_EXT = new HashSet<>(Arrays.asList("pdf","doc","docx","xlsx","xls","csv"));
  private static final Set<String> PACKAGE_ASSET_EXT = new HashSet<>(Arrays.asList("png","jpg","jpeg","gif","bmp","webp","svg"));
  private static final long ZIP_MAX_FILE_SIZE = 150L * 1024 * 1024;
  private static final long ZIP_MAX_TOTAL_SIZE = 1024L * 1024 * 1024;
  private static final int ZIP_MAX_ENTRY_COUNT = 10000;
  private static final long MINIO_PART_SIZE = 10L * 1024 * 1024;
  private static final java.util.regex.Pattern MD_IMAGE_PATTERN = java.util.regex.Pattern.compile("!\\[(.*?)]\\((.*?)\\)");
  private static final String DOMAIN_URL_PLACEHOLDER = "#{domainURL}";

  private final Set<String> convertingDocIds = ConcurrentHashMap.newKeySet();
  private final ExecutorService mdConvertExecutor = Executors.newSingleThreadExecutor();

  @PreDestroy
  public void shutdownMdConvertExecutor() {
    mdConvertExecutor.shutdownNow();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void resumeInterruptedMineruTasks() {
    try {
      List<BizDocFile> docs = bizDocFileService.list(new LambdaQueryWrapper<BizDocFile>()
          .eq(BizDocFile::getProcessStatus, "processing")
          .isNotNull(BizDocFile::getMineruTaskId));
      for (BizDocFile doc : docs) {
        if (!convertingDocIds.add(doc.getId())) {
          continue;
        }
        mdConvertExecutor.submit(() -> {
          try {
            resumeMineruTask(doc);
          } catch (Exception e) {
            log.error("恢复 MinerU 异步任务异常, docId={}", doc.getId(), e);
            markMineruTaskFailed(doc, "恢复 MinerU 异步任务异常: " + e.getMessage());
          } finally {
            convertingDocIds.remove(doc.getId());
          }
        });
      }
      if (!docs.isEmpty()) {
        log.info("AI5G 启动恢复 MinerU 异步任务数量: {}", docs.size());
      }
    } catch (Exception e) {
      log.warn("AI5G 启动恢复 MinerU 异步任务失败", e);
    }
    retryPendingTombstones();
  }

  private void resumeMineruTask(BizDocFile doc) {
    java.io.File workDir = null;
    try {
      String mineruUrl = environment.getProperty("jeecg.airag.know.mineru-url");
      if (oConvertUtils.isEmpty(mineruUrl) || oConvertUtils.isEmpty(doc.getMineruTaskId())) {
        markMineruTaskFailed(doc, "MinerU 异步任务参数不完整");
        return;
      }
      workDir = java.nio.file.Files.createTempDirectory("ai5g-mineru-resume-").toFile();
      com.alibaba.fastjson.JSONObject mineruRes = MineruClientUtil.waitForParseTask(
          mineruUrl, doc.getMineruTaskId(), workDir.getAbsolutePath(), status -> updateMineruTaskStatus(doc, status));
      if (mineruRes == null || oConvertUtils.isEmpty(mineruRes.getString("content"))) {
        markMineruTaskFailed(doc, "MinerU 异步任务未返回有效结果");
        return;
      }

      String objectName = getMinioObjectName(doc.getStoragePath());
      String mdRel = objectName == null
          ? (doc.getStoragePath() == null ? "result.md" : doc.getStoragePath().replaceAll("\\.[^./\\\\]+$", "") + ".md")
          : objectName.replaceAll("\\.[^./\\\\]+$", "") + ".md";
      java.io.File mdFile = new java.io.File(workDir, "mineru.md");
      java.io.File convertedAssetRootDir = null;
      java.io.File convertedAssetMarkdown = null;
      String markdownPath = mineruRes.getString("markdownPath");
      String extractDir = mineruRes.getString("extractDir");
      if (oConvertUtils.isNotEmpty(markdownPath) && new java.io.File(markdownPath).exists()) {
        java.io.File mineruMarkdown = new java.io.File(markdownPath);
        org.apache.commons.io.FileUtils.copyFile(mineruMarkdown, mdFile);
        if (oConvertUtils.isNotEmpty(extractDir) && new java.io.File(extractDir).exists()) {
          convertedAssetRootDir = new java.io.File(extractDir);
          convertedAssetMarkdown = mineruMarkdown;
        }
      } else {
        org.apache.commons.io.FileUtils.writeStringToFile(mdFile, mineruRes.getString("content"), java.nio.charset.StandardCharsets.UTF_8);
      }

      boolean saved = saveConvertedMarkdown(doc, mdFile, mdRel, convertedAssetRootDir, convertedAssetMarkdown);
      if (saved) {
        doc.setMineruTaskStatus("completed");
        doc.setMineruCompletedAt(new Date());
        boolean clearRemark = isConversionFailureRemark(doc.getRemark());
        if (clearRemark) {
          doc.setRemark(null);
        }
        doc.setProcessStatus("success");
        bizDocFileService.updateById(doc);
        if (clearRemark) {
          forceClearRemark(doc.getId());
        }
      } else {
        markMineruTaskFailed(doc, "MinerU 异步结果保存失败");
      }
    } catch (Exception e) {
      log.error("MinerU 异步任务恢复处理失败, docId={}", doc.getId(), e);
      markMineruTaskFailed(doc, "MinerU 异步任务恢复处理失败: " + e.getMessage());
    } finally {
      if (workDir != null && workDir.exists()) {
        try {
          org.apache.commons.io.FileUtils.deleteDirectory(workDir);
        } catch (Exception e) {
          log.warn("清理 MinerU 恢复临时目录失败: {}", workDir.getAbsolutePath(), e);
        }
      }
    }
  }

  private void updateMineruTaskStatus(BizDocFile doc, com.alibaba.fastjson.JSONObject status) {
    if (status == null || doc == null) {
      return;
    }
    String taskStatus = status.getString("status");
    boolean changed = false;
    if (oConvertUtils.isNotEmpty(taskStatus) && !java.util.Objects.equals(doc.getMineruTaskStatus(), taskStatus)) {
      doc.setMineruTaskStatus(taskStatus);
      changed = true;
    }
    Integer queuedAhead = status.getInteger("queued_ahead");
    if (queuedAhead != null && !java.util.Objects.equals(doc.getMineruQueuedAhead(), queuedAhead)) {
      doc.setMineruQueuedAhead(queuedAhead);
      changed = true;
    }
    String error = status.getString("error");
    if (!java.util.Objects.equals(doc.getMineruError(), error)) {
      doc.setMineruError(truncateText(error, 500));
      changed = true;
    }
    Date startedAt = parseMineruDate(status.getString("started_at"));
    if (startedAt != null && !java.util.Objects.equals(doc.getMineruStartedAt(), startedAt)) {
      doc.setMineruStartedAt(startedAt);
      changed = true;
    }
    Date completedAt = parseMineruDate(status.getString("completed_at"));
    if (completedAt != null && !java.util.Objects.equals(doc.getMineruCompletedAt(), completedAt)) {
      doc.setMineruCompletedAt(completedAt);
      changed = true;
    }
    if (changed) {
      bizDocFileService.updateById(doc);
    }
  }

  private Date parseMineruDate(String iso) {
    if (oConvertUtils.isEmpty(iso)) {
      return null;
    }
    try {
      return Date.from(java.time.OffsetDateTime.parse(iso).toInstant());
    } catch (Exception ignored) {
    }
    try {
      return Date.from(java.time.Instant.parse(iso));
    } catch (Exception ignored) {
    }
    return null;
  }

  private void markMineruTaskFailed(BizDocFile doc, String remark) {
    doc.setProcessStatus("failed");
    doc.setMineruTaskStatus("failed");
    doc.setRemark(truncateText(remark, 500));
    bizDocFileService.updateById(doc);
  }

  private boolean saveConvertedMarkdown(BizDocFile doc, java.io.File mdFile, String mdRel,
                                        java.io.File convertedAssetRootDir, java.io.File convertedAssetMarkdown) {
    try {
      if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        if (convertedAssetRootDir != null && convertedAssetMarkdown != null && convertedAssetMarkdown.exists()) {
          com.alibaba.fastjson.JSONObject packageResult = uploadConvertedMarkdownPackage(convertedAssetRootDir.toPath(), convertedAssetMarkdown, doc);
          String mdObjectName = packageResult.getString("mainObjectName");
          doc.setMdPath(buildMinioUrl(mdObjectName));
          doc.setAssetRoot(packageResult.getString("assetRoot"));
          doc.setAssetManifest(packageResult.getString("assetManifest"));
          if (oConvertUtils.isEmpty(doc.getSourcePackagePath())) {
            doc.setSourcePackagePath(doc.getStoragePath());
          }
          rewriteAndUploadMarkdown(mdFile, doc, mdObjectName, packageResult.getString("mainRelativeDir"));
        } else {
          try (java.io.InputStream in = new java.io.FileInputStream(mdFile)) {
            doc.setMdPath(uploadMinioObject(in, mdFile.length(), "text/markdown;charset=UTF-8", mdRel));
          }
        }
      } else {
        doc.setMdPath(mdRel);
      }
      doc.setMdConverted(true);
      return true;
    } catch (Exception e) {
      log.error("保存转换结果失败, docId={}", doc.getId(), e);
      return false;
    }
  }

  @GetMapping("/debug/knowledge-portal-token")
  @IgnoreAuth
  public Result<?> debugKnowledgePortalToken() {
    return Result.OK(KnowledgePortalTokenUtil.debugAuthToken());
  }

  @GetMapping("/debug/knowledge-portal-token-curl")
  @IgnoreAuth
  public Result<?> buildKnowledgePortalTokenCurl() {
    return Result.OK(KnowledgePortalTokenUtil.buildCurlCommand());
  }

  @PostMapping("/upload")
  public Result<?> upload(@RequestParam("file") MultipartFile file,
                          @RequestParam("directoryName") String directoryName,
                          @RequestParam("typeCode1") String typeCode1,
                          @RequestParam("typeCode2") String typeCode2,
                          @RequestParam("typeCode3") String typeCode3,
                          @RequestParam(value = "title", required = false) String title,
                          @RequestParam(value = "fileYear", required = false) Integer fileYear,
                          @RequestParam(value = "remark", required = false) String remark) {
    try {
      String orgName = file.getOriginalFilename();
      orgName = CommonUtils.getFileName(orgName);
      String ext = orgName.substring(orgName.lastIndexOf('.') + 1).toLowerCase();

      // 平台安全校验
      String bizPathCheck = directoryName + "/" + typeCode3;
      SsrfFileTypeFilter.checkUploadFileType(file, bizPathCheck);

      // 业务限定的允许类型
      if (!ALLOW_EXT.contains(ext)) {
        return Result.error("不支持的文件类型: " + ext);
      }

      // 验证分类链路
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.jeecg.modules.biz.ai5g.entity.BizDocType> w1 = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      w1.eq("level", 1).eq("code", typeCode1);
      org.jeecg.modules.biz.ai5g.entity.BizDocType t1 = bizDocTypeService.getOne(w1, false);
      if (t1 == null) return Result.error("一级类型不存在: " + typeCode1);

      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.jeecg.modules.biz.ai5g.entity.BizDocType> w2 = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      w2.eq("level", 2).eq("code", typeCode2).eq("parent_code", typeCode1);
      org.jeecg.modules.biz.ai5g.entity.BizDocType t2 = bizDocTypeService.getOne(w2, false);
      if (t2 == null) return Result.error("二级类型不存在或父级不匹配: " + typeCode2);

      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.jeecg.modules.biz.ai5g.entity.BizDocType> w3 = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      w3.eq("level", 3).eq("code", typeCode3).eq("parent_code", typeCode2);
      org.jeecg.modules.biz.ai5g.entity.BizDocType t3 = bizDocTypeService.getOne(w3, false);
      if (t3 == null) return Result.error("三级类型不存在或父级不匹配: " + typeCode3);

      String seg = validateCategory(typeCode1, typeCode2, typeCode3);
      String bizPath = buildStorageBizPath();
      String savePath;
      if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
        savePath = CommonUtils.uploadLocal(file, bizPath, uploadpath);
      } else if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        savePath = uploadMinioFile(file, bizPath);
      } else {
        savePath = CommonUtils.upload(file, bizPath, uploadType);
      }

      if (oConvertUtils.isEmpty(savePath)) {
        return Result.error("上传失败");
      }

      // 版本号与latest处理
      int version = nextVersion(orgName, seg);
      markOldVersionsNotLatest(orgName, seg);

      BizDocFile doc = new BizDocFile();
      doc.setDisplayName((title != null && title.trim().length() > 0) ? title : orgName);
      doc.setOriginalName(orgName);
      doc.setActualFileName(savePath.substring(savePath.lastIndexOf('/')+1));
      doc.setVersion(version);
      doc.setUploadTime(new Date());
      doc.setFileType(ext);
      doc.setCategoryPath(seg);
      doc.setFileYear(fileYear != null ? fileYear : Integer.valueOf(new java.text.SimpleDateFormat("yyyy").format(new Date())));
      doc.setRemark(remark);
      doc.setLatest(true);
      doc.setProcessStatus("uploaded");
      doc.setContentType(file.getContentType());
      doc.setSize(file.getSize());
      doc.setStoragePath(savePath);
      doc.setStorageFilename(CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType) ? getMinioObjectName(savePath) : doc.getActualFileName());
      doc.setMdConverted(false);
      doc.setCreateTime(new Date());
      boolean ok = bizDocFileService.save(doc);
      if (!ok) return Result.error("保存失败");
      return Result.OK(doc);
    } catch (Exception e) {
      log.error("ai5g upload error", e);
      return Result.error(e.getMessage());
    }
  }

  @PostMapping("/import/zip")
  public Result<?> importMarkdownPackage(@RequestParam("file") MultipartFile file,
                                         @RequestParam("directoryName") String directoryName,
                                         @RequestParam("typeCode1") String typeCode1,
                                         @RequestParam("typeCode2") String typeCode2,
                                         @RequestParam("typeCode3") String typeCode3,
                                         @RequestParam(value = "mainFile", required = false) String mainFile,
                                         @RequestParam(value = "title", required = false) String title,
                                         @RequestParam(value = "fileYear", required = false) Integer fileYear,
                                         @RequestParam(value = "remark", required = false) String remark) {
    if (!CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
      return Result.error("Markdown资源包导入当前仅支持MinIO上传模式");
    }
    java.io.File workDir = null;
    try {
      String orgName = CommonUtils.getFileName(file.getOriginalFilename());
      if (oConvertUtils.isEmpty(orgName)) {
        return Result.error("上传文件名不能为空");
      }
      if (!orgName.contains(".")) {
        return Result.error("请上传zip资源包");
      }
      String ext = orgName.substring(orgName.lastIndexOf('.') + 1).toLowerCase();
      if (!"zip".equals(ext)) {
        return Result.error("请上传zip资源包");
      }

      String seg = validateCategory(typeCode1, typeCode2, typeCode3);
      String bizPathCheck = directoryName + "/" + typeCode3;
      SsrfFileTypeFilter.checkUploadFileType(file, bizPathCheck);

      String packageId = java.util.UUID.randomUUID().toString().replace("-", "");
      String assetRoot = normalizeObjectName(buildPackageAssetRoot(packageId));
      workDir = java.nio.file.Files.createTempDirectory("ai5g-md-package-").toFile();
      java.io.File zipLocal = new java.io.File(workDir, orgName);
      file.transferTo(zipLocal);

      java.io.File extractedDir = new java.io.File(workDir, "files");
      unzipPackage(zipLocal.toPath(), extractedDir.toPath());
      java.io.File mainMd = findMainMarkdown(extractedDir.toPath(), mainFile);
      if (mainMd == null) {
        return Result.error("压缩包中未找到主Markdown文档");
      }

      String sourcePackagePath;
      try (java.io.InputStream in = new java.io.FileInputStream(zipLocal)) {
        sourcePackagePath = uploadMinioObject(in, zipLocal.length(), "application/zip", assetRoot + "_source/" + orgName);
      }

      java.util.List<com.alibaba.fastjson.JSONObject> manifestItems = new java.util.ArrayList<>();
      final String[] mainObjectName = new String[1];
      java.nio.file.Path rootPath = extractedDir.toPath().toAbsolutePath().normalize();
      java.nio.file.Path mainPath = mainMd.toPath().toAbsolutePath().normalize();
      try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(rootPath)) {
        stream.filter(java.nio.file.Files::isRegularFile)
            .forEach(path -> {
              try {
                java.nio.file.Path normalizedPath = path.toAbsolutePath().normalize();
                String rel = rootPath.relativize(normalizedPath).toString().replace("\\", "/");
                String objectName = assetRoot + rel;
                String contentType = detectContentType(path.toFile(), rel);
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
                  uploadMinioObject(in, java.nio.file.Files.size(path), contentType, objectName);
                }
                if (normalizedPath.equals(mainPath)) {
                  mainObjectName[0] = objectName;
                }
                String fileExt = org.apache.commons.io.FilenameUtils.getExtension(rel).toLowerCase();
                if (PACKAGE_ASSET_EXT.contains(fileExt)) {
                  com.alibaba.fastjson.JSONObject item = new com.alibaba.fastjson.JSONObject();
                  item.put("relativePath", rel);
                  item.put("objectName", objectName);
                  item.put("contentType", contentType);
                  item.put("size", java.nio.file.Files.size(path));
                  manifestItems.add(item);
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
      }
      if (oConvertUtils.isEmpty(mainObjectName[0])) {
        return Result.error("主Markdown文档上传失败");
      }

      String mainRel = rootPath.relativize(mainMd.toPath().toAbsolutePath().normalize()).toString().replace("\\", "/");
      String mainName = org.apache.commons.io.FilenameUtils.getName(mainRel);
      int version = nextVersion(mainName, seg);
      markOldVersionsNotLatest(mainName, seg);

      BizDocFile doc = new BizDocFile();
      doc.setDisplayName((title != null && title.trim().length() > 0) ? title : org.apache.commons.io.FilenameUtils.getBaseName(mainName));
      doc.setOriginalName(mainName);
      doc.setActualFileName(mainName);
      doc.setVersion(version);
      doc.setUploadTime(new Date());
      doc.setFileType("md");
      doc.setCategoryPath(seg);
      doc.setFileYear(fileYear != null ? fileYear : Integer.valueOf(new java.text.SimpleDateFormat("yyyy").format(new Date())));
      doc.setRemark(remark);
      doc.setLatest(true);
      doc.setProcessStatus("uploaded");
      doc.setContentType("text/markdown;charset=UTF-8");
      doc.setSize(mainMd.length());
      doc.setStoragePath(buildMinioUrl(mainObjectName[0]));
      doc.setStorageFilename(mainObjectName[0]);
      doc.setMdConverted(true);
      doc.setMdPath(buildMinioUrl(mainObjectName[0]));
      doc.setAssetRoot(assetRoot);
      doc.setAssetManifest(com.alibaba.fastjson.JSON.toJSONString(manifestItems));
      doc.setSourcePackagePath(sourcePackagePath);
      doc.setCreateTime(new Date());
      boolean ok = bizDocFileService.save(doc);
      if (!ok) {
        return Result.error("保存失败");
      }

      rewriteAndUploadMarkdown(mainMd, doc, mainObjectName[0], getParentRelativePath(mainRel));
      return Result.OK(doc);
    } catch (Exception e) {
      log.error("ai5g markdown package import error", e);
      return Result.error(e.getMessage());
    } finally {
      if (workDir != null && workDir.exists()) {
        try {
          org.apache.commons.io.FileUtils.deleteDirectory(workDir);
        } catch (Exception e) {
          log.warn("清理Markdown资源包临时目录失败: {}", workDir.getAbsolutePath(), e);
        }
      }
    }
  }

  @GetMapping("/page")
  public Result<?> page(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                        @RequestParam(value = "typeCode1", required = false) String typeCode1,
                        @RequestParam(value = "typeCode2", required = false) String typeCode2,
                        @RequestParam(value = "typeCode3", required = false) String typeCode3,
                        @RequestParam(value = "title", required = false) String title,
                        @RequestParam(value = "fileYear", required = false) Integer fileYear) {
    Page<BizDocFile> page = new Page<>(pageNo, pageSize);
    QueryWrapper<BizDocFile> qw = new QueryWrapper<>();
    if (oConvertUtils.isNotEmpty(typeCode1) || oConvertUtils.isNotEmpty(typeCode2) || oConvertUtils.isNotEmpty(typeCode3)) {
      String seg;
      if (oConvertUtils.isNotEmpty(typeCode3) && typeCode3.length() == 6) {
        seg = typeCode3.substring(0,2)+"/"+typeCode3.substring(2,4)+"/"+typeCode3.substring(4,6);
      } else {
        StringBuilder sb = new StringBuilder();
        if (oConvertUtils.isNotEmpty(typeCode1)) sb.append(typeCode1);
        if (oConvertUtils.isNotEmpty(typeCode2)) sb.append("/").append(typeCode2);
        if (oConvertUtils.isNotEmpty(typeCode3)) sb.append("/").append(typeCode3);
        seg = sb.toString();
      }
      qw.likeRight("category_path", seg);
    }
    if (oConvertUtils.isNotEmpty(title)) qw.like("display_name", title);
    if (fileYear != null) qw.eq("file_year", fileYear);
    qw.orderByDesc("upload_time");
    return Result.OK(bizDocFileService.page(page, qw));
  }

  @GetMapping("/overview")
  public Result<?> overview() {
    java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();

    java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
    summary.put("total", countSql("SELECT COUNT(*) FROM biz_ai5g_docfile"));
    summary.put("latest", countSql("SELECT COUNT(*) FROM biz_ai5g_docfile WHERE latest = 1"));
    summary.put("mdConverted", countSql("SELECT COUNT(*) FROM biz_ai5g_docfile WHERE md_converted = 1"));
    summary.put("processing", countSql("SELECT COUNT(*) FROM biz_ai5g_docfile WHERE process_status = 'processing'"));
    summary.put("success", countSql("SELECT COUNT(*) FROM biz_ai5g_docfile WHERE process_status = 'success'"));
    summary.put("failed", countSql("SELECT COUNT(*) FROM biz_ai5g_docfile WHERE process_status = 'failed'"));
    summary.put("totalSize", jdbcTemplate.queryForObject("SELECT COALESCE(SUM(size), 0) FROM biz_ai5g_docfile", Long.class));
    data.put("summary", summary);

    data.put("status", jdbcTemplate.queryForList(
        "SELECT COALESCE(NULLIF(process_status, ''), 'unset') AS statusCode, COUNT(*) AS docCount " +
        "FROM biz_ai5g_docfile GROUP BY statusCode ORDER BY docCount DESC"));
    data.put("fileTypes", jdbcTemplate.queryForList(
        "SELECT COALESCE(NULLIF(file_type, ''), 'unknown') AS fileType, COUNT(*) AS docCount, " +
        "SUM(CASE WHEN latest = 1 THEN 1 ELSE 0 END) AS latestCount, " +
        "SUM(CASE WHEN md_converted = 1 THEN 1 ELSE 0 END) AS mdCount, COALESCE(SUM(size), 0) AS totalSize " +
        "FROM biz_ai5g_docfile GROUP BY file_type ORDER BY docCount DESC, fileType ASC"));
    data.put("categories", jdbcTemplate.queryForList(
        "SELECT COALESCE(NULLIF(category_path, ''), 'unclassified') AS categoryPath, COUNT(*) AS docCount, " +
        "SUM(CASE WHEN latest = 1 THEN 1 ELSE 0 END) AS latestCount, " +
        "SUM(CASE WHEN md_converted = 1 THEN 1 ELSE 0 END) AS mdCount, COALESCE(SUM(size), 0) AS totalSize, " +
        "MAX(upload_time) AS lastUploadTime " +
        "FROM biz_ai5g_docfile GROUP BY category_path ORDER BY docCount DESC, categoryPath ASC"));
    return Result.OK(data);
  }

  @GetMapping("/get/{id}")
  public Result<?> get(@PathVariable("id") String id) {
    BizDocFile doc = bizDocFileService.getById(id);
    if (doc == null) return Result.error("未找到文档");
    return Result.OK(doc);
  }

  @PostMapping("/convert/{id}")
  public Result<?> convertToMarkdown(@PathVariable("id") String id) {
    BizDocFile doc = bizDocFileService.getById(id);
    if (doc == null) return Result.error("未找到文档");

    if (!CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType) && !CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
      return Result.error("当前上传模式暂不支持后台Markdown转换: " + uploadType);
    }
    
    if ("processing".equals(doc.getProcessStatus())) {
      return Result.error("当前文档正在转换中，请勿重复提交");
    }
    if (!convertingDocIds.add(id)) {
      return Result.error("该文档已有转换任务，请稍后查看结果");
    }

    doc.setProcessStatus("processing");
    doc.setConvertStartedAt(null);
    doc.setMineruTaskId(null);
    doc.setMineruTaskStatus(null);
    doc.setMineruQueuedAhead(null);
    doc.setMineruError(null);
    doc.setMineruStartedAt(null);
    doc.setMineruCompletedAt(null);
    bizDocFileService.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizDocFile>()
        .eq(BizDocFile::getId, id)
        .set(BizDocFile::getProcessStatus, "processing")
        .set(BizDocFile::getConvertStartedAt, null)
        .set(BizDocFile::getMineruTaskId, null)
        .set(BizDocFile::getMineruTaskStatus, null)
        .set(BizDocFile::getMineruQueuedAhead, null)
        .set(BizDocFile::getMineruError, null)
        .set(BizDocFile::getMineruStartedAt, null)
        .set(BizDocFile::getMineruCompletedAt, null));

    try {
      mdConvertExecutor.submit(() -> {
        java.io.File workDir = null;
        try {
            String ft = doc.getFileType() == null ? "" : doc.getFileType().toLowerCase();
            
            workDir = java.nio.file.Files.createTempDirectory("ai5g-doc-convert-").toFile();
            String objectName = getMinioObjectName(doc.getStoragePath());
            java.io.File src;
            String mdRel;
            java.io.File mdFile;
            if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
                String sourceName = objectName;
                if (oConvertUtils.isEmpty(sourceName)) {
                    sourceName = doc.getActualFileName();
                }
                sourceName = sourceName == null ? "source" : sourceName.substring(sourceName.lastIndexOf('/') + 1);
                src = new java.io.File(workDir, sourceName);
                if (!downloadMinioObject(objectName, src)) {
                    log.error("MinIO源文件下载失败: {}", doc.getStoragePath());
                    doc.setProcessStatus("failed");
                    doc.setRemark("MinIO源文件下载失败");
                    bizDocFileService.updateById(doc);
                    return;
                }
                mdRel = objectName.replaceAll("\\.[^./\\\\]+$", "") + ".md";
                mdFile = new java.io.File(workDir, src.getName().replaceAll("\\.[^./\\\\]+$", "") + ".md");
            } else {
                src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
                mdRel = doc.getStoragePath().replaceAll("\\.[^./\\\\]+$", "") + ".md";
                mdFile = new java.io.File(uploadpath + java.io.File.separator + mdRel);
                mdFile.getParentFile().mkdirs();
            }
            if (!src.exists()) {
                log.error("源文件不存在: {}", src.getAbsolutePath());
                doc.setProcessStatus("failed");
                doc.setRemark("源文件不存在");
                bizDocFileService.updateById(doc);
                return;
            }

            boolean converted = false;
            boolean mineruTaskCompleted = false;
            java.io.File convertedAssetRootDir = null;
            java.io.File convertedAssetMarkdown = null;
            
            if ("csv".equals(ft)) {
                // CSV Special handling
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(src), java.nio.charset.StandardCharsets.UTF_8));
                     java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(mdFile), java.nio.charset.StandardCharsets.UTF_8))) {
                  String line;
                  boolean headerWritten = false;
                  while ((line = br.readLine()) != null) {
                    String[] cells = line.split(",", -1);
                    String row = "|" + String.join("|", cells) + "|";
                    bw.write(row);
                    bw.newLine();
                    if (!headerWritten) {
                      String sep = "|" + Arrays.stream(cells).map(c -> "---").reduce((a,b)-> a+"|"+b).orElse("") + "|";
                      bw.write(sep);
                      bw.newLine();
                      headerWritten = true;
                    }
                  }
                }
                converted = true;
            } else if (java.util.Arrays.asList("docx", "doc", "odt", "html", "epub", "pdf", "ppt", "pptx").contains(ft)) {
                // 统一使用 MinerU 解析 (优先远程，失败回退到本地，再失败回退到 Tika/Pandoc)
                // 1. Office 格式预转 PDF (MinerU 只认 PDF)
                java.io.File mineruInputFile = src;
                boolean isOffice = java.util.Arrays.asList("docx", "doc", "ppt", "pptx").contains(ft);
                
                if (isOffice) {
                    log.info("检测到 Office 文档 ({}), 正在通过 LibreOffice 预转为 PDF...", ft);
                    String baseName = src.getName().substring(0, src.getName().lastIndexOf('.'));
                    java.io.File pdfFile = new java.io.File(src.getParent(), baseName + ".pdf");
                    
                    // 尝试转 PDF
                    boolean toPdf = trySofficeCliConvert(src, pdfFile, "pdf");
                    if (toPdf && pdfFile.exists()) {
                        mineruInputFile = pdfFile;
                    } else {
                        log.warn("Office 预转 PDF 失败，将尝试使用 Tika/Pandoc 兜底");
                        isOffice = false; // 标记转换失败，后续不走 MinerU
                    }
                }
                
                // 2. 尝试 MinerU 远程解析
                String mineruUrl = environment.getProperty("jeecg.airag.know.mineru-url");
                String mineruMode = environment.getProperty("jeecg.airag.know.mineru-mode", "gradio");
                if (isOffice || "pdf".equals(ft)) {
                   if (org.jeecg.common.util.oConvertUtils.isNotEmpty(mineruUrl)) {
                        log.info("使用 MinerU 远程服务解析: {}, mode: {}", mineruUrl, mineruMode);
                  com.alibaba.fastjson.JSONObject mineruRes = null;
                  if ("api".equalsIgnoreCase(mineruMode)) {
                      String mineruTaskId = MineruClientUtil.submitParseTask(mineruUrl, mineruInputFile, mineruFastMode);
                      if (oConvertUtils.isNotEmpty(mineruTaskId)) {
                          doc.setMineruTaskId(mineruTaskId);
                          doc.setMineruTaskStatus("pending");
                          doc.setConvertStartedAt(new Date());
                          bizDocFileService.updateById(doc);
                          mineruRes = MineruClientUtil.waitForParseTask(mineruUrl, mineruTaskId, workDir.getAbsolutePath(), status -> updateMineruTaskStatus(doc, status));
                      } else {
                          log.warn("MinerU 异步任务提交失败，尝试本地/兜底转换");
                      }
                  } else {
                      mineruRes = MineruClientUtil.parsePdf(mineruUrl, mineruInputFile, mineruMode, mineruFastMode);
                  }
                  if (mineruRes != null && org.jeecg.common.util.oConvertUtils.isNotEmpty(mineruRes.getString("content"))) {
                      try {
                                String markdownPath = mineruRes.getString("markdownPath");
                                String extractDir = mineruRes.getString("extractDir");
                                mineruTaskCompleted = "api".equalsIgnoreCase(mineruMode) && oConvertUtils.isNotEmpty(doc.getMineruTaskId());
                                if (oConvertUtils.isNotEmpty(markdownPath) && new java.io.File(markdownPath).exists()) {
                                    java.io.File mineruMarkdown = new java.io.File(markdownPath);
                                    org.apache.commons.io.FileUtils.copyFile(mineruMarkdown, mdFile);
                                    if (oConvertUtils.isNotEmpty(extractDir) && new java.io.File(extractDir).exists()) {
                                        convertedAssetRootDir = new java.io.File(extractDir);
                                        convertedAssetMarkdown = mineruMarkdown;
                                    }
                                } else {
                                    org.apache.commons.io.FileUtils.writeStringToFile(mdFile, mineruRes.getString("content"), java.nio.charset.StandardCharsets.UTF_8);
                                }
                                converted = true;
                            } catch (Exception e) {
                                log.error("保存 MinerU 结果失败", e);
                            }
                        }
                    }
                    
                    // 3. 尝试 MinerU 本地命令
                    if (!converted) {
                        com.alibaba.fastjson.JSONObject localMineruRes = tryMineruLocalConvert(mineruInputFile, mdFile);
                        converted = localMineruRes != null && Boolean.TRUE.equals(localMineruRes.getBoolean("converted"));
                        if (converted) {
                            String markdownPath = localMineruRes.getString("markdownPath");
                            String extractDir = localMineruRes.getString("extractDir");
                            if (oConvertUtils.isNotEmpty(markdownPath) && new java.io.File(markdownPath).exists()
                                && oConvertUtils.isNotEmpty(extractDir) && new java.io.File(extractDir).exists()) {
                                convertedAssetRootDir = new java.io.File(extractDir);
                                convertedAssetMarkdown = new java.io.File(markdownPath);
                            }
                        }
                    }
                }
                
                // 4. 兜底方案 (Pandoc 或 Tika)
                if (!converted) {
                    if (java.util.Arrays.asList("docx", "doc", "odt", "html", "epub").contains(ft)) {
                         converted = tryPandocConvert(src, mdFile, ft);
                    } else if ("pdf".equals(ft)) {
                         converted = tryTikaConvert(src, mdFile);
                    }
                }
                
                // 清理临时 PDF
                if (mineruInputFile != src && mineruInputFile.exists()) {
                    mineruInputFile.delete();
                }

            } else if (java.util.Arrays.asList("xls", "xlsx").contains(ft)) {
                // XLS/XLSX -> CSV -> Markdown Table
                // LibreOffice converts "foo.xlsx" to "foo.csv" in the outdir.
                String baseName = src.getName().substring(0, src.getName().lastIndexOf('.'));
                java.io.File tempCsv = new java.io.File(src.getParent(), baseName + ".csv");
                
                boolean toCsv = trySofficeCliConvert(src, tempCsv, "csv");
                if (toCsv && tempCsv.exists()) {
                    // Now read tempCsv and write to mdFile
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(tempCsv), java.nio.charset.StandardCharsets.UTF_8));
                         java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(mdFile), java.nio.charset.StandardCharsets.UTF_8))) {
                      String line;
                      boolean headerWritten = false;
                      while ((line = br.readLine()) != null) {
                        // Simple CSV split
                        String[] cells = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                        for(int i=0; i<cells.length; i++) {
                            cells[i] = cells[i].replaceAll("^\"|\"$", "").replace("\"\"", "\""); 
                        }
                        
                        String row = "|" + String.join("|", cells) + "|";
                        bw.write(row);
                        bw.newLine();
                        if (!headerWritten) {
                          String sep = "|" + Arrays.stream(cells).map(c -> "---").reduce((a,b)-> a+"|"+b).orElse("") + "|";
                          bw.write(sep);
                          bw.newLine();
                          headerWritten = true;
                        }
                      }
                    }
                    converted = true;
                    tempCsv.delete();
                }
            } else {
                doc.setProcessStatus("failed");
                doc.setRemark("暂不支持该文件类型转换为 Markdown");
                bizDocFileService.updateById(doc);
                return;
            }

            if (converted && mdFile.exists()) {
                boolean saved = saveConvertedMarkdown(doc, mdFile, mdRel, convertedAssetRootDir, convertedAssetMarkdown);
                if (saved) {
                    if (mineruTaskCompleted) {
                        doc.setMineruTaskStatus("completed");
                        doc.setMineruCompletedAt(new Date());
                    }
                    boolean clearRemark = isConversionFailureRemark(doc.getRemark());
                    doc.setMdConverted(true);
                    if (clearRemark) {
                        doc.setRemark(null);
                    }
                    doc.setProcessStatus("success");
                    bizDocFileService.updateById(doc);
                    if (clearRemark) {
                        forceClearRemark(doc.getId());
                    }
                } else {
                    doc.setProcessStatus("failed");
                    doc.setRemark("转换结果保存失败");
                    bizDocFileService.updateById(doc);
                }
            } else {
                if (oConvertUtils.isNotEmpty(doc.getMineruTaskId())) {
                    doc.setMineruTaskStatus("failed");
                }
                doc.setProcessStatus("failed");
                doc.setRemark("转换失败");
                bizDocFileService.updateById(doc);
            }
        } catch (Exception e) {
            log.error("convert to md error", e);
            if (oConvertUtils.isNotEmpty(doc.getMineruTaskId())) {
                doc.setMineruTaskStatus("failed");
            }
            doc.setProcessStatus("failed");
            doc.setRemark(truncateText("转换异常: " + e.getMessage(), 500));
            bizDocFileService.updateById(doc);
        } finally {
            if (workDir != null && workDir.exists()) {
                try {
                    org.apache.commons.io.FileUtils.deleteDirectory(workDir);
                } catch (Exception e) {
                    log.warn("清理转换临时目录失败: {}", workDir.getAbsolutePath(), e);
                }
            }
            convertingDocIds.remove(id);
        }
      });
    } catch (Exception e) {
      convertingDocIds.remove(id);
      doc.setProcessStatus("failed");
      doc.setRemark(truncateText("转换任务提交失败: " + e.getMessage(), 500));
      bizDocFileService.updateById(doc);
      return Result.error("转换任务提交失败: " + e.getMessage());
    }

    return Result.OK("转换任务已提交，请稍候查看结果");
  }

  private com.alibaba.fastjson.JSONObject tryMineruLocalConvert(java.io.File src, java.io.File mdFile) {
      try {
          String condaEnv = environment.getProperty("jeecg.airag.know.conda-env", "mineru");
          String outputPath = src.getParentFile().getAbsolutePath();
          
          // Office 预转换 PDF (MinerU 仅支持 PDF)
          java.io.File mineruInputFile = src;
          String fileType = src.getName().substring(src.getName().lastIndexOf('.') + 1).toLowerCase();
          boolean isOffice = java.util.Arrays.asList("docx", "doc", "xlsx", "xls", "pptx", "ppt").contains(fileType);
          
          if (isOffice) {
              log.info("检测到 Office 文档 ({}), 正在通过 LibreOffice 预转为 PDF...", fileType);
              String sofficeCommand = "/Applications/LibreOffice.app/Contents/MacOS/soffice";
              if (!new java.io.File(sofficeCommand).exists()) {
                  sofficeCommand = "soffice";
              }
              String baseName = src.getName().substring(0, src.getName().lastIndexOf('.'));
              
              java.util.List<String> sofficeCmd = new java.util.ArrayList<>();
              sofficeCmd.add(sofficeCommand);
              sofficeCmd.add("--headless");
              sofficeCmd.add("--convert-to");
              sofficeCmd.add("pdf");
              sofficeCmd.add("--outdir");
              sofficeCmd.add(outputPath);
              sofficeCmd.add(src.getAbsolutePath());
              
              java.lang.Process p = new java.lang.ProcessBuilder(sofficeCmd).start();
              p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
              
              java.io.File generatedPdf = new java.io.File(outputPath + java.io.File.separator + baseName + ".pdf");
              if (generatedPdf.exists()) {
                  mineruInputFile = generatedPdf;
              } else {
                  log.warn("Office 预转 PDF 失败，跳过 MinerU 解析");
                  return null;
              }
          }

          java.util.List<String> command = new java.util.ArrayList<>();
          command.add("conda");
          command.add("run");
          command.add("-n");
          command.add(condaEnv);
          command.add("mineru");
          command.add("-p");
          command.add(mineruInputFile.getAbsolutePath());
          command.add("-o");
          command.add(outputPath);

          log.info("Executing local MinerU: {}", String.join(" ", command));
          java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(command);
          pb.redirectErrorStream(true);
          java.lang.Process p = pb.start();
          
          try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
              String line;
              while ((line = reader.readLine()) != null) {
                  log.info("MinerU CLI Output: {}", line);
              }
          }
          
          boolean finished = p.waitFor(300, java.util.concurrent.TimeUnit.SECONDS);
          if (!finished) {
              p.destroyForcibly();
              return null;
          }

          if (p.exitValue() == 0) {
              String baseName = src.getName().substring(0, src.getName().lastIndexOf('.'));
              java.io.File outputRoot = new java.io.File(outputPath, baseName);
              java.io.File generatedMd = findMineruGeneratedMarkdown(outputRoot, baseName);
              if (generatedMd.exists()) {
                  org.apache.commons.io.FileUtils.copyFile(generatedMd, mdFile);
                  com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject();
                  result.put("converted", true);
                  result.put("markdownPath", generatedMd.getAbsolutePath());
                  result.put("extractDir", generatedMd.getParentFile().getAbsolutePath());
                  log.info("Local MinerU conversion success, md={}, assetDir={}", generatedMd.getAbsolutePath(), generatedMd.getParentFile().getAbsolutePath());
                  return result;
              }
              log.warn("Local MinerU exit success but markdown not found under: {}", outputRoot.getAbsolutePath());
          }
          return null;
      } catch (Exception e) {
          log.error("Local MinerU conversion error", e);
          return null;
      }
  }

  private java.io.File findMineruGeneratedMarkdown(java.io.File outputRoot, String baseName) throws java.io.IOException {
      java.io.File autoMd = new java.io.File(outputRoot, "auto" + java.io.File.separator + baseName + ".md");
      if (autoMd.exists()) {
          return autoMd;
      }
      java.io.File hybridAutoMd = new java.io.File(outputRoot, "hybrid_auto" + java.io.File.separator + baseName + ".md");
      if (hybridAutoMd.exists()) {
          return hybridAutoMd;
      }
      if (!outputRoot.exists()) {
          return autoMd;
      }
      java.util.List<java.nio.file.Path> markdownFiles = new java.util.ArrayList<>();
      try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(outputRoot.toPath())) {
          stream.filter(java.nio.file.Files::isRegularFile)
              .filter(path -> "md".equalsIgnoreCase(org.apache.commons.io.FilenameUtils.getExtension(path.getFileName().toString())))
              .forEach(markdownFiles::add);
      }
      if (markdownFiles.isEmpty()) {
          return autoMd;
      }
      markdownFiles.sort(java.util.Comparator
          .comparingInt((java.nio.file.Path path) -> mineruOutputPriority(path))
          .thenComparing(path -> path.getFileName().toString().equals(baseName + ".md") ? 0 : 1)
          .thenComparing(path -> path.toString()));
      return markdownFiles.get(0).toFile();
  }

  private int mineruOutputPriority(java.nio.file.Path path) {
      String normalized = path.toString().replace("\\", "/");
      if (normalized.contains("/auto/")) {
          return 0;
      }
      if (normalized.contains("/hybrid_auto/")) {
          return 1;
      }
      return 2;
  }

  private boolean tryTikaConvert(java.io.File src, java.io.File mdFile) {
      try (java.io.InputStream stream = new java.io.FileInputStream(src);
           java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(mdFile), java.nio.charset.StandardCharsets.UTF_8))) {
          
          BodyContentHandler handler = new BodyContentHandler(-1); // No limit
          Metadata metadata = new Metadata();
          ParseContext pcontext = new ParseContext();
          
          // PDFParser
          PDFParser pdfparser = new PDFParser();
          pdfparser.parse(stream, handler, metadata, pcontext);
          
          String text = handler.toString();
          writer.write(text);
          return true;
      } catch (Exception e) {
          log.error("Tika conversion error", e);
          return false;
      }
  }

  private boolean tryPandocConvert(java.io.File src, java.io.File mdFile, String fileType) {
      try {
          String pandoc = "/opt/homebrew/bin/pandoc"; 
          if (!new java.io.File(pandoc).exists()) pandoc = "pandoc";
          
          java.io.File convertSrc = src;
          java.io.File tempDocx = null;
          
          if ("doc".equals(fileType)) {
              // Convert .doc to .docx first
              String tempDocxPath = src.getParent() + java.io.File.separator + "temp_" + System.currentTimeMillis() + ".docx";
              tempDocx = new java.io.File(tempDocxPath);
              boolean docToDocx = trySofficeCliConvert(src, tempDocx, "docx");
              if (!docToDocx) return false;
              convertSrc = tempDocx;
          }

          java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(
              pandoc,
              convertSrc.getAbsolutePath(),
              "-o", mdFile.getAbsolutePath()
          );
          
          pb.redirectErrorStream(true);
          log.info("Executing Pandoc: {}", String.join(" ", pb.command()));
          java.lang.Process p = pb.start();
          
          try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
             String line;
             while ((line = reader.readLine()) != null) {
                 log.info("Pandoc Output: {}", line);
             }
          }
          
          boolean finished = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
          if (tempDocx != null && tempDocx.exists()) tempDocx.delete();
          
          if (!finished) {
              p.destroyForcibly();
              return false;
          }
          return p.exitValue() == 0 && mdFile.exists();
      } catch (Exception e) {
          log.error("Pandoc error", e);
          return false;
      }
  }

  private boolean downloadMinioObject(String objectName, java.io.File targetFile) {
      if (oConvertUtils.isEmpty(objectName)) {
          return false;
      }
      java.io.File parent = targetFile.getParentFile();
      if (parent != null && !parent.exists()) {
          parent.mkdirs();
      }
      try (java.io.InputStream in = getMinioObjectStream(objectName);
           java.io.OutputStream out = new java.io.FileOutputStream(targetFile)) {
          if (in == null) {
              return false;
          }
          byte[] buffer = new byte[8192];
          int len;
          while ((len = in.read(buffer)) != -1) {
              out.write(buffer, 0, len);
          }
          return true;
      } catch (Exception e) {
          log.error("MinIO文件下载失败: {}", objectName, e);
          return false;
      }
  }

  private String uploadMinioFile(MultipartFile file, String bizPath) throws Exception {
      SsrfFileTypeFilter.checkUploadFileType(file, bizPath);
      String orgName = file.getOriginalFilename();
      if (oConvertUtils.isEmpty(orgName)) {
          orgName = file.getName();
      }
      orgName = CommonUtils.getFileName(orgName);
      int dot = orgName.lastIndexOf(".");
      String ext = dot >= 0 ? orgName.substring(dot).toLowerCase() : "";
      String fileName = System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "") + ext;
      String objectName = (bizPath + "/" + fileName).replace("\\", "/");
      while (objectName.startsWith("/")) {
          objectName = objectName.substring(1);
      }
      io.minio.MinioClient client = buildMinioClient();
      log.info("AI5G MinIO upload start, endpoint={}, bucket={}, object={}, size={}, contentType={}",
          minioUrl, minioBucketName, objectName, file.getSize(), file.getContentType());
      String contentType = oConvertUtils.isEmpty(file.getContentType()) ? "application/octet-stream" : file.getContentType();
      try (java.io.InputStream in = file.getInputStream()) {
          client.putObject(io.minio.PutObjectArgs.builder()
              .bucket(minioBucketName)
              .object(objectName)
              .contentType(contentType)
              .stream(in, -1L, MINIO_PART_SIZE)
              .build());
      }
      log.info("AI5G MinIO upload success, bucket={}, object={}", minioBucketName, objectName);
      String baseUrl = minioUrl.endsWith("/") ? minioUrl : minioUrl + "/";
      return baseUrl + minioBucketName + "/" + objectName;
  }

  private String uploadMinioObject(java.io.InputStream in, long size, String contentType, String objectName) throws Exception {
      objectName = normalizeObjectName(objectName);
      io.minio.MinioClient client = buildMinioClient();
      if (!client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(minioBucketName).build())) {
          client.makeBucket(io.minio.MakeBucketArgs.builder().bucket(minioBucketName).build());
      }
      client.putObject(io.minio.PutObjectArgs.builder()
          .bucket(minioBucketName)
          .object(objectName)
          .contentType(contentType)
          .stream(in, size, -1L)
          .build());
      String baseUrl = minioUrl.endsWith("/") ? minioUrl : minioUrl + "/";
      return baseUrl + minioBucketName + "/" + objectName;
  }

  private java.io.InputStream getMinioObjectStream(String objectName) {
      try {
          return buildMinioClient().getObject(io.minio.GetObjectArgs.builder()
              .bucket(minioBucketName)
              .object(objectName)
              .build());
      } catch (Exception e) {
          log.error("MinIO文件读取失败: {}", objectName, e);
          return null;
      }
  }

  private io.minio.MinioClient buildMinioClient() {
      okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
          .proxy(java.net.Proxy.NO_PROXY)
          .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
          .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
          .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
          .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
          .build();
      return io.minio.MinioClient.builder()
          .endpoint(minioUrl)
          .credentials(minioName, minioPass)
          .httpClient(httpClient)
          .build();
  }

  private String getMinioObjectName(String path) {
      if (oConvertUtils.isEmpty(path)) {
          return path;
      }
      String value = path.trim();
      String bucketName = minioBucketName;
      if (oConvertUtils.isNotEmpty(minioUrl) && oConvertUtils.isNotEmpty(bucketName)) {
          String prefix = minioUrl.endsWith("/") ? minioUrl + bucketName + "/" : minioUrl + "/" + bucketName + "/";
          if (value.startsWith(prefix)) {
              return value.substring(prefix.length());
          }
      }
      try {
          java.net.URI uri = java.net.URI.create(value);
          if (uri.getScheme() != null && uri.getPath() != null) {
              String p = uri.getPath();
              String bucketPrefix = "/" + bucketName + "/";
              int idx = p.indexOf(bucketPrefix);
              if (idx >= 0) {
                  return p.substring(idx + bucketPrefix.length());
              }
          }
      } catch (Exception ignore) {
      }
      while (value.startsWith("/")) {
          value = value.substring(1);
      }
      if (oConvertUtils.isNotEmpty(bucketName) && value.startsWith(bucketName + "/")) {
          value = value.substring(bucketName.length() + 1);
      }
      return value;
  }

  private String resolveMinioObjectName(BizDocFile doc) {
      if (doc == null) {
          return null;
      }
      String objectName = getMinioObjectName(doc.getStoragePath());
      if (oConvertUtils.isEmpty(objectName)) {
          objectName = getMinioObjectName(doc.getStorageFilename());
      }
      return objectName;
  }

  private String buildMinioUrl(String objectName) {
      String baseUrl = minioUrl.endsWith("/") ? minioUrl : minioUrl + "/";
      return baseUrl + minioBucketName + "/" + normalizeObjectName(objectName);
  }

  private String buildStorageBizPath() {
      String month = new java.text.SimpleDateFormat("yyyyMM").format(new Date());
      return baseDir + "/files/" + month;
  }

  private String buildPackageAssetRoot(String packageId) {
      return baseDir + "/packages/" + packageId + "/";
  }

  private String validateCategory(String typeCode1, String typeCode2, String typeCode3) {
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.jeecg.modules.biz.ai5g.entity.BizDocType> w1 = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      w1.eq("level", 1).eq("code", typeCode1);
      org.jeecg.modules.biz.ai5g.entity.BizDocType t1 = bizDocTypeService.getOne(w1, false);
      if (t1 == null) throw new IllegalArgumentException("一级类型不存在: " + typeCode1);

      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.jeecg.modules.biz.ai5g.entity.BizDocType> w2 = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      w2.eq("level", 2).eq("code", typeCode2).eq("parent_code", typeCode1);
      org.jeecg.modules.biz.ai5g.entity.BizDocType t2 = bizDocTypeService.getOne(w2, false);
      if (t2 == null) throw new IllegalArgumentException("二级类型不存在或父级不匹配: " + typeCode2);

      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<org.jeecg.modules.biz.ai5g.entity.BizDocType> w3 = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      w3.eq("level", 3).eq("code", typeCode3).eq("parent_code", typeCode2);
      org.jeecg.modules.biz.ai5g.entity.BizDocType t3 = bizDocTypeService.getOne(w3, false);
      if (t3 == null) throw new IllegalArgumentException("三级类型不存在或父级不匹配: " + typeCode3);

      return typeCode3 != null && typeCode3.length() == 6 ? (typeCode3.substring(0,2) + "/" + typeCode3.substring(2,4) + "/" + typeCode3.substring(4,6)) : typeCode3;
  }

  private int nextVersion(String originalName, String categoryPath) {
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BizDocFile> vq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      vq.select("id","version");
      vq.eq("original_name", originalName);
      if (oConvertUtils.isEmpty(categoryPath)) {
          vq.isNull("category_path");
      } else {
          vq.eq("category_path", categoryPath);
      }
      vq.orderByDesc("version");
      java.util.List<BizDocFile> vers = bizDocFileService.list(vq);
      BizDocFile last = vers != null && !vers.isEmpty() ? vers.get(0) : null;
      return last == null ? 1 : (last.getVersion() == null ? 1 : last.getVersion() + 1);
  }

  private void markOldVersionsNotLatest(String originalName, String categoryPath) {
      if (oConvertUtils.isEmpty(originalName)) {
          return;
      }
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BizDocFile> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      uw.eq("original_name", originalName).set("latest", false);
      if (oConvertUtils.isEmpty(categoryPath)) {
          uw.isNull("category_path");
      } else {
          uw.eq("category_path", categoryPath);
      }
      bizDocFileService.update(uw);
  }

  private void recomputeLatest(String originalName, String categoryPath) {
      if (oConvertUtils.isEmpty(originalName)) {
          return;
      }
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BizDocFile> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      qw.select("id").eq("original_name", originalName).orderByDesc("version").orderByDesc("upload_time");
      if (oConvertUtils.isEmpty(categoryPath)) {
          qw.isNull("category_path");
      } else {
          qw.eq("category_path", categoryPath);
      }
      java.util.List<BizDocFile> remaining = bizDocFileService.list(qw);
      if (remaining == null || remaining.isEmpty()) {
          return;
      }
      String latestId = remaining.get(0).getId();
      markOldVersionsNotLatest(originalName, categoryPath);
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BizDocFile> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      uw.eq("id", latestId).set("latest", true);
      bizDocFileService.update(uw);
  }

  private void rewriteAndUploadMarkdown(java.io.File mainMd, BizDocFile doc, String objectName) throws Exception {
      rewriteAndUploadMarkdown(mainMd, doc, objectName, "");
  }

  private void rewriteAndUploadMarkdown(java.io.File mainMd, BizDocFile doc, String objectName, String mainRelativeDir) throws Exception {
      String content = org.apache.commons.io.FileUtils.readFileToString(mainMd, java.nio.charset.StandardCharsets.UTF_8);
      content = rewriteMarkdownAssetUrls(content, doc, DOMAIN_URL_PLACEHOLDER, mainRelativeDir);
      byte[] data = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      try (java.io.InputStream in = new java.io.ByteArrayInputStream(data)) {
          uploadMinioObject(in, data.length, "text/markdown;charset=UTF-8", objectName);
      }
      bizDocFileService.updateById(doc);
  }

  private com.alibaba.fastjson.JSONObject uploadConvertedMarkdownPackage(java.nio.file.Path packageRoot, java.io.File mainMd, BizDocFile doc) throws Exception {
      java.nio.file.Path rootPath = packageRoot.toRealPath();
      java.nio.file.Path mainPath = mainMd.toPath().toRealPath();
      if (!mainPath.startsWith(rootPath)) {
          throw new IllegalArgumentException("转换结果主Markdown不在资源目录内: root=" + rootPath + ", markdown=" + mainPath);
      }
      String assetRoot = oConvertUtils.isNotEmpty(doc.getAssetRoot())
          ? normalizeObjectName(doc.getAssetRoot())
          : normalizeObjectName(buildPackageAssetRoot(java.util.UUID.randomUUID().toString().replace("-", "")));
      java.util.List<com.alibaba.fastjson.JSONObject> manifestItems = new java.util.ArrayList<>();
      final String[] mainObjectName = new String[1];

      try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(rootPath)) {
          stream.filter(java.nio.file.Files::isRegularFile)
              .filter(path -> isConvertedResultAsset(path, mainPath))
              .forEach(path -> {
                  try {
                      java.nio.file.Path normalizedPath = path.toAbsolutePath().normalize();
                      String rel = rootPath.relativize(normalizedPath).toString().replace("\\", "/");
                      String objectName = assetRoot + rel;
                      String contentType = detectContentType(path.toFile(), rel);
                      try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
                          uploadMinioObject(in, java.nio.file.Files.size(path), contentType, objectName);
                      }
                      if (normalizedPath.equals(mainPath)) {
                          mainObjectName[0] = objectName;
                      }
                      String fileExt = org.apache.commons.io.FilenameUtils.getExtension(rel).toLowerCase();
                      if (PACKAGE_ASSET_EXT.contains(fileExt)) {
                          com.alibaba.fastjson.JSONObject item = new com.alibaba.fastjson.JSONObject();
                          item.put("relativePath", rel);
                          item.put("objectName", objectName);
                          item.put("contentType", contentType);
                          item.put("size", java.nio.file.Files.size(path));
                          manifestItems.add(item);
                      }
                  } catch (Exception e) {
                      throw new RuntimeException(e);
                  }
              });
      }
      if (oConvertUtils.isEmpty(mainObjectName[0])) {
          throw new IllegalStateException("转换结果主Markdown上传失败");
      }
      String mainRel = rootPath.relativize(mainPath).toString().replace("\\", "/");

      com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject();
      result.put("assetRoot", assetRoot);
      result.put("assetManifest", com.alibaba.fastjson.JSON.toJSONString(manifestItems));
      result.put("mainObjectName", mainObjectName[0]);
      result.put("mainRelativeDir", getParentRelativePath(mainRel));
      return result;
  }

  private boolean isConvertedResultAsset(java.nio.file.Path path, java.nio.file.Path mainPath) {
      try {
          java.nio.file.Path normalizedPath = path.toAbsolutePath().normalize();
          if (normalizedPath.equals(mainPath)) {
              return true;
          }
          String fileExt = org.apache.commons.io.FilenameUtils.getExtension(normalizedPath.getFileName().toString()).toLowerCase();
          return PACKAGE_ASSET_EXT.contains(fileExt);
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
  }

  private String rewriteMarkdownAssetUrls(String content, BizDocFile doc) {
      return rewriteMarkdownAssetUrls(content, doc, DOMAIN_URL_PLACEHOLDER, "");
  }

  private String rewriteMarkdownAssetUrls(String content, BizDocFile doc, String assetUrlBase) {
      return rewriteMarkdownAssetUrls(content, doc, assetUrlBase, "");
  }

  private String rewriteMarkdownAssetUrls(String content, BizDocFile doc, String assetUrlBase, String mainRelativeDir) {
      if (oConvertUtils.isEmpty(content) || doc == null || oConvertUtils.isEmpty(doc.getAssetRoot()) || oConvertUtils.isEmpty(doc.getId())) {
          return content;
      }
      String urlBase = oConvertUtils.isEmpty(assetUrlBase) ? DOMAIN_URL_PLACEHOLDER : assetUrlBase;
      if (urlBase.endsWith("/")) {
          urlBase = urlBase.substring(0, urlBase.length() - 1);
      }
      String assetRootObject = normalizeObjectName(getMinioObjectName(doc.getAssetRoot()));
      String assetPrefix = "/ai5g/doc/assets/" + doc.getId() + "/";
      java.util.regex.Matcher matcher = MD_IMAGE_PATTERN.matcher(content);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
          String alt = matcher.group(1);
          String imageUrl = matcher.group(2);
          String replacementUrl = imageUrl;
          if (oConvertUtils.isNotEmpty(imageUrl)
              && !imageUrl.startsWith("data:")
              && !imageUrl.startsWith(DOMAIN_URL_PLACEHOLDER + "/ai5g/doc/assets/")) {
              String normalizedImageUrl = imageUrl.trim().replace("\\", "/");
              String objectPath = getMinioObjectName(normalizedImageUrl);
              if (oConvertUtils.isNotEmpty(assetRootObject) && oConvertUtils.isNotEmpty(objectPath) && objectPath.startsWith(assetRootObject)) {
                  replacementUrl = urlBase + assetPrefix + objectPath.substring(assetRootObject.length());
              } else if (!normalizedImageUrl.startsWith("http://") && !normalizedImageUrl.startsWith("https://")) {
                  if (normalizedImageUrl.startsWith(assetPrefix)) {
                      replacementUrl = urlBase + normalizedImageUrl;
                  } else {
                      replacementUrl = urlBase + assetPrefix + resolvePackageRelativePath(normalizedImageUrl, mainRelativeDir);
                  }
              } else if (objectPath.startsWith(assetRootObject) && oConvertUtils.isNotEmpty(assetRootObject)) {
                  replacementUrl = urlBase + assetPrefix + objectPath.substring(assetRootObject.length());
              }
          } else if (imageUrl.startsWith(DOMAIN_URL_PLACEHOLDER + "/ai5g/doc/assets/")) {
              replacementUrl = urlBase + imageUrl.substring(DOMAIN_URL_PLACEHOLDER.length());
          }
          matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("![" + alt + "](" + replacementUrl + ")"));
      }
      matcher.appendTail(sb);
      return sb.toString();
  }

  private String buildRequestBaseUrl(jakarta.servlet.http.HttpServletRequest request) {
      String forwardedHost = request.getHeader("X-Forwarded-Host");
      String forwardedProto = request.getHeader("X-Forwarded-Proto");
      if (oConvertUtils.isNotEmpty(forwardedHost)) {
          String protocol = oConvertUtils.isNotEmpty(forwardedProto) ? forwardedProto : request.getScheme();
          String host = forwardedHost.split(",")[0].trim();
          StringBuilder base = new StringBuilder();
          base.append(protocol).append("://").append(host);
          if (host.indexOf(':') < 0) {
              int port = request.getServerPort();
              if (port > 0 && port != 80 && port != 443 && oConvertUtils.isEmpty(forwardedProto)) {
                  base.append(":").append(port);
              }
          }
          return buildPublicPath(request, base.toString());
      }

      String origin = request.getHeader("Origin");
      if (oConvertUtils.isEmpty(origin)) {
          String referer = request.getHeader("Referer");
          if (oConvertUtils.isNotEmpty(referer)) {
              try {
                  java.net.URI refererUri = java.net.URI.create(referer);
                  if (refererUri.getScheme() != null && refererUri.getHost() != null) {
                      origin = refererUri.getScheme() + "://" + refererUri.getHost();
                      if (refererUri.getPort() > 0) {
                          origin += ":" + refererUri.getPort();
                      }
                  }
              } catch (Exception ignore) {
              }
          }
      }
      if (oConvertUtils.isEmpty(origin)) {
          StringBuilder base = new StringBuilder();
          base.append(request.getScheme()).append("://").append(request.getServerName());
          int port = request.getServerPort();
          if (port > 0 && port != 80 && port != 443) {
              base.append(":").append(port);
          }
          origin = base.toString();
      }

      return buildPublicPath(request, origin);
  }

  private String buildPublicPath(jakarta.servlet.http.HttpServletRequest request, String origin) {
      String publicPath = request.getContextPath();
      if (oConvertUtils.isEmpty(publicPath)) {
          publicPath = "/jeecgboot";
      } else if (publicPath.contains("jeecg-boot")) {
          publicPath = publicPath.replace("jeecg-boot", "jeecgboot");
      }
      if (!publicPath.startsWith("/")) {
          publicPath = "/" + publicPath;
      }
      if (publicPath.endsWith("/")) {
          publicPath = publicPath.substring(0, publicPath.length() - 1);
      }
      return origin + publicPath;
  }

  private String normalizeObjectName(String objectName) {
      String value = objectName == null ? "" : objectName.trim().replace("\\", "/");
      while (value.startsWith("/")) {
          value = value.substring(1);
      }
      return value.replaceAll("/{2,}", "/");
  }

  private String normalizePackageRelativePath(String relativePath) {
      String value = relativePath == null ? "" : relativePath.trim().replace("\\", "/");
      int queryIndex = value.indexOf('?');
      if (queryIndex >= 0) {
          value = value.substring(0, queryIndex);
      }
      int hashIndex = value.indexOf('#');
      if (hashIndex >= 0) {
          value = value.substring(0, hashIndex);
      }
      while (value.startsWith("/")) {
          value = value.substring(1);
      }
      java.nio.file.Path normalized = java.nio.file.Paths.get(value).normalize();
      String result = normalized.toString().replace("\\", "/");
      if (result.startsWith("../") || result.equals("..") || result.startsWith("/")) {
          throw new IllegalArgumentException("非法资源路径: " + relativePath);
      }
      return result;
  }

  private String resolvePackageRelativePath(String assetPath, String mainRelativeDir) {
      String value = assetPath == null ? "" : assetPath.trim().replace("\\", "/");
      int queryIndex = value.indexOf('?');
      String suffix = "";
      if (queryIndex >= 0) {
          suffix = value.substring(queryIndex);
          value = value.substring(0, queryIndex);
      }
      int hashIndex = value.indexOf('#');
      if (hashIndex >= 0) {
          suffix = value.substring(hashIndex) + suffix;
          value = value.substring(0, hashIndex);
      }
      while (value.startsWith("/")) {
          value = value.substring(1);
      }
      String base = oConvertUtils.isEmpty(mainRelativeDir) ? "" : normalizePackageRelativePath(mainRelativeDir);
      String combined = oConvertUtils.isEmpty(base) || value.startsWith(base + "/") ? value : base + "/" + value;
      return normalizePackageRelativePath(combined) + suffix;
  }

  private String getParentRelativePath(String relativePath) {
      if (oConvertUtils.isEmpty(relativePath)) {
          return "";
      }
      String normalized = normalizePackageRelativePath(relativePath);
      int slashIndex = normalized.lastIndexOf('/');
      return slashIndex >= 0 ? normalized.substring(0, slashIndex) : "";
  }

  private boolean isConversionFailureRemark(String remark) {
      if (oConvertUtils.isEmpty(remark)) {
          return false;
      }
      return remark.startsWith("转换失败")
          || remark.startsWith("转换异常")
          || remark.startsWith("MinIO源文件下载失败")
          || remark.startsWith("MinIO结果上传卡住")
          || remark.startsWith("源文件不存在")
          || remark.startsWith("服务重启导致转换中断")
          || remark.startsWith("MinerU处理超时/卡住")
          || remark.startsWith("MinerU 异步任务参数不完整")
          || remark.startsWith("MinerU 异步任务未返回有效结果")
          || remark.startsWith("MinerU 异步结果保存失败")
          || remark.startsWith("MinerU 异步任务恢复处理失败")
          || remark.startsWith("恢复 MinerU 异步任务异常")
          || remark.startsWith("转换结果保存失败")
          || remark.startsWith("转换任务提交失败")
          || remark.startsWith("暂不支持该文件类型转换为 Markdown");
  }

  private String truncateText(String text, int maxLength) {
      if (text == null || text.length() <= maxLength) {
          return text;
      }
      return text.substring(0, maxLength);
  }

  private void forceClearRemark(String docId) {
      if (oConvertUtils.isEmpty(docId)) {
          return;
      }
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BizDocFile> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      uw.eq("id", docId).set("remark", null);
      bizDocFileService.update(uw);
  }

  private String detectContentType(java.io.File file, String name) {
      try {
          String type = java.nio.file.Files.probeContentType(file.toPath());
          if (oConvertUtils.isNotEmpty(type)) {
              return type;
          }
      } catch (Exception ignore) {
      }
      String ext = org.apache.commons.io.FilenameUtils.getExtension(name).toLowerCase();
      if ("md".equals(ext)) return "text/markdown;charset=UTF-8";
      if ("svg".equals(ext)) return "image/svg+xml";
      if ("png".equals(ext)) return "image/png";
      if ("jpg".equals(ext) || "jpeg".equals(ext)) return "image/jpeg";
      if ("gif".equals(ext)) return "image/gif";
      if ("webp".equals(ext)) return "image/webp";
      if ("bmp".equals(ext)) return "image/bmp";
      return "application/octet-stream";
  }

  private java.io.File findMainMarkdown(java.nio.file.Path root, String mainFile) throws Exception {
      java.util.List<java.nio.file.Path> markdownFiles = new java.util.ArrayList<>();
      try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(root)) {
          stream.filter(java.nio.file.Files::isRegularFile)
              .filter(path -> "md".equalsIgnoreCase(org.apache.commons.io.FilenameUtils.getExtension(path.getFileName().toString())))
              .forEach(markdownFiles::add);
      }
      if (markdownFiles.isEmpty()) {
          return null;
      }
      java.nio.file.Path normalizedRoot = root.toAbsolutePath().normalize();
      if (oConvertUtils.isNotEmpty(mainFile)) {
          String normalizedMain = normalizePackageRelativePath(mainFile);
          for (java.nio.file.Path path : markdownFiles) {
              String rel = normalizedRoot.relativize(path.toAbsolutePath().normalize()).toString().replace("\\", "/");
              if (rel.equals(normalizedMain)) {
                  return path.toFile();
              }
          }
          throw new IllegalArgumentException("指定的主Markdown不存在: " + mainFile);
      }
      markdownFiles.sort(java.util.Comparator
          .comparingInt((java.nio.file.Path path) -> normalizedRoot.relativize(path.toAbsolutePath().normalize()).getNameCount())
          .thenComparing(path -> path.getFileName().toString()));
      return markdownFiles.get(0).toFile();
  }

  private void unzipPackage(java.nio.file.Path zipFilePath, java.nio.file.Path targetDir) throws Exception {
      long totalUnzippedSize = 0;
      int entryCount = 0;
      java.nio.file.Files.createDirectories(targetDir);
      try (org.apache.commons.compress.archivers.zip.ZipFile zipFile = new org.apache.commons.compress.archivers.zip.ZipFile(zipFilePath.toFile())) {
          java.util.Enumeration<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> entries = zipFile.getEntries();
          while (entries.hasMoreElements()) {
              org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry = entries.nextElement();
              entryCount++;
              if (entryCount > ZIP_MAX_ENTRY_COUNT) {
                  throw new java.io.IOException("解压文件数量超限，可能是zip bomb攻击");
              }
              if (shouldSkipZipEntry(entry.getName())) {
                  continue;
              }
              java.nio.file.Path newPath = safeResolveZipEntry(targetDir, entry.getName());
              if (entry.isDirectory()) {
                  java.nio.file.Files.createDirectories(newPath);
                  continue;
              }
              java.nio.file.Files.createDirectories(newPath.getParent());
              try (java.io.InputStream in = zipFile.getInputStream(entry);
                   java.io.OutputStream out = java.nio.file.Files.newOutputStream(newPath)) {
                  long bytesCopied = copyLimited(in, out, ZIP_MAX_FILE_SIZE);
                  totalUnzippedSize += bytesCopied;
                  if (totalUnzippedSize > ZIP_MAX_TOTAL_SIZE) {
                      throw new java.io.IOException("解压总大小超限，可能是zip bomb攻击");
                  }
              }
          }
      }
  }

  private boolean shouldSkipZipEntry(String entryName) {
      if (oConvertUtils.isEmpty(entryName)) {
          return true;
      }
      String normalizedName = entryName.replace("\\", "/");
      if (normalizedName.startsWith("__MACOSX/")) {
          return true;
      }
      String fileName = java.nio.file.Paths.get(normalizedName).getFileName().toString();
      return fileName.startsWith("._") || fileName.equals(".DS_Store");
  }

  private java.nio.file.Path safeResolveZipEntry(java.nio.file.Path targetDir, String entryName) throws java.io.IOException {
      java.nio.file.Path resolvedPath = targetDir.resolve(entryName).normalize();
      if (!resolvedPath.startsWith(targetDir)) {
          throw new java.io.IOException("ZIP 路径穿越攻击被阻止:" + entryName);
      }
      return resolvedPath;
  }

  private long copyLimited(java.io.InputStream in, java.io.OutputStream out, long maxBytes) throws java.io.IOException {
      byte[] buffer = new byte[8192];
      long totalCopied = 0;
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
          totalCopied += bytesRead;
          if (totalCopied > maxBytes) {
              throw new java.io.IOException("单个文件解压超限，可能是zip bomb攻击");
          }
          out.write(buffer, 0, bytesRead);
      }
      return totalCopied;
  }

  private org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> minioResourceResponse(BizDocFile doc, boolean attachment) {
      String objectName = resolveMinioObjectName(doc);
      java.io.InputStream in = getMinioObjectStream(objectName);
      if (in == null) {
          return org.springframework.http.ResponseEntity.notFound().build();
      }
      org.springframework.core.io.Resource res = new org.springframework.core.io.InputStreamResource(in);
      String fn = (doc.getDisplayName()!=null && !doc.getDisplayName().isEmpty()) ? doc.getDisplayName() : (doc.getActualFileName()==null?"file":doc.getActualFileName());
      if (!fn.contains(".") && doc.getFileType()!=null && !doc.getFileType().isEmpty()) {
          fn = fn + "." + doc.getFileType();
      }
      org.springframework.http.ContentDisposition cd = (attachment
          ? org.springframework.http.ContentDisposition.attachment()
          : org.springframework.http.ContentDisposition.inline())
          .filename(fn, java.nio.charset.StandardCharsets.UTF_8)
          .build();
      org.springframework.http.MediaType mt;
      try { mt = org.springframework.http.MediaType.parseMediaType(doc.getContentType()==null?"application/octet-stream":doc.getContentType()); }
      catch (Exception ignore) { mt = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM; }
      return org.springframework.http.ResponseEntity.ok()
          .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
          .contentType(mt)
          .body(res);
  }

  private org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> minioPreviewResponse(BizDocFile doc) {
      String ft = doc.getFileType()==null?"":doc.getFileType().toLowerCase();
      boolean office = java.util.Arrays.asList("doc","docx","ppt","pptx","xls","xlsx").contains(ft);
      if (!office) {
          return minioResourceResponse(doc, false);
      }
      java.io.File workDir = null;
      try {
          workDir = java.nio.file.Files.createTempDirectory("ai5g-doc-preview-").toFile();
          String objectName = resolveMinioObjectName(doc);
          String srcName = objectName == null ? "source." + ft : objectName.substring(objectName.lastIndexOf('/') + 1);
          java.io.File src = new java.io.File(workDir, srcName);
          if (!downloadMinioObject(objectName, src)) {
              log.error("MinIO预览源文件下载失败: {}", objectName);
              return org.springframework.http.ResponseEntity.notFound().build();
          }
          java.io.File pdfFile = new java.io.File(workDir, src.getName().replaceAll("\\.[^./\\\\]+$", "") + ".pdf");
          boolean converted = trySofficeCliConvert(src, pdfFile, "pdf");
          log.info("MinIO preview Office to PDF result: {}, source={}, pdf={}", converted, src.getAbsolutePath(), pdfFile.getAbsolutePath());
          if (!converted || !pdfFile.exists()) {
              return minioResourceResponse(doc, false);
          }
          byte[] data = java.nio.file.Files.readAllBytes(pdfFile.toPath());
          org.springframework.core.io.Resource res = new org.springframework.core.io.ByteArrayResource(data);
          String fn = (doc.getDisplayName()!=null && !doc.getDisplayName().isEmpty()) ? doc.getDisplayName() : (doc.getActualFileName()==null?"file":doc.getActualFileName());
          if (!fn.toLowerCase().endsWith(".pdf")) {
              fn = fn.replaceAll("\\.[^./\\\\]+$", "") + ".pdf";
          }
          org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition.inline().filename(fn, java.nio.charset.StandardCharsets.UTF_8).build();
          return org.springframework.http.ResponseEntity.ok()
              .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
              .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
              .contentLength(data.length)
              .body(res);
      } catch (Exception e) {
          log.error("MinIO Office preview error", e);
          return org.springframework.http.ResponseEntity.internalServerError().build();
      } finally {
          if (workDir != null && workDir.exists()) {
              try {
                  org.apache.commons.io.FileUtils.deleteDirectory(workDir);
              } catch (Exception e) {
                  log.warn("清理预览临时目录失败: {}", workDir.getAbsolutePath(), e);
              }
          }
      }
  }

  @PostMapping("/save-md")
  public Result<?> saveMd(@RequestParam("id") String id, @RequestParam("content") String content) {
      BizDocFile doc = bizDocFileService.getById(id);
      if (doc == null || !Boolean.TRUE.equals(doc.getMdConverted()) || doc.getMdPath() == null) {
          return Result.error("文档未找到或未转换MD");
      }
      if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
          String mdObjectName = getMinioObjectName(doc.getMdPath());
          byte[] data = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          try (java.io.InputStream in = new java.io.ByteArrayInputStream(data)) {
              doc.setMdPath(uploadMinioObject(in, data.length, "text/markdown;charset=UTF-8", mdObjectName));
              bizDocFileService.updateById(doc);
              return Result.OK("保存成功");
          } catch (Exception e) {
              log.error("Save MinIO MD error", e);
              return Result.error("保存失败: " + e.getMessage());
          }
      }
      if (!CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
          return Result.error("当前上传模式非本地，暂不支持修改");
      }
      java.io.File file = new java.io.File(uploadpath + java.io.File.separator + doc.getMdPath());
      try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
          bw.write(content);
          return Result.OK("保存成功");
      } catch (Exception e) {
          log.error("Save MD error", e);
          return Result.error("保存失败: " + e.getMessage());
      }
  }

  @GetMapping("/preview-md/{id}")
  public void previewMarkdown(@PathVariable("id") String id,
                              jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response) {
      BizDocFile doc = bizDocFileService.getById(id);
      if (doc == null || !Boolean.TRUE.equals(doc.getMdConverted()) || doc.getMdPath() == null) {
        response.setStatus(404);
        return;
      }
	      if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
	          String mdObjectName = getMinioObjectName(doc.getMdPath());
	          try (java.io.InputStream autoCloseIn = getMinioObjectStream(mdObjectName);
	               java.io.OutputStream out = response.getOutputStream()) {
	              if (autoCloseIn == null) {
	                  response.setStatus(404);
	                  return;
              }
              response.setContentType("text/markdown;charset=UTF-8");
              String content = org.apache.commons.io.IOUtils.toString(autoCloseIn, java.nio.charset.StandardCharsets.UTF_8);
              content = rewriteMarkdownAssetUrls(content, doc, buildRequestBaseUrl(request));
              out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
              out.flush();
          } catch (Exception e) {
              log.error("Preview MinIO MD error", e);
	              response.setStatus(500);
	          }
	          return;
      }
      java.io.File file = new java.io.File(uploadpath + java.io.File.separator + doc.getMdPath());
      if (!file.exists()) {
          response.setStatus(404);
          return;
      }
      try (java.io.InputStream in = new java.io.FileInputStream(file);
           java.io.OutputStream out = response.getOutputStream()) {
          response.setContentType("text/markdown;charset=UTF-8");
          byte[] buffer = new byte[1024];
          int len;
          while ((len = in.read(buffer)) != -1) {
              out.write(buffer, 0, len);
          }
          out.flush();
	      } catch (Exception e) {
	          log.error("Preview MD error", e);
          response.setStatus(500);
      }
  }

  @IgnoreAuth
  @GetMapping("/assets/{id}/**")
  public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> previewAsset(@PathVariable("id") String id,
                                                                                                  jakarta.servlet.http.HttpServletRequest request) {
      BizDocFile doc = bizDocFileService.getById(id);
      if (doc == null || oConvertUtils.isEmpty(doc.getAssetRoot())) {
          return org.springframework.http.ResponseEntity.notFound().build();
      }
      String prefix = "/ai5g/doc/assets/" + id + "/";
      String uri = request.getRequestURI();
      int index = uri.indexOf(prefix);
      if (index < 0) {
          return org.springframework.http.ResponseEntity.notFound().build();
      }
      try {
          String relativePath = java.net.URLDecoder.decode(uri.substring(index + prefix.length()), java.nio.charset.StandardCharsets.UTF_8);
          relativePath = normalizePackageRelativePath(relativePath);
          if (oConvertUtils.isEmpty(relativePath)) {
              return org.springframework.http.ResponseEntity.notFound().build();
          }
          String objectName = normalizeObjectName(doc.getAssetRoot() + relativePath);
          java.io.InputStream in = getMinioObjectStream(objectName);
          if (in == null) {
              return org.springframework.http.ResponseEntity.notFound().build();
          }
          String contentType = detectContentType(new java.io.File(relativePath), relativePath);
          org.springframework.core.io.Resource res = new org.springframework.core.io.InputStreamResource(in);
          return org.springframework.http.ResponseEntity.ok()
              .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
              .body(res);
      } catch (Exception e) {
          log.error("Preview package asset error, docId={}", id, e);
          return org.springframework.http.ResponseEntity.internalServerError().build();
      }
  }

  @DeleteMapping("/remove/{id}")
  public Result<?> remove(@PathVariable("id") String id) {
    BizDocFile doc = bizDocFileService.getById(id);
    if (doc == null) return Result.error("未找到文档");
    boolean ok = bizDocFileService.removeById(id);
    if (ok) {
      recomputeLatest(doc.getOriginalName(), doc.getCategoryPath());
      String tombstoneId = null;
      try {
        tombstoneId = insertDocTombstone(doc);
      } catch (Exception e) {
        log.warn("AI5G 删除后写入清理标记失败, docId={}", doc.getId(), e);
      }
      if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        try {
          cleanupDocMinio(doc);
          if (tombstoneId != null) {
            markTombstoneStatus(tombstoneId, "cleaned", null);
          }
        } catch (Exception e) {
          log.error("文档删除后清理MinIO失败, docId={}, tombstoneId={}", doc.getId(), tombstoneId, e);
          if (tombstoneId != null) {
            markTombstoneStatus(tombstoneId, "failed", truncateText(e.getMessage(), 1000));
          }
        }
      } else {
        if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
          try {
            java.io.File src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
            if (src.exists()) src.delete();
          } catch (Exception ignore) {
          }
        }
        if (tombstoneId != null) {
          markTombstoneStatus(tombstoneId, "cleaned", null);
        }
      }
    }
    return ok ? Result.OK(true) : Result.error("删除失败");
  }

  @GetMapping("/download/{id}")
  public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable("id") String id) {
    BizDocFile doc = bizDocFileService.getById(id);
    if (doc == null) return org.springframework.http.ResponseEntity.notFound().build();
    try {
      java.io.File src;
      if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
        src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
      } else if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        return minioResourceResponse(doc, true);
      } else {
        String location = oConvertUtils.isNotEmpty(doc.getStoragePath()) ? doc.getStoragePath() : doc.getStorageFilename();
        return org.springframework.http.ResponseEntity.status(302).header("Location", java.net.URI.create(location).toASCIIString()).build();
      }
      if (!src.exists()) return org.springframework.http.ResponseEntity.notFound().build();
      org.springframework.core.io.Resource res = new org.springframework.core.io.FileSystemResource(src);
      String fn = (doc.getDisplayName()!=null && !doc.getDisplayName().isEmpty()) ? doc.getDisplayName() : (doc.getActualFileName()==null?"file":doc.getActualFileName());
      if (!fn.contains(".") && doc.getFileType()!=null && !doc.getFileType().isEmpty()) {
        fn = fn + "." + doc.getFileType();
      }
      java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
      org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition.attachment().filename(fn, utf8).build();
      org.springframework.http.MediaType mt;
      try { mt = org.springframework.http.MediaType.parseMediaType(doc.getContentType()==null?"application/octet-stream":doc.getContentType()); }
      catch (Exception ignore) { mt = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM; }
      return org.springframework.http.ResponseEntity.ok()
          .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
          .contentType(mt)
          .body(res);
    } catch (Exception e) {
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  @GetMapping("/preview/{id}")
  public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable("id") String id) {
    log.info("Preview request for id: {}", id);
    BizDocFile doc = bizDocFileService.getById(id);
    if (doc == null) {
      log.error("Document not found for id: {}", id);
      return org.springframework.http.ResponseEntity.notFound().build();
    }
    try {
      java.io.File src;
      if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
        src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
        log.info("Source file path: {}, exists: {}", src.getAbsolutePath(), src.exists());
        
        String ft = doc.getFileType()==null?"":doc.getFileType().toLowerCase();
        boolean office = java.util.Arrays.asList("doc","docx","ppt","pptx","xls","xlsx").contains(ft);
        if (office) {
          String pdfRel = doc.getStoragePath().replaceAll("\\.[^./\\\\]+$", "") + ".pdf";
          java.io.File pdfFile = new java.io.File(uploadpath + java.io.File.separator + pdfRel);
          log.info("PDF file path: {}, exists: {}", pdfFile.getAbsolutePath(), pdfFile.exists());
          
          if (!pdfFile.exists() && src.exists()) {
            try {
              pdfFile.getParentFile().mkdirs();
              log.info("Attempting conversion...");
              boolean conv = trySofficeCliConvert(src, pdfFile);
              log.info("Conversion result: {}", conv);
            } catch (Exception e) {
              log.error("Conversion exception", e);
            }
          }
          if (pdfFile.exists()) {
            log.info("Returning PDF: {}", pdfFile.getAbsolutePath());
            org.springframework.core.io.Resource res = new org.springframework.core.io.FileSystemResource(pdfFile);
            String fn = (doc.getDisplayName()!=null && !doc.getDisplayName().isEmpty()) ? doc.getDisplayName() : (doc.getActualFileName()==null?"file":doc.getActualFileName());
            if (!fn.toLowerCase().endsWith(".pdf")) fn = fn + ".pdf";
            org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition.inline().filename(fn, java.nio.charset.StandardCharsets.UTF_8).build();
            return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(res);
          }
        }
      } else if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        return minioPreviewResponse(doc);
      } else {
        String location = oConvertUtils.isNotEmpty(doc.getStoragePath()) ? doc.getStoragePath() : doc.getStorageFilename();
        return org.springframework.http.ResponseEntity.status(302).header("Location", java.net.URI.create(location).toASCIIString()).build();
      }
      if (!src.exists()) {
         log.error("Source file not found after fallback logic");
         return org.springframework.http.ResponseEntity.notFound().build();
      }
      log.info("Returning original file");

      org.springframework.core.io.Resource res = new org.springframework.core.io.FileSystemResource(src);
      String fn = (doc.getDisplayName()!=null && !doc.getDisplayName().isEmpty()) ? doc.getDisplayName() : (doc.getActualFileName()==null?"file":doc.getActualFileName());
      if (!fn.contains(".") && doc.getFileType()!=null && !doc.getFileType().isEmpty()) {
        fn = fn + "." + doc.getFileType();
      }
      java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
      org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition.inline().filename(fn, utf8).build();
      org.springframework.http.MediaType mt;
      try { mt = org.springframework.http.MediaType.parseMediaType(doc.getContentType()==null?"application/octet-stream":doc.getContentType()); }
      catch (Exception ignore) { mt = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM; }
      return org.springframework.http.ResponseEntity.ok()
          .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
          .contentType(mt)
          .body(res);
    } catch (Exception e) {
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  private boolean trySofficeCliConvert(java.io.File src, java.io.File targetFile) throws Exception {
    String format = "pdf";
    if (targetFile.getName().toLowerCase().endsWith(".docx")) format = "docx";
    // ... other formats
    return trySofficeCliConvert(src, targetFile, format);
  }

  private boolean trySofficeCliConvert(java.io.File src, java.io.File targetFile, String format) throws Exception {
    String soffice = "/Applications/LibreOffice.app/Contents/MacOS/soffice";
    if (!new java.io.File(soffice).exists()) soffice = "soffice";
    
    // 为了避免 macOS 下的沙盒权限问题，将文件复制到系统临时目录进行转换
    String tmpDir = System.getProperty("java.io.tmpdir");
    String safeBaseName = "soffice_convert_" + System.currentTimeMillis();
    java.io.File tmpSrc = new java.io.File(tmpDir, safeBaseName + "_" + src.getName());
    java.io.File tmpOutDir = new java.io.File(tmpDir, safeBaseName + "_out");
    tmpOutDir.mkdirs();
    
    try {
        org.apache.commons.io.FileUtils.copyFile(src, tmpSrc);
        
        // Create command list
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(soffice);
        
        // 始终指定临时的 UserInstallation 目录，解决并发和权限问题 (macOS/Linux 均适用)
        String userInstallDir = tmpDir + java.io.File.separator + "LO_User_" + System.currentTimeMillis();
        command.add("-env:UserInstallation=file://" + userInstallDir);
        
        command.add("--headless");
        command.add("--invisible");
        command.add("--nologo");
        command.add("--nodefault");
        command.add("--nofirststartwizard");
        command.add("--nolockcheck");
        command.add("--convert-to");
        command.add(format);
        command.add(tmpSrc.getAbsolutePath());
        command.add("--outdir");
        command.add(tmpOutDir.getAbsolutePath());

        java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(command);
        pb.redirectErrorStream(true);
        log.info("Executing CLI command (in tmp): {}", String.join(" ", pb.command()));
        
        java.lang.Process p = pb.start();
        
        // Read output
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("CLI Output: {}", line);
            }
        }
        
        boolean finished = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
          log.error("CLI command timed out");
          try { p.destroyForcibly(); } catch (Exception ignore) {}
          return false;
        }
        int code = p.exitValue();
        log.info("CLI exit code: {}", code);
        
        if (code == 0) {
            // 查找生成的文件
            String baseName = src.getName().substring(0, src.getName().lastIndexOf('.'));
            // LibreOffice 可能会处理文件名中的特殊字符，所以我们遍历目录找文件
            java.io.File[] convertedFiles = tmpOutDir.listFiles((dir, name) -> name.endsWith("." + format));
            if (convertedFiles != null && convertedFiles.length > 0) {
                org.apache.commons.io.FileUtils.copyFile(convertedFiles[0], targetFile);
                return true;
            }
        }
        return false;
    } finally {
        // 清理临时文件
        if (tmpSrc.exists()) tmpSrc.delete();
        if (tmpOutDir.exists()) org.apache.commons.io.FileUtils.deleteDirectory(tmpOutDir);
    }
  }

  private Long countSql(String sql) {
    return jdbcTemplate.queryForObject(sql, Long.class);
  }

  private String insertDocTombstone(BizDocFile doc) {
    String id = "ai5g-del-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    jdbcTemplate.update(
        "INSERT INTO biz_ai5g_docfile_tombstone " +
        "(id, doc_id, display_name, original_name, source_object, md_object, asset_root, source_package_object, status, create_time) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', NOW())",
        id,
        doc.getId(),
        doc.getDisplayName(),
        doc.getOriginalName(),
        resolveMinioObjectName(doc),
        getMinioObjectName(doc.getMdPath()),
        normalizeObjectName(doc.getAssetRoot()),
        getMinioObjectName(doc.getSourcePackagePath())
    );
    return id;
  }

  private void markTombstoneStatus(String id, String status, String error) {
    jdbcTemplate.update(
        "UPDATE biz_ai5g_docfile_tombstone SET status = ?, error_msg = ?, " +
        "cleaned_at = IF(? = 'cleaned', NOW(), cleaned_at), update_time = NOW() WHERE id = ?",
        status,
        error,
        status,
        id
    );
  }

  private void cleanupDocMinio(BizDocFile doc) throws Exception {
    cleanupTombstoneObjects(
        resolveMinioObjectName(doc),
        getMinioObjectName(doc.getMdPath()),
        normalizeObjectName(doc.getAssetRoot()),
        getMinioObjectName(doc.getSourcePackagePath())
    );
  }

  private void cleanupTombstoneObjects(String sourceObject, String mdObject, String assetRoot, String sourcePackageObject) throws Exception {
    io.minio.MinioClient client = buildMinioClient();
    removeMinioObjectQuietly(client, sourceObject);
    removeMinioObjectQuietly(client, mdObject);
    removeMinioObjectQuietly(client, sourcePackageObject);
    if (oConvertUtils.isNotEmpty(assetRoot)) {
      removeMinioPrefixQuietly(client, assetRoot);
    }
  }

  private void removeMinioObjectQuietly(io.minio.MinioClient client, String objectName) throws Exception {
    if (oConvertUtils.isEmpty(objectName)) {
      return;
    }
    try {
      client.statObject(io.minio.StatObjectArgs.builder().bucket(minioBucketName).object(objectName).build());
    } catch (Exception e) {
      return;
    }
    client.removeObject(io.minio.RemoveObjectArgs.builder().bucket(minioBucketName).object(objectName).build());
  }

  private void removeMinioPrefixQuietly(io.minio.MinioClient client, String prefix) throws Exception {
    for (io.minio.Result<io.minio.messages.Item> item : client.listObjects(io.minio.ListObjectsArgs.builder()
        .bucket(minioBucketName)
        .prefix(prefix)
        .recursive(true)
        .build())) {
      client.removeObject(io.minio.RemoveObjectArgs.builder().bucket(minioBucketName).object(item.get().objectName()).build());
    }
  }

  private void retryPendingTombstones() {
    try {
      java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT id, source_object, md_object, asset_root, source_package_object " +
          "FROM biz_ai5g_docfile_tombstone WHERE status IN ('pending', 'failed') LIMIT 100");
      for (java.util.Map<String, Object> row : rows) {
        String id = row.get("id") == null ? null : row.get("id").toString();
        try {
          cleanupTombstoneObjects(
              row.get("source_object") == null ? null : row.get("source_object").toString(),
              row.get("md_object") == null ? null : row.get("md_object").toString(),
              row.get("asset_root") == null ? null : row.get("asset_root").toString(),
              row.get("source_package_object") == null ? null : row.get("source_package_object").toString()
          );
          markTombstoneStatus(id, "cleaned", null);
        } catch (Exception e) {
          log.warn("AI5G 删除清理重试失败, tombstoneId={}", id, e);
          markTombstoneStatus(id, "failed", truncateText(e.getMessage(), 1000));
        }
      }
    } catch (Exception e) {
      log.warn("AI5G 删除清理重试查询失败", e);
    }
  }

  @PutMapping("/update")
  public Result<?> update(@RequestBody BizDocFile body) {
    if (body.getId() == null) return Result.error("id 不能为空");
    BizDocFile origin = bizDocFileService.getById(body.getId());
    if (origin == null) return Result.error("未找到文档");
    if (body.getLatest() != null) {
      origin.setLatest(body.getLatest());
    }
    origin.setDisplayName(body.getDisplayName());
    origin.setRemark(body.getRemark());
    origin.setFileYear(body.getFileYear());
    origin.setProcessStatus(body.getProcessStatus());
    bizDocFileService.updateById(origin);
    if (Boolean.TRUE.equals(body.getLatest())) {
      markOldVersionsNotLatest(origin.getOriginalName(), origin.getCategoryPath());
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BizDocFile> latestUw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      latestUw.eq("id", origin.getId()).set("latest", true);
      bizDocFileService.update(latestUw);
    }
    return Result.OK(origin);
  }

}
