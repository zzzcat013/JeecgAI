package org.jeecg.modules.biz.ai5g.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scope-limited read-only query service for AI/MCP tools.
 */
@Service
@RequiredArgsConstructor
public class Ai5gDomainQueryService {
    private static final int MAX_PAGE_SIZE = 200;
    private static final Pattern TABLE_REF = Pattern.compile("(?i)\\b(from|join)\\s+([`\\w.]+)");
    private static final Pattern DANGEROUS_SQL = Pattern.compile("(?i)\\b(insert|update|delete|drop|alter|truncate|create|replace|grant|revoke|merge|call|exec|execute|load|outfile|dumpfile)\\b");

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> scopes() {
        return jdbcTemplate.queryForList(
                "SELECT scope_code AS scopeCode, scope_name AS scopeName, scope_type AS scopeType, description " +
                        "FROM biz_ai5g_query_scope WHERE enabled=1 ORDER BY sort_no, scope_code");
    }

    public Map<String, Object> context(String scopeCode) {
        Scope scope = loadScope(scopeCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeCode", scope.scopeCode);
        result.put("scopeName", scope.scopeName);
        result.put("scopeType", scope.scopeType);
        result.put("description", scope.description);
        result.put("basePrompt", scope.basePrompt);
        result.put("queryRules", scope.queryRules);
        result.put("examples", scope.examples);
        result.put("allowedTables", scope.allowedTables);
        result.put("tables", tableMetadata(scope.allowedTables));
        return result;
    }

    public Map<String, Object> safeSelect(String scopeCode, String sql, Integer pageNo, Integer pageSize) {
        Scope scope = loadScope(scopeCode);
        String normalizedSql = validateSelect(sql, scope.allowedTables);
        Page page = page(pageNo, pageSize);
        String pagedSql = hasLimit(normalizedSql) ? normalizedSql : normalizedSql + " LIMIT " + page.pageSize + " OFFSET " + page.offset;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(pagedSql);
        return pageResult(records, page, normalizedSql, scope);
    }

    public Map<String, Object> intentQuery(String scopeCode, String intent, String keyword, String projectCode,
                                           Integer pageNo, Integer pageSize) {
        Scope scope = loadScope(scopeCode);
        Page page = page(pageNo, pageSize);
        String key = StringUtils.trimToNull(keyword);
        String code = StringUtils.trimToNull(projectCode);

        if ("toc".equalsIgnoreCase(scope.scopeCode)) {
            return tocIntent(intent, key, code, page, scope);
        }
        if ("tob".equalsIgnoreCase(scope.scopeCode)) {
            return tobIntent(intent, key, code, page, scope);
        }
        throw new IllegalArgumentException("该查询范围暂未配置意图查询，请使用 safeSelect: " + scope.scopeCode);
    }

    private Map<String, Object> tocIntent(String intent, String keyword, String projectCode, Page page, Scope scope) {
        String name = StringUtils.defaultString(intent).toLowerCase(Locale.ROOT);
        if ("summary".equals(name)) {
            String sql = "SELECT COUNT(*) AS projectCount, " +
                    "SUM(CASE WHEN project_status='已开通' THEN 1 ELSE 0 END) AS openedCount, " +
                    "COALESCE(SUM(expected_user_count),0) AS expectedUserCount, " +
                    "COALESCE(SUM(resource_count),0) AS resourceCount, COALESCE(SUM(route_count),0) AS routeCount " +
                    "FROM biz_5g_toc_project_overview_view";
            return intentResult(jdbcTemplate.queryForList(sql), page, sql, scope, "summary");
        }
        if ("projects".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "project_name", "project_code", "customer_name", "dnn", "service_id", "upf_name");
            String sql = "SELECT project_code AS projectCode, project_name AS projectName, customer_name AS customerName, " +
                    "dnn, service_id AS serviceId, upf_name AS upfName, project_status AS projectStatus, " +
                    "expected_user_count AS expectedUserCount, resource_count AS resourceCount, route_count AS routeCount " +
                    "FROM biz_5g_toc_project_overview_view WHERE 1=1 " + where +
                    "ORDER BY requested_open_date DESC, project_name LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "projects");
        }
        if ("routes".equals(name) || "addresspools".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "p.project_name", "r.project_code", "r.address_pool", "r.destination_cidr", "r.next_hop", "r.route_type");
            String sql = "SELECT r.project_code AS projectCode, p.project_name AS projectName, r.upf_name AS upfName, " +
                    "r.route_type AS routeType, r.address_pool AS addressPool, r.destination_cidr AS destinationCidr, r.next_hop AS nextHop, r.vrf " +
                    "FROM biz_5g_toc_route_config r JOIN biz_5g_toc_project p ON p.id=r.project_id " +
                    "WHERE 1=1 " + where +
                    "ORDER BY p.project_name, r.upf_name, r.destination_cidr LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "routes");
        }
        if ("resources".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "p.project_name", "r.project_code", "r.resource_type", "r.resource_name", "r.resource_value", "r.network_side");
            String sql = "SELECT r.project_code AS projectCode, p.project_name AS projectName, r.resource_type AS resourceType, " +
                    "r.resource_name AS resourceName, r.network_side AS networkSide, r.resource_value AS resourceValue, r.backup_value AS backupValue " +
                    "FROM biz_5g_toc_network_resource r JOIN biz_5g_toc_project p ON p.id=r.project_id " +
                    "WHERE 1=1 " + where +
                    "ORDER BY p.project_name, r.circuit_no, r.sort_no LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "resources");
        }
        if ("documents".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "p.project_name", "d.project_code", "d.doc_type", "d.title", "d.content", "d.keywords");
            String sql = "SELECT d.project_code AS projectCode, p.project_name AS projectName, d.doc_type AS docType, d.title, d.content, d.keywords " +
                    "FROM biz_5g_toc_doc_fragment d JOIN biz_5g_toc_project p ON p.id=d.project_id " +
                    "WHERE 1=1 " + where +
                    "ORDER BY p.project_name, d.doc_type, d.sort_no LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "documents");
        }
        throw new IllegalArgumentException("ToC不支持的意图: " + intent + "，可用: summary, projects, routes, resources, documents");
    }

    private Map<String, Object> tobIntent(String intent, String keyword, String projectCode, Page page, Scope scope) {
        String name = StringUtils.defaultString(intent).toLowerCase(Locale.ROOT);
        if ("projects".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "project_name", "project_code", "customer_name", "dnn", "lac", "plc_server_ip", "ar_ip");
            String sql = "SELECT project_code AS projectCode, project_name AS projectName, customer_name AS customerName, dnn, lac, plc_server_ip AS plcServerIp, ar_ip AS arIp " +
                    "FROM biz_5g_tob_project WHERE 1=1 " + where +
                    "ORDER BY project_name LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "projects");
        }
        if ("cpe".equals(name) || "sim".equals(name) || "cpesim".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "project_name", "project_code", "fixed_ip", "iccid", "msisdn", "vehicle_no", "vehicle_type", "service_type");
            String sql = "SELECT project_code AS projectCode, project_name AS projectName, service_type AS serviceType, vehicle_type AS vehicleType, vehicle_no AS vehicleNo, " +
                    "fixed_ip AS fixedIp, cpe_login_addr AS cpeLoginAddr, recorder_ip AS recorderIp, plc_ip AS plcIp, iccid, msisdn, imsi, sim_status AS simStatus, cpe_remark AS remark " +
                    "FROM biz_5g_tob_cpe_sim_view WHERE 1=1 " + where +
                    "ORDER BY project_name, service_type, vehicle_no LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "cpeSim");
        }
        if ("cameras".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "p.project_name", "c.project_code", "c.vehicle_name", "c.actual_ip_range", "c.planned_ip_range", "c.mapping_port", "c.ar_open_port");
            String sql = "SELECT c.project_code AS projectCode, p.project_name AS projectName, c.vehicle_name AS vehicleName, c.camera_count AS cameraCount, " +
                    "c.actual_ip_range AS actualIpRange, c.planned_ip_range AS plannedIpRange, c.mapping_port AS mappingPort, c.ar_open_port AS arOpenPort, c.remark " +
                    "FROM biz_5g_tob_camera_config c JOIN biz_5g_tob_project p ON p.id=c.project_id " +
                    "WHERE 1=1 " + where +
                    "ORDER BY p.project_name, c.vehicle_name LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "cameras");
        }
        if ("documents".equals(name)) {
            List<Object> args = new ArrayList<>();
            String where = scopedWhere(keyword, projectCode, args, "p.project_name", "d.project_code", "d.doc_type", "d.title", "d.content", "d.keywords");
            String sql = "SELECT d.project_code AS projectCode, p.project_name AS projectName, d.doc_type AS docType, d.title, d.content, d.keywords " +
                    "FROM biz_5g_tob_doc_fragment d JOIN biz_5g_tob_project p ON p.id=d.project_id " +
                    "WHERE 1=1 " + where +
                    "ORDER BY p.project_name, d.doc_type, d.source_row LIMIT ? OFFSET ?";
            args.add(page.pageSize);
            args.add(page.offset);
            return intentResult(jdbcTemplate.queryForList(sql, args.toArray()), page, sql, scope, "documents");
        }
        throw new IllegalArgumentException("ToB不支持的意图: " + intent + "，可用: projects, cpeSim, cameras, documents");
    }

    private Scope loadScope(String scopeCode) {
        String code = StringUtils.defaultIfBlank(scopeCode, "toc");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT scope_code, scope_name, scope_type, description, allowed_tables, base_prompt, query_rules, examples " +
                        "FROM biz_ai5g_query_scope WHERE scope_code=? AND enabled=1", code);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("未找到可用查询范围: " + code);
        }
        Map<String, Object> row = rows.get(0);
        return new Scope(
                string(row.get("scope_code")),
                string(row.get("scope_name")),
                string(row.get("scope_type")),
                string(row.get("description")),
                splitTables(string(row.get("allowed_tables"))),
                string(row.get("base_prompt")),
                string(row.get("query_rules")),
                string(row.get("examples"))
        );
    }

    private String scopedWhere(String keyword, String projectCode, List<Object> args,
                               String projectNameColumn, String projectCodeColumn, String... keywordColumns) {
        StringBuilder where = new StringBuilder();
        if (keyword != null) {
            List<String> columns = new ArrayList<>();
            if (StringUtils.isNotBlank(projectNameColumn)) {
                columns.add(projectNameColumn);
            }
            if (StringUtils.isNotBlank(projectCodeColumn)) {
                columns.add(projectCodeColumn);
            }
            columns.addAll(Arrays.asList(keywordColumns));
            where.append(" AND (")
                    .append(columns.stream().map(column -> column + " LIKE ?").collect(Collectors.joining(" OR ")))
                    .append(") ");
            for (int i = 0; i < columns.size(); i++) {
                args.add(like(keyword));
            }
        }
        if (projectCode != null && StringUtils.isNotBlank(projectCodeColumn)) {
            where.append(" AND ").append(projectCodeColumn).append("=? ");
            args.add(projectCode);
        }
        return where.toString();
    }

    private List<Map<String, Object>> tableMetadata(List<String> tables) {
        if (tables.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = tables.stream().map(t -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> tableRows = jdbcTemplate.queryForList(
                "SELECT table_name AS tableName, table_comment AS tableComment FROM information_schema.tables " +
                        "WHERE table_schema=DATABASE() AND table_name IN (" + placeholders + ") ORDER BY table_name",
                tables.toArray());
        List<Map<String, Object>> columnRows = jdbcTemplate.queryForList(
                "SELECT table_name AS tableName, column_name AS columnName, data_type AS dataType, column_comment AS columnComment, column_key AS columnKey " +
                        "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN (" + placeholders + ") " +
                        "ORDER BY table_name, ordinal_position",
                tables.toArray());
        Map<String, List<Map<String, Object>>> columnsByTable = columnRows.stream()
                .collect(Collectors.groupingBy(r -> string(r.get("tableName")), LinkedHashMap::new, Collectors.toList()));
        tableRows.forEach(row -> row.put("columns", columnsByTable.getOrDefault(string(row.get("tableName")), Collections.emptyList())));
        return tableRows;
    }

    private String validateSelect(String sql, List<String> allowedTables) {
        String value = StringUtils.trimToEmpty(sql);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("SQL不能为空");
        }
        if (!value.toLowerCase(Locale.ROOT).startsWith("select")) {
            throw new IllegalArgumentException("仅支持SELECT查询");
        }
        if (value.contains(";") || value.contains("--") || value.contains("/*") || value.contains("*/")) {
            throw new IllegalArgumentException("SQL不能包含分号或注释");
        }
        String withoutLiterals = value.replaceAll("'([^']|'')*'", "''").replaceAll("\"([^\"]|\"\")*\"", "\"\"");
        if (DANGEROUS_SQL.matcher(withoutLiterals).find()) {
            throw new IllegalArgumentException("SQL包含非只读关键字");
        }
        Set<String> refs = referencedTables(withoutLiterals);
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("未识别到查询表，请使用显式FROM/JOIN");
        }
        Set<String> allowed = allowedTables.stream().map(String::toLowerCase).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> rejected = refs.stream().filter(t -> !allowed.contains(t.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException("SQL包含未授权表: " + rejected + "；允许表: " + allowedTables);
        }
        return value;
    }

    private Set<String> referencedTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REF.matcher(sql);
        while (matcher.find()) {
            String raw = matcher.group(2);
            String table = raw.replace("`", "");
            int dot = table.lastIndexOf('.');
            if (dot >= 0) {
                table = table.substring(dot + 1);
            }
            tables.add(table);
        }
        return tables;
    }

    private boolean hasLimit(String sql) {
        return Pattern.compile("(?i)\\blimit\\b").matcher(sql).find();
    }

    private Page page(Integer pageNo, Integer pageSize) {
        int current = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page(current, size, (long) (current - 1) * size);
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, Page page, String sql, Scope scope) {
        return intentResult(records, page, sql, scope, "safeSelect");
    }

    private Map<String, Object> intentResult(List<Map<String, Object>> records, Page page, String sql, Scope scope, String intent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeCode", scope.scopeCode);
        result.put("intent", intent);
        result.put("records", records);
        result.put("current", page.pageNo);
        result.put("size", page.pageSize);
        result.put("executedSql", sql);
        result.put("note", "结果来自受限只读查询工具；如需换范围，先调用 ai5gQueryContext 查看 allowedTables 和 queryRules。");
        return result;
    }

    private List<String> splitTables(String csv) {
        if (StringUtils.isBlank(csv)) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private String like(String value) {
        return value == null ? null : "%" + value + "%";
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record Scope(String scopeCode, String scopeName, String scopeType, String description,
                         List<String> allowedTables, String basePrompt, String queryRules, String examples) {}

    private record Page(int pageNo, int pageSize, long offset) {}
}
