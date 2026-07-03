package org.jeecg.modules.biz.ai5g.knowledge;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.common.util.RestUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.biz.ai5g.util.KnowledgePortalTokenUtil;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 企业知识库读取客户端.
 */
public class EnterpriseKnowledgeClient {

    private static final String CONFIG_PREFIX = "knowledge-portal.";
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final String PERSON_BASE_PATH = "/webApi/v1/personBase";
    private static final String LIST_RAG_FILES_PATH = "/webApi/v1/listRagFiles";
    private static final String RETRIEVAL_PATH = "/webApi/v1/retrival";

    private EnterpriseKnowledgeClient() {
    }

    /**
     * 获取当前配置用户的个人知识库列表.
     */
    public static JSONObject listPersonBases() {
        return request(HttpMethod.GET, PERSON_BASE_PATH, null);
    }

    /**
     * 分页获取个人知识库文件列表.
     */
    public static JSONObject listPersonFiles(String categoryId, String categoryName, Long pageNum, Long pageSize) {
        JSONObject body = new JSONObject();
        body.put("categoryId", categoryId);
        if (categoryName != null && !categoryName.trim().isEmpty()) {
            body.put("categoryName", categoryName);
        }
        body.put("pageNum", pageNum == null ? 1L : pageNum);
        body.put("pageSize", pageSize == null ? 10L : pageSize);
        return listPersonFiles(body);
    }

    /**
     * 分页获取知识库文件列表, 支持文档中的全部过滤字段.
     */
    public static JSONObject listPersonFiles(JSONObject body) {
        validateNotBlank(body == null ? null : body.getString("categoryId"), "categoryId");
        if (!body.containsKey("pageNum")) {
            body.put("pageNum", 1L);
        }
        if (!body.containsKey("pageSize")) {
            body.put("pageSize", 10L);
        }
        return request(HttpMethod.POST, LIST_RAG_FILES_PATH, body);
    }

    /**
     * 检索个人知识库内容, 返回相关片段.
     */
    public static JSONObject retrieve(JSONObject body) {
        if (body == null) {
            body = new JSONObject();
        }
        if (!body.containsKey("largeScaleSearch")) {
            body.put("largeScaleSearch", false);
        }
        return request(HttpMethod.POST, RETRIEVAL_PATH, body);
    }

    private static JSONObject request(HttpMethod method, String path, JSONObject body) {
        KnowledgePortalConfig config = getConfig();
        String url = buildUrl(config.getBaseUrl(), path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(KnowledgePortalTokenUtil.getAuthToken());

        ResponseEntity<JSONObject> response = RestUtil.request(
                url,
                method,
                headers,
                null,
                body,
                JSONObject.class,
                config.getTimeout()
        );
        return response.getBody();
    }

    private static KnowledgePortalConfig getConfig() {
        if (SpringContextUtils.getApplicationContext() == null) {
            throw new IllegalStateException("Spring ApplicationContext 未初始化, 无法读取 knowledge-portal 配置");
        }
        Environment environment = SpringContextUtils.getApplicationContext().getEnvironment();
        String baseUrl = environment.getProperty(CONFIG_PREFIX + "base-url");
        Integer timeout = environment.getProperty(CONFIG_PREFIX + "timeout", Integer.class, DEFAULT_TIMEOUT);
        validateNotBlank(baseUrl, CONFIG_PREFIX + "base-url");
        return new KnowledgePortalConfig(baseUrl, timeout == null ? DEFAULT_TIMEOUT : timeout);
    }

    private static String buildUrl(String baseUrl, String path) {
        String base = baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private static void validateNotBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    private static class KnowledgePortalConfig {
        private final String baseUrl;
        private final int timeout;

        private KnowledgePortalConfig(String baseUrl, int timeout) {
            this.baseUrl = baseUrl;
            this.timeout = timeout;
        }

        private String getBaseUrl() {
            return baseUrl;
        }

        private int getTimeout() {
            return timeout;
        }
    }
}
