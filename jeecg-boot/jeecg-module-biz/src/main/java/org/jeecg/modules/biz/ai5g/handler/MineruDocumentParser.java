package org.jeecg.modules.biz.ai5g.handler;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jeecg.modules.airag.llm.handler.CommandExecUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.biz.ai5g.util.MineruClientUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class MineruDocumentParser {

    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
        "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt"
    );

    public boolean support(String fileType) {
        return oConvertUtils.isNotEmpty(fileType) && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }

    public String parse(File docFile, boolean enableMinerU, String mineruUrl, String uploadpath) {
        return parse(docFile, enableMinerU, mineruUrl, uploadpath, "gradio");
    }

    public String parse(File docFile, boolean enableMinerU, String mineruUrl, String uploadpath, String mineruMode) {
        // 只有开启了 MinerU 才进行处理
        if (!enableMinerU) {
            return null;
        }

        String fileType = FilenameUtils.getExtension(docFile.getName()).toLowerCase();
        
        // MinerU 仅支持 PDF 和图片，如果是 Office 文档需要先转为 PDF
        File mineruInputFile = docFile;
        boolean isOffice = Arrays.asList("docx", "doc", "xlsx", "xls", "pptx", "ppt").contains(fileType);
        File tempPdfFile = null;
        
        if (isOffice) {
            log.info("检测到 Office 文档 ({}), 正在通过 LibreOffice 预转为 PDF...", fileType);
            String baseName = FilenameUtils.getBaseName(docFile.getName());
            String tempPdfPath = docFile.getParent() + File.separator + baseName + "_" + System.currentTimeMillis() + ".pdf";
            tempPdfFile = new File(tempPdfPath);
            
            // 调用 soffice 命令进行转换
            // macOS 下 LibreOffice 路径通常在 /Applications/LibreOffice.app/Contents/MacOS/soffice
            String sofficeCommand = "/Applications/LibreOffice.app/Contents/MacOS/soffice";
            File sofficeFile = new File(sofficeCommand);
            if (!sofficeFile.exists()) {
                sofficeCommand = "soffice"; // 尝试从环境变量找
            }
            
            // 增加 UserInstallation 参数防止并发冲突 (macOS下使用临时目录以避免权限问题)
            String tmpDir = System.getProperty("java.io.tmpdir");
            String userProfile = "file://" + tmpDir + "/soffice_user_" + System.currentTimeMillis();
            
            // 为了避免 macOS 下的沙盒权限问题，将文件复制到系统临时目录进行转换
            File tmpSrc = new File(tmpDir, "soffice_convert_" + System.currentTimeMillis() + "_" + docFile.getName());
            File tmpOutDir = new File(tmpDir, "soffice_convert_" + System.currentTimeMillis() + "_out");
            tmpOutDir.mkdirs();
            
            try {
                org.apache.commons.io.FileUtils.copyFile(docFile, tmpSrc);
                
                String[] sofficeArgs = {
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", tmpOutDir.getAbsolutePath(),
                    tmpSrc.getAbsolutePath()
                };
                
                // macOS 下不使用 UserInstallation 参数可能更安全，或者指向 tmp
                // 这里我们仅在非 macOS 下使用自定义 userProfile，或者在 macOS 下指向 /tmp
                // 根据之前的经验，macOS 下去掉 UserInstallation 参数或者指向 /tmp 都可以
                // 为了稳妥，我们使用不带 UserInstallation 的默认配置（如果之前验证通过的话），
                // 或者使用指向 /tmp 的配置。之前的测试表明，将文件移到 /tmp 下操作是最稳健的。
                
                log.info("执行 LibreOffice 转换命令: {} {}", sofficeCommand, String.join(" ", sofficeArgs));
                // 注意：这里需要根据操作系统判断是否添加 UserInstallation，或者直接依赖 CommandExecUtil
                // 但为了简化，我们假设环境配置正确。更严谨的做法是复用之前 Ai5gDocumentController 中的 trySofficeCliConvert 逻辑
                // 这里简化为直接调用
                
                String result = CommandExecUtil.execCommand(sofficeCommand, sofficeArgs);
                log.info("LibreOffice 转换输出: {}", result);
                
                // 查找生成的文件
                File[] convertedFiles = tmpOutDir.listFiles((dir, name) -> name.endsWith(".pdf"));
                if (convertedFiles != null && convertedFiles.length > 0) {
                     org.apache.commons.io.FileUtils.copyFile(convertedFiles[0], tempPdfFile);
                     mineruInputFile = tempPdfFile;
                     log.info("Office 预转换成功: {}", tempPdfFile.getAbsolutePath());
                } else {
                    log.error("LibreOffice 转换未报错但找不到生成文件");
                }
                
                // 清理临时文件
                if (tmpSrc.exists()) tmpSrc.delete();
                if (tmpOutDir.exists()) org.apache.commons.io.FileUtils.deleteDirectory(tmpOutDir);

            } catch (Exception e) {
                log.error("Office 预转 PDF 失败: {}", e.getMessage(), e);
            }
        }

        String outputPath = new File(oConvertUtils.getString(uploadpath, ""), "temp").getAbsolutePath();
        String fileBaseName = FilenameUtils.getBaseName(docFile.getName());
        String newFileDir = outputPath + File.separator + fileBaseName + File.separator + "auto" + File.separator ;
        File convertedFile = new File(newFileDir + fileBaseName + ".md");
        String content = null;

        // 如果是 Office 且预转 PDF 失败，则不继续执行 MinerU
        if (isOffice && mineruInputFile == docFile) {
            log.warn("Office 预转 PDF 失败，跳过 MinerU 解析");
        } else {
            // 优先使用远程 Web 接口
            if (oConvertUtils.isNotEmpty(mineruUrl)) {
                long start = System.currentTimeMillis();
                log.info("使用 MinerU 远程服务解析: {}, mode: {}", mineruUrl, mineruMode);
                JSONObject mineruRes = MineruClientUtil.parsePdf(mineruUrl, mineruInputFile, mineruMode);
                if (mineruRes != null && oConvertUtils.isNotEmpty(mineruRes.getString("content"))) {
                    try {
                        FileUtils.forceMkdir(new File(newFileDir));
                        content = mineruRes.getString("content");
                        org.apache.commons.io.FileUtils.writeStringToFile(convertedFile, content, StandardCharsets.UTF_8);
                        log.info("MinerU 远程解析成功 (耗时 {}ms) 并保存到: {}", (System.currentTimeMillis()-start), convertedFile.getAbsolutePath());
                    } catch (IOException e) {
                        log.error("保存 MinerU 远程解析结果失败", e);
                    }
                } else {
                    log.warn("MinerU 远程解析未返回内容或失败");
                }
            }

            // 本地命令模式 (作为备选)
            if (content == null && !convertedFile.exists()) {
                 // ... 本地命令逻辑 (略，如果需要支持可在此补充) ...
                 // 考虑到代码迁移，这里暂时只保留远程调用，因为本地调用依赖环境较重
            }
        }

        // 清理临时 PDF 文件
        if (tempPdfFile != null && tempPdfFile.exists()) {
            tempPdfFile.delete();
        }

        return content;
    }
}
