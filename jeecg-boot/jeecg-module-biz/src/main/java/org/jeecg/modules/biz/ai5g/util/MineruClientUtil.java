package org.jeecg.modules.biz.ai5g.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 远程接口调用客户端，兼容 Gradio 4.x 和 FastAPI /file_parse。
 *
 * @Author: Trae
 * @Date: 2026-02-03
 */
@Slf4j
public class MineruClientUtil {

    /**
     * 获取配置了超时的 RestTemplate
     */
    private static RestTemplate getTimeoutRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30s
        factory.setReadTimeout(1200000);   // 20min
        return new RestTemplate(factory);
    }

    /**
     * 默认按 Gradio 接口解析 PDF，兼容旧配置。
     *
     * @param mineruUrl Gradio 服务地址 (例如 http://10.52.7.21:7810/)
     * @param pdfFile   PDF 文件
     * @return 包含 "content" (Markdown内容) 和 "hasImages" (布尔值) 的 JSONObject
     */
    public static JSONObject parsePdf(String mineruUrl, File pdfFile) {
        return parsePdf(mineruUrl, pdfFile, "gradio");
    }

    /**
     * 调用 MinerU 远程接口解析 PDF
     *
     * @param mineruUrl MinerU 服务地址
     * @param pdfFile   PDF 文件
     * @param mode      gradio 或 api
     * @return 包含 "content" (Markdown内容) 和 "hasImages" (布尔值) 的 JSONObject
     */
    public static JSONObject parsePdf(String mineruUrl, File pdfFile, String mode) {
        if ("api".equalsIgnoreCase(mode)) {
            return parsePdfByApi(mineruUrl, pdfFile);
        }
        return parsePdfByGradio(mineruUrl, pdfFile);
    }

    /**
     * 调用 Gradio 接口解析 PDF
     */
    private static JSONObject parsePdfByGradio(String mineruUrl, File pdfFile) {
        try {
            if (mineruUrl == null || !mineruUrl.startsWith("http")) {
                log.error("MinerU URL 配置错误: {}", mineruUrl);
                return null;
            }
            mineruUrl = normalizeMineruUrl(mineruUrl);

            // 1. 上传文件到 Gradio
            String uploadUrl = mineruUrl + "gradio_api/upload";
            
            RestTemplate restTemplate = getTimeoutRestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new FileSystemResource(pdfFile));
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            log.info("MinerU 开始上传文件: {}, 目标: {}", pdfFile.getName(), uploadUrl);
            
            ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);
            
            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("MinerU 上传失败: {}, 响应: {}", response.getStatusCode(), response.getBody());
                return null;
            }
            
            JSONArray uploadResult = JSONArray.parseArray(response.getBody());
            if (uploadResult == null || uploadResult.isEmpty()) {
                return null;
            }
            String remotePath = uploadResult.getString(0);
            log.info("MinerU 文件上传成功，远程路径: {}", remotePath);

            // 2. 发起异步调用 (Gradio 4.x /call 接口)
            String callUrl = mineruUrl + "gradio_api/call/to_markdown";
            
            JSONObject callParams = new JSONObject();
            JSONArray data = new JSONArray();
            
            // 参数1: FileData 对象
            JSONObject fileObj = new JSONObject();
            fileObj.put("path", remotePath);
            fileObj.put("meta", new JSONObject().fluentPut("_type", "gradio.FileData"));
            data.add(fileObj);
            
            // 严格对齐 MinerU 2.5 Gradio 4.x 的 8 个参数
            data.add(1000); // max_pages
            data.add(false); // force_ocr
            data.add(true);  // formula_label_hybrid
            data.add(true);  // table_enable
            data.add("ch (Chinese, English, Chinese Traditional)"); // ocr_language
            data.add("hybrid-auto-engine"); // backend
            data.add(""); // server_url
            
            callParams.put("data", data);
            
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> jsonEntity = new HttpEntity<>(callParams.toJSONString(), jsonHeaders);
            ResponseEntity<JSONObject> callResponse = restTemplate.postForEntity(callUrl, jsonEntity, JSONObject.class);
            
            if (callResponse.getStatusCode() != HttpStatus.OK || callResponse.getBody() == null) {
                log.error("MinerU 发起调用失败: {}", callResponse.getStatusCode());
                return null;
            }
            
            String eventId = callResponse.getBody().getString("event_id");
            if (eventId == null) {
                log.error("MinerU 未返回 event_id");
                return null;
            }
            log.info("MinerU 异步任务已提交, event_id: {}", eventId);

            // 3. 轮询结果 (通过 SSE 接口)
            String sseUrl = mineruUrl + "gradio_api/call/to_markdown/" + eventId;
            return pollSseResult(sseUrl, pdfFile.getParentFile().getAbsolutePath(), mineruUrl);
            
        } catch (Exception e) {
            log.error("MinerU Gradio 调用异常", e);
            return null;
        }
    }

    /**
     * 调用 MinerU FastAPI /file_parse 接口解析 PDF。
     */
    private static JSONObject parsePdfByApi(String mineruUrl, File pdfFile) {
        try {
            if (mineruUrl == null || !mineruUrl.startsWith("http")) {
                log.error("MinerU URL 配置错误: {}", mineruUrl);
                return null;
            }
            mineruUrl = normalizeMineruUrl(mineruUrl);
            String fileParseUrl = mineruUrl + "file_parse";

            RestTemplate restTemplate = getTimeoutRestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(java.util.Collections.singletonList(MediaType.ALL));
            headers.set("User-Agent", "JeecgAI-MinerU-Client/1.0");

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new FileSystemResource(pdfFile));
            body.add("lang_list", "ch");
            body.add("backend", "hybrid-engine");
            body.add("effort", "medium");
            body.add("parse_method", "auto");
            body.add("formula_enable", "true");
            body.add("table_enable", "true");
            body.add("image_analysis", "true");
            body.add("return_md", "true");
            body.add("return_middle_json", "false");
            body.add("return_model_output", "false");
            body.add("return_content_list", "false");
            body.add("return_images", "true");
            body.add("response_format_zip", "true");
            body.add("return_original_file", "false");
            body.add("client_side_output_generation", "false");
            body.add("start_page_id", "0");
            body.add("end_page_id", "99999");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            log.info("MinerU API 开始解析文件: {}, 目标: {}", pdfFile.getName(), fileParseUrl);

            ResponseEntity<byte[]> response = restTemplate.exchange(fileParseUrl, HttpMethod.POST, requestEntity, byte[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().length == 0) {
                log.error("MinerU API 解析失败: {}, 响应为空", response.getStatusCode());
                return null;
            }

            byte[] responseBytes = response.getBody();
            MediaType contentType = response.getHeaders().getContentType();
            if ((contentType != null && MediaType.APPLICATION_JSON.includes(contentType)) || looksLikeJson(responseBytes)) {
                String responseText = new String(responseBytes, StandardCharsets.UTF_8);
                return parseApiJsonResult(responseText, pdfFile.getParentFile().getAbsolutePath());
            }

            log.info("MinerU API 返回 ZIP 结果，大小: {} bytes", responseBytes.length);
            return extractZipBytes(responseBytes, pdfFile.getParentFile().getAbsolutePath());
        } catch (Exception e) {
            log.error("MinerU API 调用异常", e);
            return null;
        }
    }

    private static String normalizeMineruUrl(String mineruUrl) {
        String normalized = mineruUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/docs")) {
            normalized = normalized.substring(0, normalized.length() - "/docs".length());
        }
        return normalized + "/";
    }

    /**
     * 轮询 SSE 结果
     */
    private static JSONObject pollSseResult(String sseUrl, String outputDir, String mineruUrl) {
        long startTime = System.currentTimeMillis();
        long timeout = 600000; // 10分钟超时
        
        while (System.currentTimeMillis() - startTime < timeout) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(sseUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setReadTimeout(30000);
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    String lastData = null;
                    int heartBeat = 0;
                    while ((line = reader.readLine()) != null) {
                        if (heartBeat++ % 10 == 0) {
                             log.debug("SSE 接收心跳或数据: {}", line.length() > 100 ? line.substring(0, 100) + "..." : line);
                        }
                        if (line.startsWith("event: complete")) {
                            // 找到完成标志，读取下一行 data
                            String dataLine = reader.readLine();
                            if (dataLine != null && dataLine.startsWith("data: ")) {
                                String jsonData = dataLine.substring(6);
                                return parseResultData(jsonData, outputDir, mineruUrl);
                            }
                        } else if (line.startsWith("event: error")) {
                            log.error("MinerU 任务执行失败 (SSE error)");
                            return null;
                        } else if (line.startsWith("data: ")) {
                            lastData = line.substring(6);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("SSE 连接中断，正在重试... {}", e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
            
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        
        log.error("MinerU 解析任务超时");
        return null;
    }

    private static JSONObject parseResultData(String jsonData, String outputDir, String mineruUrl) {
        try {
            JSONArray resultData = JSONArray.parseArray(jsonData);
            if (resultData != null && resultData.size() >= 3) {
                // 返回值: [markdown, html, zip_file, layout_pdf]
                Object zipObj = resultData.get(2);
                if (zipObj instanceof JSONObject) {
                    JSONObject zipFileObj = (JSONObject) zipObj;
                    String zipPath = zipFileObj.getString("name");
                    if (zipPath == null) zipPath = zipFileObj.getString("path");
                    
                    if (zipPath != null) {
                        String zipUrl = mineruUrl + (zipPath.startsWith("http") ? "" : "gradio_api/file=" + zipPath);
                        log.info("提取到 MinerU 结果 ZIP: {}", zipUrl);
                        return downloadAndExtractMd(zipUrl, outputDir);
                    }
                }
                // 如果 ZIP 提取失败或不存在，回退到 index 0 的文本
                String mdText = resultData.getString(0);
                if (mdText != null && !mdText.isEmpty()) {
                    log.info("从 index 0 获取 Markdown 文本内容");
                    JSONObject res = new JSONObject();
                    res.put("content", mdText);
                    res.put("hasImages", false);
                    return res;
                }
            }
        } catch (Exception e) {
            log.error("解析结果 JSON 失败: {}", jsonData, e);
        }
        return null;
    }

    private static JSONObject parseApiJsonResult(String jsonData, String outputDir) {
        try {
            Object parsed = JSON.parse(jsonData);
            String mdText = findMarkdownText(parsed);
            if (mdText != null && !mdText.isEmpty()) {
                File extractDir = new File(outputDir + File.separator + "mineru_res_" + System.currentTimeMillis());
                FileUtils.forceMkdir(extractDir);
                File markdownFile = new File(extractDir, "mineru.md");
                FileUtils.writeStringToFile(markdownFile, mdText, StandardCharsets.UTF_8);
                int imageCount = writeJsonImages(parsed, extractDir);

                JSONObject res = new JSONObject();
                res.put("content", mdText);
                res.put("hasImages", imageCount > 0);
                res.put("extractDir", extractDir.getCanonicalPath());
                res.put("markdownPath", markdownFile.getCanonicalPath());
                return res;
            }
            log.error("MinerU API JSON 响应中未找到 Markdown 内容: {}", jsonData.length() > 500 ? jsonData.substring(0, 500) + "..." : jsonData);
        } catch (Exception e) {
            log.error("解析 MinerU API JSON 失败: {}", jsonData, e);
        }
        return null;
    }

    private static int writeJsonImages(Object value, File extractDir) throws IOException {
        int count = 0;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : object.keySet()) {
                Object child = object.get(key);
                if ("images".equalsIgnoreCase(key) && child instanceof JSONObject) {
                    JSONObject images = (JSONObject) child;
                    for (String imageName : images.keySet()) {
                        if (writeJsonImage(imageName, images.get(imageName), extractDir)) {
                            count++;
                        }
                    }
                } else {
                    count += writeJsonImages(child, extractDir);
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                count += writeJsonImages(array.get(i), extractDir);
            }
        }
        return count;
    }

    private static boolean writeJsonImage(String imageName, Object imageValue, File extractDir) throws IOException {
        try {
            String imageData = extractImageData(imageValue);
            if (imageData == null || imageData.trim().isEmpty()) {
                return false;
            }
            String normalizedName = imageName == null ? "" : imageName.trim().replace("\\", "/");
            if (normalizedName.isEmpty()) {
                normalizedName = "images/image_" + System.nanoTime() + ".png";
            }
            while (normalizedName.startsWith("/")) {
                normalizedName = normalizedName.substring(1);
            }
            if (!normalizedName.contains(".")) {
                normalizedName += ".png";
            }
            File imageFile = safeResolveZipEntry(extractDir, normalizedName);
            imageFile.getParentFile().mkdirs();
            FileUtils.writeByteArrayToFile(imageFile, decodeImageBytes(imageData));
            return true;
        } catch (Exception e) {
            log.warn("写入 MinerU JSON 图片失败: {}", imageName, e);
            return false;
        }
    }

    private static String extractImageData(Object imageValue) {
        if (imageValue instanceof String) {
            return (String) imageValue;
        }
        if (imageValue instanceof JSONObject) {
            JSONObject object = (JSONObject) imageValue;
            String[] keys = {"data", "base64", "content", "image"};
            for (String key : keys) {
                Object value = object.get(key);
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    private static byte[] decodeImageBytes(String imageData) {
        String data = imageData.trim();
        int commaIndex = data.indexOf(',');
        if (data.startsWith("data:") && commaIndex >= 0) {
            data = data.substring(commaIndex + 1);
        }
        return Base64.getDecoder().decode(data);
    }

    private static String findMarkdownText(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String[] preferredKeys = {"md_content", "markdown", "markdown_content", "md", "content"};
            for (String key : preferredKeys) {
                Object candidate = object.get(key);
                if (candidate instanceof String && !((String) candidate).trim().isEmpty()) {
                    return (String) candidate;
                }
            }
            for (String key : object.keySet()) {
                String result = findMarkdownText(object.get(key));
                if (result != null && !result.trim().isEmpty()) {
                    return result;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                String result = findMarkdownText(array.get(i));
                if (result != null && !result.trim().isEmpty()) {
                    return result;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeJson(byte[] bytes) {
        for (byte b : bytes) {
            if (!Character.isWhitespace((char) b)) {
                return b == '{' || b == '[';
            }
        }
        return false;
    }

    private static JSONObject extractZipBytes(byte[] zipBytes, String outputDir) {
        File extractDir = null;
        String mdContent = null;
        String markdownPath = null;
        boolean hasImages = false;
        try {
            extractDir = new File(outputDir + File.separator + "mineru_res_" + System.currentTimeMillis());
            FileUtils.forceMkdir(extractDir);

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        File dir = safeResolveZipEntry(extractDir, entry.getName());
                        dir.mkdirs();
                        if (entry.getName().contains("images")) {
                            hasImages = true;
                        }
                    } else {
                        File file = safeResolveZipEntry(extractDir, entry.getName());
                        file.getParentFile().mkdirs();

                        if (entry.getName().contains("images")) {
                            hasImages = true;
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        byte[] bytes = baos.toByteArray();
                        FileUtils.writeByteArrayToFile(file, bytes);

                        if (entry.getName().endsWith(".md") && mdContent == null) {
                            mdContent = new String(bytes, StandardCharsets.UTF_8);
                            markdownPath = file.getAbsolutePath();
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (mdContent != null) {
                JSONObject res = new JSONObject();
                res.put("content", mdContent);
                res.put("hasImages", hasImages);
                res.put("extractDir", extractDir.getCanonicalPath());
                res.put("markdownPath", markdownPath);
                return res;
            }
            log.error("MinerU API ZIP 结果中未找到 Markdown 文件");
        } catch (Exception e) {
            log.error("处理 MinerU API ZIP 结果失败", e);
        }
        return null;
    }

    private static JSONObject downloadAndExtractMd(String zipUrl, String outputDir) {
        File tempZip = null;
        File extractDir = null;
        String mdContent = null;
        String markdownPath = null;
        boolean hasImages = false;
        long start = System.currentTimeMillis();
        try {
            tempZip = new File(outputDir + File.separator + "mineru_res_" + System.currentTimeMillis() + ".zip");
            extractDir = new File(outputDir + File.separator + "mineru_res_" + System.currentTimeMillis());
            log.info("开始下载 MinerU 结果 ZIP: {}", zipUrl);
            
            RestTemplate restTemplate = getTimeoutRestTemplate();
            byte[] zipBytes = restTemplate.getForObject(zipUrl, byte[].class);
            
            if (zipBytes == null) {
                log.error("下载 ZIP 失败，内容为空");
                return null;
            }
            log.info("ZIP 下载完成，大小: {} bytes，耗时: {}ms", zipBytes.length, (System.currentTimeMillis() - start));
            
            FileUtils.writeByteArrayToFile(tempZip, zipBytes);
            FileUtils.forceMkdir(extractDir);
            
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        File dir = safeResolveZipEntry(extractDir, entry.getName());
                        dir.mkdirs();
                        if (entry.getName().contains("images")) {
                            hasImages = true;
                        }
                    } else {
                        File file = safeResolveZipEntry(extractDir, entry.getName());
                        file.getParentFile().mkdirs();
                        
                        if (entry.getName().contains("images")) {
                            hasImages = true;
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        byte[] bytes = baos.toByteArray();
                        FileUtils.writeByteArrayToFile(file, bytes);
                        
                        if (entry.getName().endsWith(".md") && mdContent == null) {
                            mdContent = new String(bytes, StandardCharsets.UTF_8);
                            markdownPath = file.getAbsolutePath();
                        }
                    }
                    zis.closeEntry();
                }
            }
            
            if (mdContent != null) {
                JSONObject res = new JSONObject();
                res.put("content", mdContent);
                res.put("hasImages", hasImages);
                res.put("extractDir", extractDir.getCanonicalPath());
                res.put("markdownPath", markdownPath);
                return res;
            }
        } catch (Exception e) {
            log.error("处理 MinerU ZIP 结果失败", e);
        } finally {
            if (tempZip != null && tempZip.exists()) {
                tempZip.delete();
            }
        }
        return null;
    }

    private static File safeResolveZipEntry(File targetDir, String entryName) throws IOException {
        File target = targetDir.getCanonicalFile();
        File resolved = new File(targetDir, entryName).getCanonicalFile();
        if (!resolved.toPath().startsWith(target.toPath())) {
            throw new IOException("ZIP 路径穿越攻击被阻止: " + entryName);
        }
        return resolved;
    }
}
