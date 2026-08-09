package org.jeecg.modules.biz.roomops.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsRecord;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomopsGovernanceService {
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private IBizRoomopsTaskService taskService;

  public List<Map<String, Object>> listTemplates() {
    return jdbcTemplate.queryForList("select * from biz_roomops_template order by update_time desc, create_time desc");
  }

  public List<Map<String, Object>> listPlans(String month) {
    if (blank(month)) {
      return jdbcTemplate.queryForList("select * from biz_roomops_month_plan order by plan_month desc, create_time desc");
    }
    return jdbcTemplate.queryForList(
        "select * from biz_roomops_month_plan where plan_month = ? order by create_time desc", month);
  }

  public List<Map<String, Object>> listIssues(String status) {
    if (blank(status)) {
      return jdbcTemplate.queryForList("select * from biz_roomops_issue order by create_time desc");
    }
    return jdbcTemplate.queryForList(
        "select * from biz_roomops_issue where status = ? order by create_time desc", status);
  }

  @Transactional(rollbackFor = Exception.class)
  public void saveTemplate(JSONObject body, String operator) {
    String id = defaultText(body.getString("id"), id());
    String code = defaultText(body.getString("templateCode"), "TPL-" + System.currentTimeMillis());
    jdbcTemplate.update(
        """
        insert into biz_roomops_template
          (id, template_code, template_name, business_type, check_items_json, status, create_by, create_time, update_by, update_time)
        values (?, ?, ?, ?, ?, ?, ?, now(), ?, now())
        on duplicate key update template_name=values(template_name), business_type=values(business_type),
          check_items_json=values(check_items_json), status=values(status), update_by=values(update_by), update_time=now()
        """,
        id, code, required(body.getString("templateName"), "模板名称"),
        defaultText(body.getString("businessType"), "inspection"),
        normalizeJsonArray(body.getString("checkItemsJson")), defaultText(body.getString("status"), "1"),
        operator, operator);
  }

  @Transactional(rollbackFor = Exception.class)
  public void savePlan(JSONObject body, String operator) {
    String id = defaultText(body.getString("id"), id());
    String planCode = defaultText(body.getString("planCode"), "PLAN-" + System.currentTimeMillis());
    String month = normalizeMonth(body.getString("planMonth"));
    jdbcTemplate.update(
        """
        insert into biz_roomops_month_plan
          (id, plan_code, plan_name, plan_month, template_id, room_ids_json, assignee_userid,
           assignee_name, deadline_day, status, generated_count, create_by, create_time, update_by, update_time)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, now(), ?, now())
        on duplicate key update plan_name=values(plan_name), plan_month=values(plan_month),
          template_id=values(template_id), room_ids_json=values(room_ids_json),
          assignee_userid=values(assignee_userid), assignee_name=values(assignee_name),
          deadline_day=values(deadline_day), status=values(status), update_by=values(update_by), update_time=now()
        """,
        id, planCode, required(body.getString("planName"), "计划名称"), month,
        required(body.getString("templateId"), "模板"), normalizeJsonArray(body.getString("roomIdsJson")),
        body.getString("assigneeUserid"), body.getString("assigneeName"),
        body.getInteger("deadlineDay") == null ? 28 : body.getInteger("deadlineDay"),
        defaultText(body.getString("status"), "DRAFT"), operator, operator);
  }

  @Transactional(rollbackFor = Exception.class)
  public int generatePlan(String planId, String operatorUserid, String operatorName) {
    Map<String, Object> plan = jdbcTemplate.queryForMap("select * from biz_roomops_month_plan where id = ? for update", planId);
    if ("GENERATED".equals(String.valueOf(plan.get("status")))) {
      throw new IllegalStateException("该月度计划已生成任务，请勿重复生成");
    }
    Map<String, Object> template = jdbcTemplate.queryForMap(
        "select * from biz_roomops_template where id = ?", plan.get("template_id"));
    JSONArray roomIds = JSON.parseArray(String.valueOf(plan.get("room_ids_json")));
    YearMonth month = YearMonth.parse(String.valueOf(plan.get("plan_month")));
    int requestedDay = Integer.parseInt(String.valueOf(plan.get("deadline_day")));
    int deadlineDay = Math.max(1, Math.min(requestedDay, month.lengthOfMonth()));
    Date deadline = Timestamp.valueOf(LocalDateTime.of(month.getYear(), month.getMonth(), deadlineDay, 18, 0));
    int generated = 0;
    for (Object roomIdValue : roomIds) {
      String roomId = String.valueOf(roomIdValue).trim();
      if (roomId.isEmpty()) {
        continue;
      }
      BizRoomopsTask task = new BizRoomopsTask();
      task.setBusinessType(String.valueOf(template.get("business_type")));
      task.setTaskTitle(plan.get("plan_name") + " - " + roomId);
      task.setTaskContent(String.valueOf(template.get("check_items_json")));
      task.setRoomId(roomId);
      task.setAssigneeUserid(text(plan.get("assignee_userid")));
      task.setAssigneeName(text(plan.get("assignee_name")));
      task.setDeadlineAt(deadline);
      task.setPriority("normal");
      taskService.createTask(task, operatorUserid, operatorName);
      generated++;
    }
    jdbcTemplate.update(
        "update biz_roomops_month_plan set status='GENERATED', generated_count=?, update_by=?, update_time=now() where id=?",
        generated, operatorName, planId);
    return generated;
  }

  @Transactional(rollbackFor = Exception.class)
  public void upsertIssueFromRecord(BizRoomopsRecord record) {
    String description = firstNonBlank(record.getSiteProblems(), record.getRemainingIssues(), record.getExceptionDesc());
    boolean abnormal = !blank(description)
        || !normal(record.getEnvironmentStatus())
        || !normal(record.getDeviceStatus());
    if (!abnormal) {
      return;
    }
    jdbcTemplate.update(
        """
        insert into biz_roomops_issue
          (id, issue_id, record_id, room_id, room_name, description, severity, status, reporter_name,
           create_time, update_time)
        values (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, now(), now())
        on duplicate key update room_id=values(room_id), room_name=values(room_name),
          description=values(description), severity=values(severity), update_time=now()
        """,
        id(), "ISSUE-" + record.getRecordId(), record.getRecordId(), record.getRoomId(), record.getRoomName(),
        defaultText(description, "环境或设备状态异常"), "normal", record.getInspectorName());
  }

  @Transactional(rollbackFor = Exception.class)
  public void updateIssue(JSONObject body, String operator) {
    String issueId = required(body.getString("issueId"), "问题编号");
    String status = defaultText(body.getString("status"), "PROCESSING");
    if (!List.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED", "REOPENED").contains(status)) {
      throw new IllegalArgumentException("不支持的问题状态：" + status);
    }
    jdbcTemplate.update(
        """
        update biz_roomops_issue set status=?, assignee_userid=?, assignee_name=?, deadline_at=?,
          rectification_result=?, resolved_at=case when ?='RESOLVED' then now() else resolved_at end,
          closed_at=case when ?='CLOSED' then now() else closed_at end, closed_by=case when ?='CLOSED' then ? else closed_by end,
          update_time=now() where issue_id=?
        """,
        status, body.getString("assigneeUserid"), body.getString("assigneeName"), body.getDate("deadlineAt"),
        body.getString("rectificationResult"), status, status, status, operator, issueId);
  }

  public Map<String, Object> monthlySummary(String monthValue) {
    String month = normalizeMonth(monthValue);
    LocalDate first = YearMonth.parse(month).atDay(1);
    LocalDate next = first.plusMonths(1);
    long total = count("select count(*) from biz_roomops_task where create_time >= ? and create_time < ?", first, next);
    long completed = count("select count(*) from biz_roomops_task where status='DONE' and create_time >= ? and create_time < ?", first, next);
    long overdue = count("select count(*) from biz_roomops_task where deadline_at < now() and status not in ('DONE','SUBMITTED') and create_time >= ? and create_time < ?", first, next);
    long issues = count("select count(*) from biz_roomops_issue where create_time >= ? and create_time < ?", first, next);
    long closed = count("select count(*) from biz_roomops_issue where status='CLOSED' and create_time >= ? and create_time < ?", first, next);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("month", month);
    result.put("totalTasks", total);
    result.put("completedTasks", completed);
    result.put("overdueTasks", overdue);
    result.put("completionRate", rate(completed, total));
    result.put("overdueRate", rate(overdue, total));
    result.put("totalIssues", issues);
    result.put("closedIssues", closed);
    result.put("issueCloseRate", rate(closed, issues));
    return result;
  }

  public byte[] monthlyCsv(String month) {
    Map<String, Object> summary = monthlySummary(month);
    StringBuilder csv = new StringBuilder("月份,任务总数,完成数,逾期数,完成率,逾期率,问题总数,已闭环问题,问题闭环率\n");
    csv.append(summary.get("month")).append(',').append(summary.get("totalTasks")).append(',')
        .append(summary.get("completedTasks")).append(',').append(summary.get("overdueTasks")).append(',')
        .append(summary.get("completionRate")).append(',').append(summary.get("overdueRate")).append(',')
        .append(summary.get("totalIssues")).append(',').append(summary.get("closedIssues")).append(',')
        .append(summary.get("issueCloseRate")).append('\n');
    return ("\ufeff" + csv).getBytes(StandardCharsets.UTF_8);
  }

  private long count(String sql, Object... args) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private double rate(long value, long total) {
    return total == 0 ? 0 : Math.round(value * 10000.0 / total) / 100.0;
  }

  private String normalizeMonth(String value) {
    String month = blank(value) ? YearMonth.now().toString() : value.trim();
    YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
    return month;
  }

  private String normalizeJsonArray(String value) {
    String json = defaultText(value, "[]");
    JSON.parseArray(json);
    return json;
  }

  private String required(String value, String field) {
    if (blank(value)) {
      throw new IllegalArgumentException(field + "不能为空");
    }
    return value.trim();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (!blank(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private boolean normal(String value) {
    return blank(value) || "正常".equals(value);
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String defaultText(String value, String fallback) {
    return blank(value) ? fallback : value.trim();
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String id() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
