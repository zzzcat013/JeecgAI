package org.jeecg.modules.biz.ai5g.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.CopyObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.biz.ai5g.entity.BizDocFile;
import org.jeecg.modules.biz.ai5g.service.IBizDocFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai5g/doc/admin/storage-migration")
public class Ai5gStorageMigrationController {

  @Value("${jeecg.uploadType}")
  private String uploadType;

  @Value("${jeecg.path.upload}")
  private String uploadpath;

  @Value("${jeecg.ai5g.baseDir:doc}")
  private String baseDir;

  @Value("${jeecg.minio.minio_url:}")
  private String minioUrl;

  @Value("${jeecg.minio.minio_name:}")
  private String minioName;

  @Value("${jeecg.minio.minio_pass:}")
  private String minioPass;

  @Value("${jeecg.minio.bucketName:}")
  private String minioBucketName;

  @Autowired
  private IBizDocFileService bizDocFileService;

  @PostMapping("/run")
  public Result<?> run(@RequestBody(required = false) StorageMigrationReq req) {
    boolean dryRun = req == null || req.getDryRun() == null || req.getDryRun();
    int pageSize = req != null && req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 200;
    int maxRecords = req != null && req.getMaxRecords() != null && req.getMaxRecords() > 0 ? req.getMaxRecords() : Integer.MAX_VALUE;

    List<StorageMigrationPlan> sample = new ArrayList<>();
    int totalChecked = 0;
    int totalCandidates = 0;
    int sourceMoved = 0;
    int packageMoved = 0;
    int dbUpdated = 0;
    int failed = 0;
    int pageNo = 1;

    while (true) {
      Page<BizDocFile> page = new Page<>(pageNo, pageSize);
      IPage<BizDocFile> dataPage = bizDocFileService.page(
          page,
          new QueryWrapper<BizDocFile>().orderByAsc("create_time").orderByAsc("id"));
      List<BizDocFile> records = dataPage.getRecords();
      if (records == null || records.isEmpty()) {
        break;
      }
      for (BizDocFile doc : records) {
        totalChecked++;
        StorageMigrationPlan plan = buildPlan(doc);
        if (plan == null) {
          continue;
        }
        totalCandidates++;
        if (sample.size() < 50) {
          sample.add(plan);
        }
        if (dryRun) {
          continue;
        }
        try {
          boolean sourceChanged = migrateSource(doc, plan);
          boolean packageChanged = migratePackage(doc, plan);
          boolean dbChanged = applyDbUpdates(doc, plan);
          if (sourceChanged) {
            sourceMoved++;
          }
          if (packageChanged) {
            packageMoved++;
          }
          if (dbChanged || sourceChanged || packageChanged) {
            dbUpdated++;
          }
        } catch (Exception e) {
          failed++;
          log.error("AI5G storage migration failed, docId={}, storagePath={}, assetRoot={}",
              doc.getId(), doc.getStoragePath(), doc.getAssetRoot(), e);
        }
        if (!dryRun && dbUpdated + failed >= maxRecords) {
          break;
        }
      }
      if (records.size() < pageSize || (!dryRun && dbUpdated + failed >= maxRecords)) {
        break;
      }
      pageNo++;
    }

    StorageMigrationResult result = new StorageMigrationResult();
    result.setDryRun(dryRun);
    result.setTotalChecked(totalChecked);
    result.setTotalCandidates(totalCandidates);
    result.setSourceMoved(sourceMoved);
    result.setPackageMoved(packageMoved);
    result.setDbUpdated(dbUpdated);
    result.setFailed(failed);
    result.setSamples(sample);
    return Result.OK(result);
  }

