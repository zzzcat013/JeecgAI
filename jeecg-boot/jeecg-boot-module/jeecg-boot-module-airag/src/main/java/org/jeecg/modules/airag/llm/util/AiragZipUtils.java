package org.jeecg.modules.airag.llm.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Airag 知识库文档打包工具类
 * 将包含 MD 和图片的目录打包成符合 JEECG 规范的 ZIP 格式
 */
@Slf4j
public class AiragZipUtils {

    private static final String STATIC_AIGC_PATH = "aigc/";
    private static final String MD_IMAGE_PREFIX = "/aigc/";

    /**
     * 将目录打包成符合 JEECG 知识库规范的 ZIP
     * 
     * @param sourceDirPath 源目录（包含 md 和 images 子目录）
     * @param outputZipPath 输出 ZIP 路径
     * @return 是否成功
     */
    public static boolean packageToKnowledgeZip(String sourceDirPath, String outputZipPath) {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            log.error("源目录不存在或不是目录: {}", sourceDirPath);
            return false;
        }

        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(new FileOutputStream(outputZipPath))) {
            zaos.setEncoding("UTF-8");

            // 1. 处理所有 MD 文件
            Collection<File> mdFiles = FileUtils.listFiles(sourceDir, new String[]{"md"}, true);
            for (File mdFile : mdFiles) {
                String content = FileUtils.readFileToString(mdFile, StandardCharsets.UTF_8);
                // 替换图片路径: ![](images/xxx.png) -> ![](/aigc/xxx.png)
                // 或者 ![](xxx.png) 如果图片在同级
                String processedContent = processMarkdownImages(content);
                
                String relativePath = sourceDir.toPath().relativize(mdFile.toPath()).toString();
                // 统一斜杠格式
                relativePath = relativePath.replace("\\", "/");
                
                // 更加鲁棒的名称清洗逻辑
                String fileName = FilenameUtils.getName(relativePath);
                // 匹配: 文件名_时间戳.md (时间戳通常为 10-15 位)
                if (fileName.matches(".*_\\d{10,15}\\.md$")) {
                    String cleanName = fileName.replaceAll("_\\d{10,15}\\.md$", ".md");
                    relativePath = relativePath.replace(fileName, cleanName);
                    log.info("清理 MD 文件名时间戳: {} -> {}", fileName, cleanName);
                } else if (fileName.matches(".*_\\d{10,15}$")) {
                    // 处理没有扩展名但带时间戳的情况
                    String cleanName = fileName.replaceAll("_\\d{10,15}$", "");
                    relativePath = relativePath.replace(fileName, cleanName);
                    log.info("清理文件名时间戳(无扩展名): {} -> {}", fileName, cleanName);
                }
                
                ZipArchiveEntry entry = new ZipArchiveEntry(relativePath);
                zaos.putArchiveEntry(entry);
                zaos.write(processedContent.getBytes(StandardCharsets.UTF_8));
                zaos.closeArchiveEntry();
            }

            // 2. 递归扫描所有图片文件 (兼容 images, auto/images, assets 等各种目录结构)
            String[] imgExts = new String[]{"png", "jpg", "jpeg", "gif", "svg", "bmp", "webp"};
            // 使用 true 开启递归查找
            Collection<File> allImages = FileUtils.listFiles(sourceDir, imgExts, true);
            
            for (File imgFile : allImages) {
                // 排除以 . 开头的隐藏文件
                if (imgFile.getName().startsWith(".")) {
                    continue;
                }
                
                // 统一扁平化到 aigc/ 目录下，避免多级目录问题
                ZipArchiveEntry entry = new ZipArchiveEntry(STATIC_AIGC_PATH + imgFile.getName());
                zaos.putArchiveEntry(entry);
                try (FileInputStream fis = new FileInputStream(imgFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zaos.write(buffer, 0, len);
                    }
                }
                zaos.closeArchiveEntry();
            }

            zaos.finish();
            log.info("打包成功: {}", outputZipPath);
            return true;
        } catch (IOException e) {
            log.error("打包 ZIP 失败", e);
            return false;
        }
    }

    /**
     * 处理 Markdown 中的图片引用
     * 匹配 ![](images/xxx.png) 或 ![](xxx.png)
     * 同时处理 HTML 图片 src="xxx.png"
     */
    private static String processMarkdownImages(String content) {
        // 1. 匹配 Markdown 图片语法 ![] (path)
        Pattern patternMd = Pattern.compile("!\\[.*?\\]\\((.*?)\\)");
        Matcher matcherMd = patternMd.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcherMd.find()) {
            String originalPath = matcherMd.group(1);
            String fileName = FilenameUtils.getName(originalPath);
            // 替换为标准的 /aigc/xxx.png
            matcherMd.appendReplacement(sb, Matcher.quoteReplacement("![](" + MD_IMAGE_PREFIX + fileName + ")"));
        }
        matcherMd.appendTail(sb);
        String processedContent = sb.toString();

        // 2. 匹配 HTML 图片语法 src="path"
        Pattern patternHtml = Pattern.compile("src\\s*=\\s*['\"]([^'\"]*?)['\"]");
        Matcher matcherHtml = patternHtml.matcher(processedContent);
        sb = new StringBuffer();
        while (matcherHtml.find()) {
            String originalPath = matcherHtml.group(1);
            // 简单过滤：仅处理常见图片格式或已经是 aigc 目录的（避免误伤 js/css）
            if (isImageResource(originalPath)) {
                String fileName = FilenameUtils.getName(originalPath);
                // 替换为标准的 src="/aigc/xxx.png"
                matcherHtml.appendReplacement(sb, "src=\"" + MD_IMAGE_PREFIX + fileName + "\"");
            } else {
                matcherHtml.appendReplacement(sb, matcherHtml.group(0));
            }
        }
        matcherHtml.appendTail(sb);
        return sb.toString();
    }

    private static boolean isImageResource(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") || 
               lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") || 
               lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".bmp") || 
               lowerUrl.endsWith(".webp") || lowerUrl.contains("/aigc/");
    }
}
