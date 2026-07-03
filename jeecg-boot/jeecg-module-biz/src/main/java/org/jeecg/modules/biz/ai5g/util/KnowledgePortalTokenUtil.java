package org.jeecg.modules.biz.ai5g.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.RestUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 企业知识门户 Token 获取工具.
 */
@Slf4j
public class KnowledgePortalTokenUtil {

    private static final String TOKEN_PATH = "/external-auth/ai-api-key-token";
    private static final String CONFIG_PREFIX = "knowledge-portal.";
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final String DYNAMIC_KEY_PREFIX = "kg_ai_api_key_token_";
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String IV_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int AES_KEY_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private KnowledgePortalTokenUtil() {
    }

    /**
     * 从 application.yml 的 knowledge-portal 配置获取知识门户访问 Token.
     */
    public static String getAuthToken() {
        KnowledgePortalConfig config = getConfig();
        return getAuthToken(config.getBaseUrl(), config.getApiKey(), config.getUsername(), config.getTimeout());
    }

    /**
     * 获取知识门户访问 Token.
     *
     * @param portalBaseUrl 知识门户服务地址, 例如 https://example.com 或完整 token 接口地址
     * @param apiKey        网络AI平台大模型网关 API Key
     * @param username      用户 OA 账号
     * @return 知识门户访问令牌
     */
    public static String getAuthToken(String portalBaseUrl, String apiKey, String username) {
        return getAuthToken(portalBaseUrl, apiKey, username, DEFAULT_TIMEOUT);
    }