  private StorageMigrationPlan buildPlan(BizDocFile doc) {
    if (doc == null) {
      return null;
    }
    boolean sourceLegacy = isLegacySourcePath(doc.getStoragePath());
    boolean packageLegacy = isLegacyPackageRoot(doc.getAssetRoot());
    boolean mdLegacy = isLegacyMdPath(doc);
    boolean sourcePackageLegacy = isLegacySourcePackagePath(doc.getSourcePackagePath());
    if (!sourceLegacy && !packageLegacy && !mdLegacy && !sourcePackageLegacy) {
      return null;
    }

    StorageMigrationPlan plan = new StorageMigrationPlan();
    plan.setId(doc.getId());
    plan.setDisplayName(doc.getDisplayName());
    plan.setCategoryPath(doc.getCategoryPath());
    plan.setSourceLegacy(sourceLegacy);
    plan.setPackageLegacy(packageLegacy);
    plan.setMdLegacy(mdLegacy);
    plan.setSourcePackageLegacy(sourcePackageLegacy);

    if (sourceLegacy) {
      plan.setOldSourcePath(doc.getStoragePath());
      plan.setNewSourcePath(buildNewSourcePath(doc));
    }
    if (packageLegacy) {
      String oldRoot = normalizeObjectName(toObjectName(doc.getAssetRoot()));
      String newRoot = rewritePackageRoot(oldRoot);
      plan.setOldAssetRoot(doc.getAssetRoot());
      plan.setNewAssetRoot(toStoredValue(doc.getAssetRoot(), newRoot));
      if (oConvertUtils.isNotEmpty(doc.getMdPath())) {
        plan.setOldMdPath(doc.getMdPath());
        plan.setNewMdPath(rewriteByRoot(doc.getMdPath(), oldRoot, newRoot));
      }
      if (oConvertUtils.isNotEmpty(doc.getSourcePackagePath())) {
        plan.setOldSourcePackagePath(doc.getSourcePackagePath());
        plan.setNewSourcePackagePath(rewriteByRoot(doc.getSourcePackagePath(), oldRoot, newRoot));
      }
      if (oConvertUtils.isNotEmpty(doc.getAssetManifest())) {
        plan.setOldAssetManifest(doc.getAssetManifest());
        plan.setNewAssetManifest(doc.getAssetManifest().replace(oldRoot, newRoot));
      }
    } else if (mdLegacy && oConvertUtils.isNotEmpty(doc.getMdPath())) {
      plan.setOldMdPath(doc.getMdPath());
      plan.setNewMdPath(buildNewMarkdownPath(doc));
    }

    if (sourcePackageLegacy) {
      plan.setOldSourcePackagePath(doc.getSourcePackagePath());
      plan.setNewSourcePackagePath(buildNewSourcePath(doc));
    }
    return plan;
  }

  private boolean applyDbUpdates(BizDocFile doc, StorageMigrationPlan plan) {
    boolean changed = false;
    if (plan.getNewSourcePath() != null) {
      doc.setStoragePath(plan.getNewSourcePath());
      doc.setStorageFilename(toObjectName(plan.getNewSourcePath()));
      changed = true;
    }
    if (plan.getNewAssetRoot() != null) {
      doc.setAssetRoot(plan.getNewAssetRoot());
      changed = true;
    }
    if (plan.getNewMdPath() != null) {
      doc.setMdPath(plan.getNewMdPath());
      changed = true;
    }
    if (plan.getNewSourcePackagePath() != null) {
      doc.setSourcePackagePath(plan.getNewSourcePackagePath());
      changed = true;
    }
    if (plan.getNewAssetManifest() != null) {
      doc.setAssetManifest(plan.getNewAssetManifest());
      changed = true;
    }
    if (changed) {
      doc.setUpdateTime(new Date());
      bizDocFileService.updateById(doc);
    }
    return changed;
  }

