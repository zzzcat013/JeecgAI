package org.jeecg.modules.biz.ai5g.util;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU Gradio Web 接口调用客户端 (Gradio 4.x 适配版)
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
        factory.setReadTimeout(300000);   // 5min
        return new RestTemplate(factory);
    }

    /**
     * 调用 Gradio 接口解析 PDF
     *
     * @param mineruUrl Gradio 服务地址 (例如 http://10.52.7.21:7810/)
     * @param pdfFile   PDF 文件
     * @return 包含 "content" (Markdown内容) 和 "hasImages" (布尔值) 的 JSONObject
     */
    public static JSONObject parsePdf(String mineruUrl, File pdfFile) {
        try {
            if (mineruUrl == null || !mineruUrl.startsWith("http")) {
                log.error("MinerU URL 配置错误: {}", mineruUrl);
                return null;
            }
            if (!mineruUrl.endsWith("/")) {
                mineruUrl += "/";
            }

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

    private static JSONObject downloadAndExtractMd(String zipUrl, String outputDir) {
        File tempZip = null;
        String mdContent = null;
        boolean hasImages = false;
        long start = System.currentTimeMillis();
        try {
            tempZip = new File(outputDir + File.separator + "mineru_res_" + System.currentTimeMillis() + ".zip");
            log.info("开始下载 MinerU 结果 ZIP: {}", zipUrl);
            
            RestTemplate restTemplate = getTimeoutRestTemplate();
            byte[] zipBytes = restTemplate.getForObject(zipUrl, byte[].class);
            
            if (zipBytes == null) {
                log.error("下载 ZIP 失败，内容为空");
                return null;
            }
            log.info("ZIP 下载完成，大小: {} bytes，耗时: {}ms", zipBytes.length, (System.currentTimeMillis() - start));
            
            FileUtils.writeByteArrayToFile(tempZip, zipBytes);
            
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        File dir = new File(outputDir + File.separator + entry.getName());
                        dir.mkdirs();
                        if (entry.getName().contains("images")) {
                            hasImages = true;
                        }
                    } else {
                        File file = new File(outputDir + File.separator + entry.getName());
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
                        }
                    }
                    zis.closeEntry();
                }
            }
            
            if (mdContent != null) {
                JSONObject res = new JSONObject();
                res.put("content", mdContent);
                res.put("hasImages", hasImages);
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
}