    /**
     * 获取知识门户访问 Token.
     *
     * @param portalBaseUrl 知识门户服务地址, 例如 https://example.com 或完整 token 接口地址
     * @param apiKey        网络AI平台大模型网关 API Key
     * @param username      用户 OA 账号
     * @param timeout       请求超时时间, 单位毫秒
     * @return 知识门户访问令牌
     */
    public static String getAuthToken(String portalBaseUrl, String apiKey, String username, int timeout) {
        JSONObject response = requestAuthToken(portalBaseUrl, apiKey, username, timeout);
        if (response == null) {
            throw new IllegalStateException("知识门户 Token 接口无响应");
        }
        Integer code = response.getInteger("code");
        if (code == null || code != 200) {
            String message = response.getString("message");
            throw new IllegalStateException("获取知识门户 Token 失败, code=" + code + ", message=" + message);
        }
        JSONObject data = response.getJSONObject("data");
        String token = data == null ? null : data.getString("token");
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("知识门户 Token 响应缺少 data.token");
        }
        return token;
    }

    /**
     * 从 application.yml 的 knowledge-portal 配置请求 Token 接口, 返回完整响应体.
     */
    public static JSONObject requestAuthToken() {
        KnowledgePortalConfig config = getConfig();
        return requestAuthToken(config.getBaseUrl(), config.getApiKey(), config.getUsername(), config.getTimeout());
    }

    /**
     * 请求知识门户 Token 接口, 返回完整响应体.
     */
    public static JSONObject requestAuthToken(String portalBaseUrl, String apiKey, String username) {
        return requestAuthToken(portalBaseUrl, apiKey, username, DEFAULT_TIMEOUT);
    }

    /**
     * 请求知识门户 Token 接口, 返回完整响应体.
     */
    public static JSONObject requestAuthToken(String portalBaseUrl, String apiKey, String username, int timeout) {
        String tokenUrl = buildTokenUrl(portalBaseUrl);
        JSONObject requestBody = buildTokenRequest(apiKey, username);

        log.info("开始请求知识门户 Token, url={}, username={}", tokenUrl, username);
        ResponseEntity<JSONObject> response = RestUtil.request(
                tokenUrl,
                HttpMethod.POST,
                RestUtil.getHeaderApplicationJson(),
                null,
                requestBody,
                JSONObject.class,
                timeout
        );
        return response.getBody();
    }

    /**
     * 从配置发起一次 Token 请求并返回脱敏调试信息.
     */
    public static JSONObject debugAuthToken() {
        KnowledgePortalConfig config = getConfig();
        return debugAuthToken(config.getBaseUrl(), config.getApiKey(), config.getUsername(), config.getTimeout());
    }

    /**
     * 发起一次 Token 请求并返回脱敏调试信息.
     */
    public static JSONObject debugAuthToken(String portalBaseUrl, String apiKey, String username, int timeout) {
        JSONObject debug = new JSONObject();
        String tokenUrl = buildTokenUrl(portalBaseUrl);
        JSONObject requestBody = buildTokenRequest(apiKey, username);

        debug.put("url", tokenUrl);
        debug.put("username", username);
        debug.put("timeout", timeout);
        debug.put("apiKeyConfigured", apiKey != null && !apiKey.trim().isEmpty());
        debug.put("currentDate", requestBody.getString("currentDate"));
        debug.put("iv", requestBody.getString("iv"));
        debug.put("apiKeyEncLength", requestBody.getString("apiKeyEnc") == null ? 0 : requestBody.getString("apiKeyEnc").length());
        debug.put("apiKeyEncPrefix", maskPrefix(requestBody.getString("apiKeyEnc")));

        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = RestUtil.request(
                    tokenUrl,
                    HttpMethod.POST,
                    RestUtil.getHeaderApplicationJson(),
                    null,
                    requestBody,
                    String.class,
                    timeout
            );
            debug.put("success", true);
            putHttpStatus(debug, response.getStatusCode());
            debug.put("durationMs", System.currentTimeMillis() - startTime);
            debug.put("responseBody", maskToken(response.getBody()));
            fillParsedResponse(debug, response.getBody());
        } catch (HttpStatusCodeException e) {
            debug.put("success", false);
            putHttpStatus(debug, e.getStatusCode());
            debug.put("durationMs", System.currentTimeMillis() - startTime);
            debug.put("errorType", e.getClass().getName());
            debug.put("errorMessage", e.getMessage());
            debug.put("responseBody", maskToken(e.getResponseBodyAsString()));
            fillParsedResponse(debug, e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            debug.put("success", false);
            debug.put("durationMs", System.currentTimeMillis() - startTime);
            debug.put("errorType", e.getClass().getName());
            debug.put("errorMessage", e.getMessage());
        } catch (Exception e) {
            debug.put("success", false);
            debug.put("durationMs", System.currentTimeMillis() - startTime);
            debug.put("errorType", e.getClass().getName());
            debug.put("errorMessage", e.getMessage());
        }
        return debug;
    }

    /**
     * 从配置生成可直接执行的 curl 命令, 只生成命令不发请求.
     */
    public static JSONObject buildCurlCommand() {
        KnowledgePortalConfig config = getConfig();
        return buildCurlCommand(config.getBaseUrl(), config.getApiKey(), config.getUsername(), config.getTimeout());
    }

    /**
     * 生成可直接执行的 curl 命令, 只生成命令不发请求.
     */
    public static JSONObject buildCurlCommand(String portalBaseUrl, String apiKey, String username, int timeout) {
        String tokenUrl = buildTokenUrl(portalBaseUrl);
        JSONObject requestBody = buildTokenRequest(apiKey, username);
        int timeoutSeconds = Math.max(1, (timeout + 999) / 1000);

        String command = "curl -v --max-time " + timeoutSeconds
                + " -X POST " + shellQuote(tokenUrl)
                + " -H " + shellQuote("Content-Type: application/json")
                + " -d " + shellQuote(requestBody.toJSONString());

        JSONObject result = new JSONObject();
        result.put("url", tokenUrl);
        result.put("username", username);
        result.put("timeout", timeout);
        result.put("timeoutSeconds", timeoutSeconds);
        result.put("currentDate", requestBody.getString("currentDate"));
        result.put("iv", requestBody.getString("iv"));
        result.put("apiKeyEncLength", requestBody.getString("apiKeyEnc") == null ? 0 : requestBody.getString("apiKeyEnc").length());
        result.put("curl", command);
        result.put("requestBody", requestBody);
        return result;
    }

    /**
     * 构建 Token 接口请求参数.
     */
    public static JSONObject buildTokenRequest(String apiKey, String username) {
        validateNotBlank(apiKey, "apiKey");
        validateNotBlank(username, "username");

        String currentDate = currentDateTime();
        String iv = generateIV();

        JSONObject encryptBody = new JSONObject();
        encryptBody.put("apiKey", apiKey);
        encryptBody.put("username", username);
        encryptBody.put("currentDate", currentDate);
        encryptBody.put("iv", iv);

        String apiKeyEnc = aesEncrypt(encryptBody.toJSONString(), generateDynamicKey(), iv);

        JSONObject requestBody = new JSONObject();
        requestBody.put("apiKeyEnc", apiKeyEnc);
        requestBody.put("currentDate", currentDate);
        requestBody.put("iv", iv);
        return requestBody;
    }

    /**
     * 生成 16 位随机 IV, 字符范围 A-Za-z0-9.
     */
    public static String generateIV() {
        StringBuilder iv = new StringBuilder(IV_LENGTH);
        for (int i = 0; i < IV_LENGTH; i++) {
            iv.append(IV_CHARS.charAt(SECURE_RANDOM.nextInt(IV_CHARS.length())));
        }
        return iv.toString();
    }

    /**
     * 生成动态密钥, 格式 kg_ai_api_key_token_yyyy-MM-dd.
     */
    public static String generateDynamicKey() {
        return DYNAMIC_KEY_PREFIX + LocalDate.now(SHANGHAI_ZONE).format(DATE_FORMATTER);
    }

    /**
     * AES/CBC/PKCS5Padding 加密并 Base64 编码.
     */
    public static String aesEncrypt(String data, String key, String iv) {
        try {
            validateNotBlank(data, "data");
            validateNotBlank(key, "key");
            validateNotBlank(iv, "iv");
            if (iv.getBytes(StandardCharsets.UTF_8).length != IV_LENGTH) {
                throw new IllegalArgumentException("iv 必须为 16 位 UTF-8 字符");
            }
            if (key.length() < AES_KEY_LENGTH) {
                throw new IllegalArgumentException("key 长度不能小于 16");
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.substring(0, AES_KEY_LENGTH).getBytes(StandardCharsets.UTF_8),
                    "AES"
            );
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("知识门户 Token 参数加密失败", e);
        }
    }

    private static String currentDateTime() {
        return LocalDateTime.now(SHANGHAI_ZONE).format(DATE_TIME_FORMATTER);
    }

    private static String buildTokenUrl(String portalBaseUrl) {
        validateNotBlank(portalBaseUrl, "portalBaseUrl");
        String url = portalBaseUrl.trim();
        if (url.endsWith(TOKEN_PATH)) {
            return url;
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1) + TOKEN_PATH;
        }
        return url + TOKEN_PATH;
    }

    private static void validateNotBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    private static void putHttpStatus(JSONObject debug, HttpStatusCode statusCode) {
        debug.put("httpStatus", statusCode == null ? null : statusCode.value());
    }

    private static void fillParsedResponse(JSONObject debug, String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject responseJson = JSONObject.parseObject(responseBody);
            debug.put("businessCode", responseJson.getInteger("code"));
            debug.put("businessMessage", responseJson.getString("message"));
            JSONObject data = responseJson.getJSONObject("data");
            if (data != null) {
                debug.put("tokenMasked", maskTokenValue(data.getString("token")));
            }
        } catch (Exception ignored) {
            debug.put("responseJsonParsed", false);
        }
    }

    private static String maskPrefix(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, Math.min(12, value.length())) + "***";
    }

    private static String maskToken(String body) {
        if (body == null || body.trim().isEmpty()) {
            return body;
        }
        try {
            JSONObject json = JSONObject.parseObject(body);
            JSONObject data = json.getJSONObject("data");
            if (data != null && data.containsKey("token")) {
                data.put("token", maskTokenValue(data.getString("token")));
            }
            return json.toJSONString();
        } catch (Exception ignored) {
            return body;
        }
    }

    private static String maskTokenValue(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (token.length() <= 12) {
            return "***";
        }
        return token.substring(0, 8) + "***" + token.substring(token.length() - 4);
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static KnowledgePortalConfig getConfig() {
        if (SpringContextUtils.getApplicationContext() == null) {
            throw new IllegalStateException("Spring ApplicationContext 未初始化, 无法读取 knowledge-portal 配置");
        }
        Environment environment = SpringContextUtils.getApplicationContext().getEnvironment();
        String baseUrl = environment.getProperty(CONFIG_PREFIX + "base-url");
        String apiKey = environment.getProperty(CONFIG_PREFIX + "api-key");
        String username = environment.getProperty(CONFIG_PREFIX + "username");
        Integer timeout = environment.getProperty(CONFIG_PREFIX + "timeout", Integer.class, DEFAULT_TIMEOUT);

        validateNotBlank(baseUrl, CONFIG_PREFIX + "base-url");
        validateNotBlank(apiKey, CONFIG_PREFIX + "api-key");
        validateNotBlank(username, CONFIG_PREFIX + "username");
        return new KnowledgePortalConfig(baseUrl, apiKey, username, timeout == null ? DEFAULT_TIMEOUT : timeout);
    }

    private static class KnowledgePortalConfig {
        private final String baseUrl;
        private final String apiKey;
        private final String username;
        private final int timeout;

        private KnowledgePortalConfig(String baseUrl, String apiKey, String username, int timeout) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.username = username;
            this.timeout = timeout;
        }

        private String getBaseUrl() {
            return baseUrl;
        }

        private String getApiKey() {
            return apiKey;
        }

        private String getUsername() {
            return username;
        }

        private int getTimeout() {
            return timeout;
        }
    }
}