  private boolean migrateSource(BizDocFile doc, StorageMigrationPlan plan) throws Exception {
    if (!plan.isSourceLegacy() || oConvertUtils.isEmpty(plan.getOldSourcePath()) || oConvertUtils.isEmpty(plan.getNewSourcePath())) {
      return false;
    }
    if (isRemoteUrl(plan.getOldSourcePath())) {
      String oldObject = toObjectName(plan.getOldSourcePath());
      String newObject = toObjectName(plan.getNewSourcePath());
      if (oConvertUtils.isEmpty(oldObject) || oConvertUtils.isEmpty(newObject) || oldObject.equals(newObject)) {
        return false;
      }
      copyMinioObject(oldObject, newObject);
      removeMinioObject(oldObject);
      return true;
    }
    Path oldFile = resolveLocalPath(plan.getOldSourcePath());
    Path newFile = resolveLocalPath(plan.getNewSourcePath());
    if (oldFile == null || newFile == null || oldFile.equals(newFile)) {
      return false;
    }
    if (!Files.exists(oldFile) && Files.exists(newFile)) {
      return false;
    }
    Files.createDirectories(newFile.getParent());
    if (!Files.exists(newFile) && Files.exists(oldFile)) {
      Files.copy(oldFile, newFile);
    }
    if (Files.exists(newFile) && Files.exists(oldFile)) {
      Files.deleteIfExists(oldFile);
    }
    return true;
  }

  private boolean migratePackage(BizDocFile doc, StorageMigrationPlan plan) throws Exception {
    if (!plan.isPackageLegacy() || oConvertUtils.isEmpty(plan.getOldAssetRoot()) || oConvertUtils.isEmpty(plan.getNewAssetRoot())) {
      return false;
    }
    String oldRoot = normalizeObjectName(toObjectName(plan.getOldAssetRoot()));
    String newRoot = normalizeObjectName(toObjectName(plan.getNewAssetRoot()));
    if (oldRoot.equals(newRoot)) {
      return false;
    }
    if (isRemoteUrl(plan.getOldAssetRoot())) {
      copyMinioPrefix(oldRoot, newRoot);
      removeMinioPrefix(oldRoot);
    } else {
      copyLocalPrefix(resolveLocalPath(plan.getOldAssetRoot()), resolveLocalPath(plan.getNewAssetRoot()));
    }

    if (oConvertUtils.isNotEmpty(plan.getNewMdPath()) && isRemoteUrl(plan.getOldMdPath()) && !plan.getOldMdPath().equals(plan.getNewMdPath())) {
      // markdown and asset tree are moved together through prefix copy, no extra IO required
    }
    return true;
  }

  private void copyMinioPrefix(String oldPrefix, String newPrefix) throws Exception {
    MinioClient client = buildMinioClient();
    ensureBucket(client);
    for (io.minio.Result<io.minio.messages.Item> item : client.listObjects(ListObjectsArgs.builder().bucket(minioBucketName).prefix(oldPrefix).recursive(true).build())) {
      String objectName = item.get().objectName();
        String suffix = objectName.substring(oldPrefix.length());
        String targetObject = newPrefix + suffix;
        if (!objectExists(client, targetObject)) {
          client.copyObject(CopyObjectArgs.builder()
              .bucket(minioBucketName)
              .object(targetObject)
              .source(SourceObject.builder().bucket(minioBucketName).object(objectName).build())
              .build());
        }
    }
  }

  private void removeMinioPrefix(String prefix) throws Exception {
    MinioClient client = buildMinioClient();
    for (io.minio.Result<io.minio.messages.Item> item : client.listObjects(ListObjectsArgs.builder().bucket(minioBucketName).prefix(prefix).recursive(true).build())) {
      client.removeObject(RemoveObjectArgs.builder().bucket(minioBucketName).object(item.get().objectName()).build());
    }
  }

