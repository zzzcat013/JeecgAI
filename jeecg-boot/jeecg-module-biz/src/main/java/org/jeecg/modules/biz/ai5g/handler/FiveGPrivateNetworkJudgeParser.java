package org.jeecg.modules.biz.ai5g.handler;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.airag.flow.component.enhance.IAiRagEnhanceJava;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses the 5G private-network domain classifier JSON for AI flow branching.
 */
@Component("fiveGPrivateNetworkJudgeParser")
public class FiveGPrivateNetworkJudgeParser implements IAiRagEnhanceJava {

    private static final String TRUE = "true";
    private static final String FALSE = "false";

    @Override
    public Map<String, Object> process(Map<String, Object> inputParams) {
        String raw = String.valueOf(inputParams.getOrDefault("judgeJson", "")).trim();
        raw = extractJson(raw);

        Map<String, Object> result = new HashMap<>();
        try {
            JSONObject json = JSONObject.parseObject(raw);
            String flag = json.getBooleanValue("is_5g_private_network") ? TRUE : FALSE;

            result.put("result", flag);
            result.put("is_5g_private_network", flag);
            result.put("category", defaultString(json.getString("category")));
            result.put("reason", defaultString(json.getString("reason")));
        } catch (Exception e) {
            result.put("result", FALSE);
            result.put("is_5g_private_network", FALSE);
            result.put("category", "PARSE_ERROR");
            result.put("reason", "INVALID_JSON");
        }
        return result;
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
