package org.jeecg.modules.biz.ai5g.controller;

import lombok.extern.slf4j.Slf4j;
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
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.biz.ai5g.entity.BizDocFile;
import org.jeecg.modules.biz.ai5g.service.IBizDocFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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

  @Autowired
  private IBizDocFileService bizDocFileService;
  @Autowired
  private org.jeecg.modules.biz.ai5g.service.IBizDocTypeService bizDocTypeService;
  @Autowired
  private org.springframework.core.env.Environment environment;

  private static final Set<String> ALLOW_EXT = new HashSet<>(Arrays.asList("pdf","doc","docx","xlsx","xls","csv"));
  private static final Set<String> PACKAGE_ASSET_EXT = new HashSet<>(Arrays.asList("png","jpg","jpeg","gif","bmp","webp","svg"));
  private static final long ZIP_MAX_FILE_SIZE = 150L * 1024 * 1024;
  private static final long ZIP_MAX_TOTAL_SIZE = 1024L * 1024 * 1024;
  private static final int ZIP_MAX_ENTRY_COUNT = 10000;
  private static final long MINIO_PART_SIZE = 10L * 1024 * 1024;
  private static final java.util.regex.Pattern MD_IMAGE_PATTERN = java.util.regex.Pattern.compile("!\\[(.*?)]\\((.*?)\\)");
  private static final String DOMAIN_URL_PLACEHOLDER = "#{domainURL}";

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

      String seg = typeCode3 != null && typeCode3.length() == 6 ? (typeCode3.substring(0,2) + "/" + typeCode3.substring(2,4) + "/" + typeCode3.substring(4,6)) : typeCode3;
      String bizPath = baseDir + "/" + seg;
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
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BizDocFile> vq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
      vq.select("id","version");
      vq.eq("original_name", orgName).orderByDesc("version");
      java.util.List<BizDocFile> vers = bizDocFileService.list(vq);
      BizDocFile last = vers != null && !vers.isEmpty() ? vers.get(0) : null;
      int version = last == null ? 1 : (last.getVersion() == null ? 1 : last.getVersion() + 1);
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BizDocFile> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      uw.eq("original_name", orgName).set("latest", false);
      bizDocFileService.update(uw);

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
      String assetRoot = normalizeObjectName(baseDir + "/" + seg + "/packages/" + packageId + "/");
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

      rewriteAndUploadMarkdown(mainMd, doc, mainObjectName[0]);
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
    
    // 异步执行转换任务
    new Thread(() -> {
        java.io.File workDir = null;
        try {
            // 更新状态为处理中
            doc.setProcessStatus("processing");
            bizDocFileService.updateById(doc);
            
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
                if (isOffice || "pdf".equals(ft)) {
                   if (org.jeecg.common.util.oConvertUtils.isNotEmpty(mineruUrl)) {
                        log.info("使用 MinerU 远程服务解析: {}", mineruUrl);
                  com.alibaba.fastjson.JSONObject mineruRes = MineruClientUtil.parsePdf(mineruUrl, mineruInputFile);
                  if (mineruRes != null && org.jeecg.common.util.oConvertUtils.isNotEmpty(mineruRes.getString("content"))) {
                      try {
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
                        rewriteAndUploadMarkdown(mdFile, doc, mdObjectName);
                    } else {
                        try (java.io.InputStream in = new java.io.FileInputStream(mdFile)) {
                            doc.setMdPath(uploadMinioObject(in, mdFile.length(), "text/markdown;charset=UTF-8", mdRel));
                        }
                    }
                } else {
                    doc.setMdPath(mdRel);
                }
                doc.setMdConverted(true);
                doc.setProcessStatus("success");
                bizDocFileService.updateById(doc);
            } else {
                doc.setProcessStatus("failed");
                doc.setRemark("转换失败");
                bizDocFileService.updateById(doc);
            }
        } catch (Exception e) {
            log.error("convert to md error", e);
            doc.setProcessStatus("failed");
            doc.setRemark("转换异常: " + e.getMessage());
            bizDocFileService.updateById(doc);
        } finally {
            if (workDir != null && workDir.exists()) {
                try {
                    org.apache.commons.io.FileUtils.deleteDirectory(workDir);
                } catch (Exception e) {
                    log.warn("清理转换临时目录失败: {}", workDir.getAbsolutePath(), e);
                }
            }
        }
    }).start();

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
              .stream(in, -1, MINIO_PART_SIZE)
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
          .stream(in, size, -1)
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

  private String buildMinioUrl(String objectName) {
      String baseUrl = minioUrl.endsWith("/") ? minioUrl : minioUrl + "/";
      return baseUrl + minioBucketName + "/" + normalizeObjectName(objectName);
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
      vq.eq("original_name", originalName).eq("category_path", categoryPath).orderByDesc("version");
      java.util.List<BizDocFile> vers = bizDocFileService.list(vq);
      BizDocFile last = vers != null && !vers.isEmpty() ? vers.get(0) : null;
      return last == null ? 1 : (last.getVersion() == null ? 1 : last.getVersion() + 1);
  }

  private void markOldVersionsNotLatest(String originalName, String categoryPath) {
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BizDocFile> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      uw.eq("original_name", originalName).eq("category_path", categoryPath).set("latest", false);
      bizDocFileService.update(uw);
  }

  private void rewriteAndUploadMarkdown(java.io.File mainMd, BizDocFile doc, String objectName) throws Exception {
      String content = org.apache.commons.io.FileUtils.readFileToString(mainMd, java.nio.charset.StandardCharsets.UTF_8);
      content = rewriteMarkdownAssetUrls(content, doc);
      byte[] data = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      try (java.io.InputStream in = new java.io.ByteArrayInputStream(data)) {
          uploadMinioObject(in, data.length, "text/markdown;charset=UTF-8", objectName);
      }
      doc.setSize((long) data.length);
      bizDocFileService.updateById(doc);
  }

  private com.alibaba.fastjson.JSONObject uploadConvertedMarkdownPackage(java.nio.file.Path packageRoot, java.io.File mainMd, BizDocFile doc) throws Exception {
      java.nio.file.Path rootPath = packageRoot.toAbsolutePath().normalize();
      java.nio.file.Path mainPath = mainMd.toPath().toAbsolutePath().normalize();
      if (!mainPath.startsWith(rootPath)) {
          throw new IllegalArgumentException("转换结果主Markdown不在资源目录内");
      }
      String categoryPath = oConvertUtils.isNotEmpty(doc.getCategoryPath()) ? doc.getCategoryPath() : "uncategorized";
      String packageId = java.util.UUID.randomUUID().toString().replace("-", "");
      String assetRoot = normalizeObjectName(baseDir + "/" + categoryPath + "/packages/" + packageId + "/");
      java.util.List<com.alibaba.fastjson.JSONObject> manifestItems = new java.util.ArrayList<>();
      final String[] mainObjectName = new String[1];

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
          throw new IllegalStateException("转换结果主Markdown上传失败");
      }

      com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject();
      result.put("assetRoot", assetRoot);
      result.put("assetManifest", com.alibaba.fastjson.JSON.toJSONString(manifestItems));
      result.put("mainObjectName", mainObjectName[0]);
      return result;
  }

  private String rewriteMarkdownAssetUrls(String content, BizDocFile doc) {
      return rewriteMarkdownAssetUrls(content, doc, DOMAIN_URL_PLACEHOLDER);
  }

  private String rewriteMarkdownAssetUrls(String content, BizDocFile doc, String assetUrlBase) {
      if (oConvertUtils.isEmpty(content) || doc == null || oConvertUtils.isEmpty(doc.getAssetRoot()) || oConvertUtils.isEmpty(doc.getId())) {
          return content;
      }
      String urlBase = oConvertUtils.isEmpty(assetUrlBase) ? DOMAIN_URL_PLACEHOLDER : assetUrlBase;
      if (urlBase.endsWith("/")) {
          urlBase = urlBase.substring(0, urlBase.length() - 1);
      }
      java.util.regex.Matcher matcher = MD_IMAGE_PATTERN.matcher(content);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
          String alt = matcher.group(1);
          String imageUrl = matcher.group(2);
          String replacementUrl = imageUrl;
          if (oConvertUtils.isNotEmpty(imageUrl)
              && !imageUrl.startsWith("http://")
              && !imageUrl.startsWith("https://")
              && !imageUrl.startsWith("data:")
              && !imageUrl.startsWith(DOMAIN_URL_PLACEHOLDER + "/ai5g/doc/assets/")) {
              String assetPrefix = "/ai5g/doc/assets/" + doc.getId() + "/";
              if (imageUrl.startsWith(assetPrefix)) {
                  replacementUrl = urlBase + imageUrl;
              } else {
                  replacementUrl = urlBase + assetPrefix + normalizePackageRelativePath(imageUrl);
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
      String uri = request.getRequestURI();
      String contextPath = request.getContextPath();
      String path = oConvertUtils.isEmpty(contextPath) ? "" : contextPath;
      String prefix = uri;
      int apiIndex = uri.indexOf("/ai5g/");
      if (apiIndex >= 0) {
          prefix = uri.substring(0, apiIndex);
      }
      if (oConvertUtils.isNotEmpty(prefix)) {
          path = prefix;
      }
      StringBuilder base = new StringBuilder();
      base.append(request.getScheme()).append("://").append(request.getServerName());
      int port = request.getServerPort();
      if (port > 0 && port != 80 && port != 443) {
          base.append(":").append(port);
      }
      base.append(path);
      return base.toString();
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
      String objectName = getMinioObjectName(oConvertUtils.isNotEmpty(doc.getStorageFilename()) ? doc.getStorageFilename() : doc.getStoragePath());
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
          String objectName = getMinioObjectName(oConvertUtils.isNotEmpty(doc.getStorageFilename()) ? doc.getStorageFilename() : doc.getStoragePath());
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
	          try (java.io.InputStream in = getMinioObjectStream(mdObjectName);
	               java.io.OutputStream out = response.getOutputStream()) {
	              if (in == null) {
	                  response.setStatus(404);
	                  return;
              }
              response.setContentType("text/markdown;charset=UTF-8");
              String content = org.apache.commons.io.IOUtils.toString(in, java.nio.charset.StandardCharsets.UTF_8);
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
    if (ok && CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
      try {
        java.io.File src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
        if (src.exists()) src.delete();
      } catch (Exception ignore) {}
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

  @PutMapping("/update")
  public Result<?> update(@RequestBody BizDocFile body) {
    if (body.getId() == null) return Result.error("id 不能为空");
    BizDocFile origin = bizDocFileService.getById(body.getId());
    if (origin == null) return Result.error("未找到文档");
    origin.setDisplayName(body.getDisplayName());
    origin.setRemark(body.getRemark());
    origin.setFileYear(body.getFileYear());
    origin.setProcessStatus(body.getProcessStatus());
    bizDocFileService.updateById(origin);
    return Result.OK(origin);
  }

}
