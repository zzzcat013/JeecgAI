package org.jeecg.modules.biz.ai5g.controller;

import lombok.extern.slf4j.Slf4j;
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

  @Autowired
  private IBizDocFileService bizDocFileService;
  @Autowired
  private org.jeecg.modules.biz.ai5g.service.IBizDocTypeService bizDocTypeService;

  private static final Set<String> ALLOW_EXT = new HashSet<>(Arrays.asList("pdf","doc","docx","xlsx","xls","csv"));

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
      doc.setStorageFilename(doc.getActualFileName());
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

    // 仅在本地上传模式、CSV类型执行简单转换，其它类型视技术可行性后续扩展
    if (!CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
      return Result.error("当前上传模式非本地，暂不支持后台Markdown转换");
    }
    if (!"csv".equalsIgnoreCase(doc.getFileType())) {
      return Result.error("暂仅支持CSV转Markdown表格");
    }
    try {
      java.io.File src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
      if (!src.exists()) return Result.error("源文件不存在");
      String mdRel = doc.getStoragePath().replaceAll("\\.[^./\\\\]+$", "") + ".md";
      java.io.File mdFile = new java.io.File(uploadpath + java.io.File.separator + mdRel);
      mdFile.getParentFile().mkdirs();

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

      doc.setMdConverted(true);
      doc.setMdPath(mdRel);
      bizDocFileService.updateById(doc);
      return Result.OK(doc);
    } catch (Exception e) {
      log.error("convert csv to md error", e);
      return Result.error("转换失败: " + e.getMessage());
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

  @GetMapping("/preview/{id}")
  public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable("id") String id) {
    BizDocFile doc = bizDocFileService.getById(id);
    if (doc == null) return org.springframework.http.ResponseEntity.notFound().build();
    try {
      java.io.File src;
      if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
        src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
      } else {
        return org.springframework.http.ResponseEntity.status(302).header("Location", doc.getStorageFilename()).build();
      }
      if (!src.exists()) return org.springframework.http.ResponseEntity.notFound().build();
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
