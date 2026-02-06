package org.jeecg.modules.biz.ai5g.controller;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.biz.ai5g.util.MineruClientUtil;
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

  @Autowired
  private IBizDocFileService bizDocFileService;
  @Autowired
  private org.jeecg.modules.biz.ai5g.service.IBizDocTypeService bizDocTypeService;
  @Autowired
  private org.springframework.core.env.Environment environment;

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

    if (!CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
      return Result.error("当前上传模式非本地，暂不支持后台Markdown转换");
    }
    
    // 异步执行转换任务
    new Thread(() -> {
        try {
            // 更新状态为处理中
            doc.setProcessStatus("processing");
            bizDocFileService.updateById(doc);
            
            String ft = doc.getFileType() == null ? "" : doc.getFileType().toLowerCase();
            
            java.io.File src = new java.io.File(uploadpath + java.io.File.separator + doc.getStoragePath());
            if (!src.exists()) {
                log.error("源文件不存在: {}", src.getAbsolutePath());
                doc.setProcessStatus("failed");
                doc.setRemark("源文件不存在");
                bizDocFileService.updateById(doc);
                return;
            }
            
            String mdRel = doc.getStoragePath().replaceAll("\\.[^./\\\\]+$", "") + ".md";
            java.io.File mdFile = new java.io.File(uploadpath + java.io.File.separator + mdRel);
            mdFile.getParentFile().mkdirs();

            boolean converted = false;
            
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
                                org.apache.commons.io.FileUtils.writeStringToFile(mdFile, mineruRes.getString("content"), java.nio.charset.StandardCharsets.UTF_8);
                                converted = true;
                            } catch (Exception e) {
                                log.error("保存 MinerU 结果失败", e);
                            }
                        }
                    }
                    
                    // 3. 尝试 MinerU 本地命令
                    if (!converted) {
                        converted = tryMineruLocalConvert(mineruInputFile, mdFile);
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
                doc.setMdConverted(true);
                doc.setMdPath(mdRel);
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
        }
    }).start();

    return Result.OK("转换任务已提交，请稍候查看结果");
  }

  private boolean tryMineruLocalConvert(java.io.File src, java.io.File mdFile) {
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
                  return false;
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
              return false;
          }

          if (p.exitValue() == 0) {
              // MinerU creates output in a subfolder: {outputPath}/{baseName}/auto/{baseName}.md
              String baseName = src.getName().substring(0, src.getName().lastIndexOf('.'));
              java.io.File generatedMd = new java.io.File(outputPath + java.io.File.separator + baseName + java.io.File.separator + "auto" + java.io.File.separator + baseName + ".md");
              if (generatedMd.exists()) {
                  org.apache.commons.io.FileUtils.copyFile(generatedMd, mdFile);
                  return true;
              }
          }
          return false;
      } catch (Exception e) {
          log.error("Local MinerU conversion error", e);
          return false;
      }
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

  @PostMapping("/save-md")
  public Result<?> saveMd(@RequestParam("id") String id, @RequestParam("content") String content) {
      BizDocFile doc = bizDocFileService.getById(id);
      if (doc == null || !Boolean.TRUE.equals(doc.getMdConverted()) || doc.getMdPath() == null) {
          return Result.error("文档未找到或未转换MD");
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
  public void previewMarkdown(@PathVariable("id") String id, jakarta.servlet.http.HttpServletResponse response) {
      BizDocFile doc = bizDocFileService.getById(id);
      if (doc == null || !Boolean.TRUE.equals(doc.getMdConverted()) || doc.getMdPath() == null) {
          response.setStatus(404);
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
      } else {
        return org.springframework.http.ResponseEntity.status(302).header("Location", doc.getStorageFilename()).build();
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
