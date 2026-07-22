package org.jeecg.modules.biz.ai5g.controller;

import lombok.RequiredArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.biz.ai5g.service.Ai5gDomainQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI5G domain query tools for API-plugin/MCP access.
 */
@RestController
@RequestMapping("/ai5g/mcp/domain-query")
@RequiredArgsConstructor
public class Ai5gDomainQueryMcpController {
    private final Ai5gDomainQueryService queryService;

    @GetMapping("/scopes")
    public Result<List<Map<String, Object>>> scopes() {
        return Result.OK(queryService.scopes());
    }

    @GetMapping("/context")
    public Result<Map<String, Object>> context(@RequestParam(defaultValue = "toc") String scopeCode) {
        return Result.OK(queryService.context(scopeCode));
    }

    @PostMapping("/safeSelect")
    public Result<Map<String, Object>> safeSelect(@RequestBody Map<String, Object> body) {
        return Result.OK(queryService.safeSelect(
                string(body.get("scopeCode"), "toc"),
                string(body.get("sql"), ""),
                integer(body.get("pageNo")),
                integer(body.get("pageSize"))));
    }

    @PostMapping("/intentQuery")
    public Result<Map<String, Object>> intentQuery(@RequestBody Map<String, Object> body) {
        return Result.OK(queryService.intentQuery(
                string(body.get("scopeCode"), "toc"),
                string(body.get("intent"), ""),
                string(body.get("keyword"), null),
                string(body.get("projectCode"), null),
                integer(body.get("pageNo")),
                integer(body.get("pageSize"))));
    }

    private String string(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