  private void copyLocalPrefix(Path oldRoot, Path newRoot) throws IOException {
    if (oldRoot == null || newRoot == null || oldRoot.equals(newRoot)) {
      return;
    }
    if (!Files.exists(oldRoot) && Files.exists(newRoot)) {
      return;
    }
    Files.createDirectories(newRoot);
    if (!Files.exists(oldRoot)) {
      return;
    }
    Files.walkFileTree(oldRoot, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path target = newRoot.resolve(oldRoot.relativize(dir));
        Files.createDirectories(target);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path target = newRoot.resolve(oldRoot.relativize(file));
        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
          Files.copy(file, target);
        }
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (!dir.equals(oldRoot)) {
          Files.deleteIfExists(dir);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private boolean isLegacySourcePath(String storagePath) {
    if (oConvertUtils.isEmpty(storagePath)) {
      return false;
    }
    String obj = toObjectName(storagePath);
    return oConvertUtils.isNotEmpty(obj) && obj.startsWith(baseDir + "/") && !obj.startsWith(baseDir + "/files/");
  }

  private boolean isLegacyPackageRoot(String assetRoot) {
    if (oConvertUtils.isEmpty(assetRoot)) {
      return false;
    }
    String obj = toObjectName(assetRoot);
    return oConvertUtils.isNotEmpty(obj) && obj.startsWith(baseDir + "/") && !obj.startsWith(baseDir + "/packages/");
  }

  private boolean isLegacySourcePackagePath(String sourcePackagePath) {
    if (oConvertUtils.isEmpty(sourcePackagePath)) {
      return false;
    }
    String obj = toObjectName(sourcePackagePath);
    return oConvertUtils.isNotEmpty(obj)
        && obj.startsWith(baseDir + "/")
        && !obj.startsWith(baseDir + "/packages/")
        && !obj.startsWith(baseDir + "/files/");
  }

  private boolean isLegacyMdPath(BizDocFile doc) {
    if (doc == null || oConvertUtils.isEmpty(doc.getMdPath())) {
      return false;
    }
    return isLegacyPackageRoot(doc.getAssetRoot()) || isLegacySourcePath(doc.getStoragePath());
  }

  private String buildNewSourcePath(BizDocFile doc) {
    String fileName = resolveFileName(doc);
    Date baseDate = doc.getUploadTime() != null ? doc.getUploadTime() : (doc.getCreateTime() != null ? doc.getCreateTime() : new Date());
    String month = new SimpleDateFormat("yyyyMM").format(baseDate);
    String relative = baseDir + "/files/" + month + "/" + fileName;
    if (isRemoteUrl(doc.getStoragePath())) {
      return buildMinioUrl(relative);
    }
    return relative;
  }

  private String buildNewMarkdownPath(BizDocFile doc) {
    String newSource = buildNewSourcePath(doc);
    return newSource.replaceAll("\\.[^./\\\\]+$", "") + ".md";
  }

  private String rewritePackageRoot(String oldObjectName) {
    if (oConvertUtils.isEmpty(oldObjectName)) {
      return oldObjectName;
    }
    int idx = oldObjectName.indexOf("/packages/");
    if (idx < 0) {
      return oldObjectName;
    }
    String suffix = oldObjectName.substring(idx);
    return normalizeObjectName(baseDir + suffix);
  }

  private String rewriteByRoot(String path, String oldRoot, String newRoot) {
    if (oConvertUtils.isEmpty(path) || oConvertUtils.isEmpty(oldRoot) || oConvertUtils.isEmpty(newRoot)) {
      return path;
    }
    return path.replace(oldRoot, newRoot);
  }

  private Path resolveLocalPath(String path) {
    if (oConvertUtils.isEmpty(path)) {
      return null;
    }
    String value = path;
    if (isRemoteUrl(value)) {
      value = toObjectName(value);
    }
    if (Paths.get(value).isAbsolute()) {
      return Paths.get(value).normalize();
    }
    return Paths.get(uploadpath, value).normalize();
  }

  private String resolveFileName(BizDocFile doc) {
    if (doc == null) {
      return "";
    }
    if (oConvertUtils.isNotEmpty(doc.getStorageFilename())) {
      return CommonUtils.getFileName(doc.getStorageFilename());
    }
    if (oConvertUtils.isNotEmpty(doc.getStoragePath())) {
      String obj = toObjectName(doc.getStoragePath());
      if (oConvertUtils.isNotEmpty(obj)) {
        return CommonUtils.getFileName(obj.substring(obj.lastIndexOf('/') + 1));
      }
      return CommonUtils.getFileName(doc.getStoragePath().substring(doc.getStoragePath().lastIndexOf('/') + 1));
    }
    return CommonUtils.getFileName(doc.getActualFileName());
  }

  private String resolveFileNameFromPath(String path) {
    if (oConvertUtils.isEmpty(path)) {
      return "";
    }
    String obj = toObjectName(path);
    if (oConvertUtils.isNotEmpty(obj)) {
      return CommonUtils.getFileName(obj.substring(obj.lastIndexOf('/') + 1));
    }
    return CommonUtils.getFileName(path.substring(path.lastIndexOf('/') + 1));
  }

  private String toStoredValue(String originalValue, String newObjectName) {
    if (oConvertUtils.isEmpty(newObjectName)) {
      return originalValue;
    }
    return isRemoteUrl(originalValue) ? buildMinioUrl(newObjectName) : normalizeObjectName(newObjectName);
  }

  private String buildMinioUrl(String objectName) {
    String baseUrl = minioUrl.endsWith("/") ? minioUrl : minioUrl + "/";
    return baseUrl + minioBucketName + "/" + normalizeObjectName(objectName);
  }

  private String toObjectName(String path) {
    if (oConvertUtils.isEmpty(path)) {
      return path;
    }
    String value = path.trim();
    if (isRemoteUrl(value)) {
      try {
        java.net.URI uri = java.net.URI.create(value);
        String p = uri.getPath();
        String bucketPrefix = "/" + minioBucketName + "/";
        int idx = p.indexOf(bucketPrefix);
        if (idx >= 0) {
          return p.substring(idx + bucketPrefix.length());
        }
      } catch (Exception ignore) {
      }
    }
    if (value.startsWith(minioBucketName + "/")) {
      return value.substring(minioBucketName.length() + 1);
    }
    return value;
  }

  private boolean isRemoteUrl(String path) {
    return oConvertUtils.isNotEmpty(path) && (path.startsWith("http://") || path.startsWith("https://"));
  }

  private String normalizeObjectName(String objectName) {
    if (oConvertUtils.isEmpty(objectName)) {
      return objectName;
    }
    String value = objectName.replace("\\", "/");
    while (value.startsWith("/")) {
      value = value.substring(1);
    }
    return value;
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

  private void ensureBucket(MinioClient client) throws Exception {
    if (!client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(minioBucketName).build())) {
      client.makeBucket(io.minio.MakeBucketArgs.builder().bucket(minioBucketName).build());
    }
  }

  private boolean objectExists(MinioClient client, String objectName) {
    try {
      io.minio.StatObjectResponse stat = client.statObject(io.minio.StatObjectArgs.builder().bucket(minioBucketName).object(objectName).build());
      return stat != null;
    } catch (Exception e) {
      return false;
    }
  }

  private void copyMinioObject(String oldObject, String newObject) throws Exception {
    MinioClient client = buildMinioClient();
    ensureBucket(client);
    if (objectExists(client, newObject)) {
      return;
    }
    client.copyObject(CopyObjectArgs.builder()
        .bucket(minioBucketName)
        .object(newObject)
        .source(SourceObject.builder().bucket(minioBucketName).object(oldObject).build())
        .build());
  }

  private void removeMinioObject(String objectName) throws Exception {
    MinioClient client = buildMinioClient();
    client.removeObject(RemoveObjectArgs.builder().bucket(minioBucketName).object(objectName).build());
  }

  @Data
  public static class StorageMigrationReq {
    private Boolean dryRun = Boolean.TRUE;
    private Integer pageSize = 200;
    private Integer maxRecords;
  }

  @Data
  public static class StorageMigrationPlan {
    private String id;
    private String displayName;
    private String categoryPath;

    private boolean sourceLegacy;
    private boolean packageLegacy;
    private boolean mdLegacy;
    private boolean sourcePackageLegacy;

    private String oldSourcePath;
    private String newSourcePath;
    private String oldAssetRoot;
    private String newAssetRoot;
    private String oldMdPath;
    private String newMdPath;
    private String oldSourcePackagePath;
    private String newSourcePackagePath;
    private String oldAssetManifest;
    private String newAssetManifest;
  }

  @Data
  public static class StorageMigrationResult {
    private boolean dryRun;
    private int totalChecked;
    private int totalCandidates;
    private int sourceMoved;
    private int packageMoved;
    private int dbUpdated;
    private int failed;
    private List<StorageMigrationPlan> samples;
  }
}
