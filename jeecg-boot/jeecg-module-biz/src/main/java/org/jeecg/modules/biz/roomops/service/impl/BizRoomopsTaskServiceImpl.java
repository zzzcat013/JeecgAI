package org.jeecg.modules.biz.roomops.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsMachineRoom;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTask;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTaskRound;
import org.jeecg.modules.biz.roomops.mapper.BizRoomopsTaskMapper;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsMachineRoomService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsTaskRoundService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BizRoomopsTaskServiceImpl extends ServiceImpl<BizRoomopsTaskMapper, BizRoomopsTask>
    implements IBizRoomopsTaskService {

  private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

  @Autowired
  private IBizRoomopsTaskRoundService taskRoundService;

  @Autowired
  private IBizRoomopsMachineRoomService machineRoomService;

  @Value("${jeecg.roomops.sync.vpsBaseUrl:}")
  private String vpsBaseUrl;

  @Value("${jeecg.roomops.sync.pullToken:}")
  private String pullToken;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public BizRoomopsTask createTask(BizRoomopsTask task, String operatorUserid, String operatorName) {
    Date now = new Date();
    fillRoomScope(task);
    if (blank(task.getTaskId())) {
      task.setTaskId(generateTaskId(task));
    } else {
      task.setTaskId(task.getTaskId().trim());
    }
    task.setBusinessType(defaultText(task.getBusinessType(), "inspection"));
    task.setStatus(defaultText(task.getStatus(), resolveInitialStatus(task)));
    task.setPriority(defaultText(task.getPriority(), "normal"));
    task.setRoundCount(defaultInt(task.getRoundCount(), 1));
    task.setAssignerUserid(defaultText(task.getAssignerUserid(), operatorUserid));
    task.setAssignerName(defaultText(task.getAssignerName(), operatorName));
    task.setCreateBy(defaultText(task.getCreateBy(), operatorName));
    task.setUpdateBy(operatorName);
    task.setCreateTime(now);
    task.setUpdateTime(now);
    save(task);
    addRound(task.getTaskId(), 1, "CREATE", "", task.getStatus(), operatorUserid, operatorName,
        "创建任务并下发");
    tryPushTask(task);
    return task;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public BizRoomopsTask updateTask(BizRoomopsTask task, String operatorUserid, String operatorName) {
    BizRoomopsTask existing = getByTaskId(task.getTaskId());
    if (existing == null) {
      throw new IllegalArgumentException("任务不存在：" + task.getTaskId());
    }
    String fromStatus = existing.getStatus();
    task.setId(existing.getId());
    task.setCreateTime(existing.getCreateTime());
    task.setCreateBy(existing.getCreateBy());
    task.setRoundCount(defaultInt(existing.getRoundCount(), 1));
    task.setUpdateTime(new Date());
    task.setUpdateBy(operatorName);
    task.setAssignerUserid(defaultText(task.getAssignerUserid(), existing.getAssignerUserid()));
    task.setAssignerName(defaultText(task.getAssignerName(), existing.getAssignerName()));
    task.setStatus(resolveStatusAfterEdit(existing, task));
    fillRoomScope(task);
    updateById(task);
    if (!fromStatus.equals(task.getStatus())) {
      addRound(task.getTaskId(), task.getRoundCount(), "REASSIGN", fromStatus, task.getStatus(),
          operatorUserid, operatorName, "任务信息变更或执行人调整");
    } else {
      addRound(task.getTaskId(), task.getRoundCount(), "UPDATE", fromStatus, task.getStatus(),
          operatorUserid, operatorName, "任务信息更新");
    }
    tryPushTask(task);
    return task;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void confirmTask(String taskId, String remark, String operatorUserid, String operatorName) {
    BizRoomopsTask task = getByTaskId(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在：" + taskId);
    }
    if (!"SUBMITTED".equals(task.getStatus())) {
      throw new IllegalArgumentException("仅已提交任务可以确认闭环，当前状态：" + task.getStatus());
    }
    String fromStatus = task.getStatus();
    task.setStatus("DONE");
    task.setConfirmRemark(remark);
    task.setConfirmBy(defaultText(operatorName, task.getConfirmBy()));
    task.setConfirmUserid(defaultText(operatorUserid, task.getConfirmUserid()));
    task.setConfirmedAt(new Date());
    task.setUpdateBy(operatorName);
    task.setUpdateTime(new Date());
    updateById(task);
    addRound(task.getTaskId(), task.getRoundCount(), "CONFIRM", fromStatus, "DONE",
        operatorUserid, operatorName, defaultText(remark, "确认任务完成"));
    tryPushTask(task);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void rejectTask(String taskId, String remark, String reassignUserid, String reassignName,
                         boolean clearAssignee, String operatorUserid, String operatorName) {
    BizRoomopsTask task = getByTaskId(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在：" + taskId);
    }
    if (!"SUBMITTED".equals(task.getStatus())) {
      throw new IllegalArgumentException("仅已提交任务可以驳回，当前状态：" + task.getStatus());
    }
    String fromStatus = task.getStatus();
    String toStatus = clearAssignee ? "AVAILABLE" : "REOPENED";
    task.setStatus(toStatus);
    task.setRejectRemark(remark);
    task.setUpdateBy(operatorName);
    task.setUpdateTime(new Date());
    if (clearAssignee) {
      task.setAssigneeUserid("");
      task.setAssigneeName("");
    } else if (!blank(reassignName) || !blank(reassignUserid)) {
      task.setAssigneeUserid(defaultText(reassignUserid, task.getAssigneeUserid()));
      task.setAssigneeName(defaultText(reassignName, task.getAssigneeName()));
    }
    updateById(task);
    String roundRemark = defaultText(remark, "任务被驳回，重新下发");
    if (clearAssignee) {
      roundRemark += "，改为待接单";
    } else if (!blank(reassignName)) {
      roundRemark += "，重新指定执行人：" + reassignName;
    }
    addRound(task.getTaskId(), task.getRoundCount(), "REJECT", fromStatus, toStatus,
        operatorUserid, operatorName, roundRemark);
    tryPushTask(task);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void archiveTask(String taskId, boolean archived, String operatorUserid, String operatorName) {
    BizRoomopsTask task = getByTaskId(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在：" + taskId);
    }
    String fromStatus = task.getStatus();
    task.setArchived(archived ? 1 : 0);
    task.setArchivedAt(archived ? new Date() : null);
    task.setArchivedBy(archived ? operatorName : null);
    task.setUpdateBy(operatorName);
    task.setUpdateTime(new Date());
    updateById(task);
    addRound(task.getTaskId(), task.getRoundCount(), archived ? "ARCHIVE" : "UNARCHIVE",
        fromStatus, fromStatus, operatorUserid, operatorName,
        archived ? "任务已归档" : "任务已恢复");
  }

  @Override
  public void pushTask(String taskId) throws Exception {
    BizRoomopsTask task = getByTaskId(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在：" + taskId);
    }
    pushTasksToVps(Collections.singletonList(task));
  }

  @Override
  public void pushAllActive() throws Exception {
    List<BizRoomopsTask> tasks = list(new QueryWrapper<BizRoomopsTask>()
        .in("status", "AVAILABLE", "ASSIGNED", "REOPENED")
        .orderByDesc("create_time"));
    if (tasks.isEmpty()) {
      return;
    }
    pushTasksToVps(tasks);
  }

  @Override
  public void pullTaskUpdatesFromVps() {
    if (blank(vpsBaseUrl) || blank(pullToken)) {
      return;
    }
    try {
      String base = vpsBaseUrl.endsWith("/") ? vpsBaseUrl.substring(0, vpsBaseUrl.length() - 1) : vpsBaseUrl;
      HttpURLConnection connection = (HttpURLConnection) new URL(base + "/api/tasks/changed?limit=200")
          .openConnection(java.net.Proxy.NO_PROXY);
      connection.setRequestMethod("GET");
      connection.setRequestProperty("X-Roomops-Pull-Token", pullToken);
      connection.setConnectTimeout(10000);
      connection.setReadTimeout(30000);
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) {
        log.warn("VPS任务变更拉取失败：{} {}", status, readResponse(connection));
        return;
      }
      String body = readBody(connection);
      JSONObject payload = JSON.parseObject(body);
      JSONArray tasks = payload.getJSONArray("tasks");
      if (tasks == null || tasks.isEmpty()) {
        return;
      }
      for (int i = 0; i < tasks.size(); i++) {
        JSONObject json = tasks.getJSONObject(i);
        BizRoomopsTask task = fromJson(json);
        upsertFromVps(task);
        importVpsRounds(task.getTaskId(), json.getJSONArray("rounds"));
      }
      log.info("VPS任务变更拉取完成，tasks={}", tasks.size());
    } catch (Exception e) {
      log.warn("VPS任务变更拉取异常", e);
    }
  }

  @Override
  public void markSubmitted(String recordId, String inspectorName, String inspectorUserid) {
    if (blank(recordId)) {
      return;
    }
    BizRoomopsTask task = getByTaskId(recordId);
    if (task == null || "DONE".equals(task.getStatus()) || "SUBMITTED".equals(task.getStatus())) {
      return;
    }
    String fromStatus = task.getStatus();
    task.setStatus("SUBMITTED");
    task.setSubmittedAt(new Date());
    task.setRecordId(recordId);
    if (blank(task.getAssigneeName())) {
      task.setAssigneeName(inspectorName);
    }
    if (blank(task.getAssigneeUserid())) {
      task.setAssigneeUserid(inspectorUserid);
    }
    task.setUpdateTime(new Date());
    updateById(task);
    addRound(task.getTaskId(), task.getRoundCount(), "SUBMIT", fromStatus, "SUBMITTED",
        inspectorUserid, inspectorName, "执行人已提交现场记录");
  }

  @Override
  public List<BizRoomopsTaskRound> listRounds(String taskId) {
    return taskRoundService.list(new QueryWrapper<BizRoomopsTaskRound>()
        .eq("task_id", taskId)
        .orderByAsc("round_no", "action_time"));
  }

  private String resolveInitialStatus(BizRoomopsTask task) {
    return blank(task.getAssigneeUserid()) && blank(task.getAssigneeName())
        ? "AVAILABLE"
        : "ASSIGNED";
  }

  private String resolveStatusAfterEdit(BizRoomopsTask existing, BizRoomopsTask task) {
    boolean hadAssignee = !blank(existing.getAssigneeUserid()) || !blank(existing.getAssigneeName());
    boolean hasAssignee = !blank(task.getAssigneeUserid()) || !blank(task.getAssigneeName());
    if ("AVAILABLE".equals(existing.getStatus()) && hasAssignee) {
      return "ASSIGNED";
    }
    if ("ASSIGNED".equals(existing.getStatus()) && !hasAssignee) {
      return "AVAILABLE";
    }
    if ("DONE".equals(existing.getStatus())) {
      return "DONE";
    }
    return defaultText(existing.getStatus(), "AVAILABLE");
  }

  private void fillRoomScope(BizRoomopsTask task) {
    if (blank(task.getRoomId())) {
      return;
    }
    BizRoomopsMachineRoom room = machineRoomService.getOne(
        new QueryWrapper<BizRoomopsMachineRoom>().eq("room_id", task.getRoomId().trim()).last("limit 1"), false);
    if (room == null) {
      return;
    }
    task.setRoomId(room.getRoomId());
    task.setRoomName(defaultText(task.getRoomName(), room.getRoomName()));
    task.setDomainCode(defaultText(task.getDomainCode(), room.getDomainCode()));
    task.setDomainShortCode(defaultText(task.getDomainShortCode(), room.getDomainShortCode()));
    task.setDomainName(defaultText(task.getDomainName(), room.getDomainName()));
    task.setRegionCode(defaultText(task.getRegionCode(), room.getRegionCode()));
    task.setRegionName(defaultText(task.getRegionName(), room.getRegionName()));
  }

  private void addRound(String taskId, Integer roundNo, String action, String fromStatus, String toStatus,
                        String operatorUserid, String operatorName, String remark) {
    BizRoomopsTaskRound round = new BizRoomopsTaskRound();
    round.setTaskId(taskId);
    round.setRoundNo(defaultInt(roundNo, 1));
    round.setAction(action);
    round.setFromStatus(fromStatus);
    round.setToStatus(toStatus);
    round.setOperatorUserid(operatorUserid);
    round.setOperatorName(operatorName);
    round.setRemark(remark);
    round.setActionTime(new Date());
    round.setCreateTime(new Date());
    taskRoundService.save(round);
  }

  private BizRoomopsTask getByTaskId(String taskId) {
    if (blank(taskId)) {
      return null;
    }
    return getOne(new QueryWrapper<BizRoomopsTask>().eq("task_id", taskId.trim()).last("limit 1"), false);
  }

  private void tryPushTask(BizRoomopsTask task) {
    try {
      pushTasksToVps(Collections.singletonList(task));
    } catch (Exception e) {
      log.warn("任务下发到 VPS 失败，可在任务分派页手动下发。taskId={}, error={}", task.getTaskId(), e.getMessage());
    }
  }

  private void pushTasksToVps(List<BizRoomopsTask> tasks) throws Exception {
    if (tasks.isEmpty()) {
      return;
    }
    if (blank(vpsBaseUrl) || blank(pullToken)) {
      log.warn("未配置 vpsBaseUrl/pullToken，任务不会下发到 VPS");
      return;
    }
    JSONArray array = new JSONArray();
    for (BizRoomopsTask task : tasks) {
      array.add(toJson(task));
    }
    JSONObject payload = new JSONObject();
    payload.put("tasks", array);
    byte[] body = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
    String base = vpsBaseUrl.endsWith("/") ? vpsBaseUrl.substring(0, vpsBaseUrl.length() - 1) : vpsBaseUrl;
    HttpURLConnection connection = (HttpURLConnection) new URL(base + "/api/tasks/push").openConnection(java.net.Proxy.NO_PROXY);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("X-Roomops-Pull-Token", pullToken);
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(30000);
    connection.setDoOutput(true);
    connection.getOutputStream().write(body);
    int status = connection.getResponseCode();
    if (status < 200 || status >= 300) {
      String message = readResponse(connection);
      throw new IllegalStateException("VPS任务下发失败：" + status + " " + message);
    }
    log.info("任务下发成功，taskIds={}", tasks.stream().map(BizRoomopsTask::getTaskId).collect(Collectors.toList()));
  }

  private BizRoomopsTask fromJson(JSONObject json) {
    BizRoomopsTask task = new BizRoomopsTask();
    task.setTaskId(text(json, "taskId", "task_id"));
    task.setBusinessType(text(json, "businessType", "business_type"));
    task.setTaskTitle(text(json, "taskTitle", "task_title"));
    task.setTaskContent(text(json, "taskContent", "task_content"));
    task.setDomainCode(text(json, "domainCode", "domain_code"));
    task.setDomainShortCode(text(json, "domainShortCode", "domain_short_code"));
    task.setDomainName(text(json, "domainName", "domain_name"));
    task.setRegionCode(text(json, "regionCode", "region_code"));
    task.setRegionName(text(json, "regionName", "region_name"));
    task.setRoomId(text(json, "roomId", "room_id"));
    task.setRoomName(text(json, "roomName", "room_name"));
    task.setAssignerUserid(text(json, "assignerUserid", "assigner_userid"));
    task.setAssignerName(text(json, "assignerName", "assigner_name"));
    task.setAssigneeUserid(text(json, "assigneeUserid", "assignee_userid"));
    task.setAssigneeName(text(json, "assigneeName", "assignee_name"));
    task.setStatus(text(json, "status"));
    task.setPriority(text(json, "priority"));
    task.setRoundCount(integer(json, "roundCount", "round_count"));
    task.setDeadlineAt(date(text(json, "deadlineAt", "deadline_at")));
    task.setAssignedAt(date(text(json, "assignedAt", "assigned_at")));
    task.setClaimedAt(date(text(json, "claimedAt", "claimed_at")));
    task.setSubmittedAt(date(text(json, "submittedAt", "submitted_at")));
    task.setConfirmedAt(date(text(json, "confirmedAt", "confirmed_at")));
    task.setRejectRemark(text(json, "rejectRemark", "reject_remark"));
    task.setConfirmRemark(text(json, "confirmRemark", "confirm_remark"));
    task.setConfirmBy(text(json, "confirmBy", "confirm_by"));
    task.setConfirmUserid(text(json, "confirmUserid", "confirm_userid"));
    task.setRecordId(text(json, "recordId", "record_id"));
    task.setProjectId(text(json, "projectId", "project_id"));
    return task;
  }

  private void upsertFromVps(BizRoomopsTask incoming) {
    BizRoomopsTask existing = getByTaskId(incoming.getTaskId());
    if (existing == null) {
      incoming.setStatus(defaultText(incoming.getStatus(), "AVAILABLE"));
      incoming.setPriority(defaultText(incoming.getPriority(), "normal"));
      incoming.setRoundCount(defaultInt(incoming.getRoundCount(), 1));
      incoming.setCreateTime(new Date());
      incoming.setUpdateTime(new Date());
      save(incoming);
      return;
    }
    incoming.setId(existing.getId());
    incoming.setCreateTime(existing.getCreateTime());
    incoming.setCreateBy(existing.getCreateBy());
    incoming.setAssignerUserid(defaultText(incoming.getAssignerUserid(), existing.getAssignerUserid()));
    incoming.setAssignerName(defaultText(incoming.getAssignerName(), existing.getAssignerName()));
    incoming.setConfirmRemark(defaultText(incoming.getConfirmRemark(), existing.getConfirmRemark()));
    incoming.setConfirmBy(defaultText(incoming.getConfirmBy(), existing.getConfirmBy()));
    incoming.setConfirmUserid(defaultText(incoming.getConfirmUserid(), existing.getConfirmUserid()));
    incoming.setConfirmedAt(defaultDate(incoming.getConfirmedAt(), existing.getConfirmedAt()));
    incoming.setRejectRemark(defaultText(incoming.getRejectRemark(), existing.getRejectRemark()));
    incoming.setRecordId(defaultText(incoming.getRecordId(), existing.getRecordId()));
    incoming.setProjectId(defaultText(incoming.getProjectId(), existing.getProjectId()));
    incoming.setUpdateTime(new Date());
    updateById(incoming);
  }

  private void importVpsRounds(String taskId, JSONArray rounds) {
    if (rounds == null || rounds.isEmpty()) {
      return;
    }
    for (int i = 0; i < rounds.size(); i++) {
      JSONObject roundJson = rounds.getJSONObject(i);
      String action = defaultText(text(roundJson, "action"), "UPDATE");
      Date actionTime = date(text(roundJson, "actionTime"));
      if (actionTime == null) {
        continue;
      }
      long count = taskRoundService.count(new QueryWrapper<BizRoomopsTaskRound>()
          .eq("task_id", taskId)
          .eq("action", action)
          .eq("action_time", actionTime));
      if (count > 0) {
        continue;
      }
      BizRoomopsTaskRound round = new BizRoomopsTaskRound();
      round.setTaskId(taskId);
      round.setRoundNo(integer(roundJson, "roundNo", "round_no"));
      round.setAction(action);
      round.setFromStatus(text(roundJson, "fromStatus", "from_status"));
      round.setToStatus(text(roundJson, "toStatus", "to_status"));
      round.setOperatorUserid(text(roundJson, "operatorUserid", "operator_userid"));
      round.setOperatorName(text(roundJson, "operatorName", "operator_name"));
      round.setRemark(text(roundJson, "remark"));
      round.setActionTime(actionTime);
      round.setCreateTime(new Date());
      taskRoundService.save(round);
    }
  }

  private JSONObject toJson(BizRoomopsTask task) {
    JSONObject json = new JSONObject();
    json.put("taskId", task.getTaskId());
    json.put("businessType", task.getBusinessType());
    json.put("taskTitle", task.getTaskTitle());
    json.put("taskContent", task.getTaskContent());
    json.put("domainCode", task.getDomainCode());
    json.put("domainShortCode", task.getDomainShortCode());
    json.put("domainName", task.getDomainName());
    json.put("regionCode", task.getRegionCode());
    json.put("regionName", task.getRegionName());
    json.put("roomId", task.getRoomId());
    json.put("roomName", task.getRoomName());
    json.put("assignerUserid", task.getAssignerUserid());
    json.put("assignerName", task.getAssignerName());
    json.put("assigneeUserid", task.getAssigneeUserid());
    json.put("assigneeName", task.getAssigneeName());
    json.put("status", task.getStatus());
    json.put("priority", task.getPriority());
    json.put("roundCount", task.getRoundCount());
    json.put("deadlineAt", formatDateTime(task.getDeadlineAt()));
    json.put("assignedAt", formatDateTime(task.getAssignedAt()));
    json.put("claimedAt", formatDateTime(task.getClaimedAt()));
    json.put("submittedAt", formatDateTime(task.getSubmittedAt()));
    json.put("confirmedAt", formatDateTime(task.getConfirmedAt()));
    json.put("rejectRemark", task.getRejectRemark());
    json.put("confirmRemark", task.getConfirmRemark());
    json.put("confirmBy", task.getConfirmBy());
    json.put("confirmUserid", task.getConfirmUserid());
    json.put("recordId", task.getRecordId());
    json.put("projectId", task.getProjectId());
    return json;
  }

  private String generateTaskId(BizRoomopsTask task) {
    String prefix = businessTypePrefix(defaultText(task.getBusinessType(), "inspection"));
    String domainShort = defaultText(task.getDomainShortCode(), "CORE");
    String room = blank(task.getRoomId()) ? "ROOM" : task.getRoomId().trim().replaceAll("[^A-Za-z0-9_-]", "_");
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
    return prefix + "-" + domainShort + "-" + room + "-" + random;
  }

  private String businessTypePrefix(String businessType) {
    if ("fault".equals(businessType)) {
      return "FR";
    }
    if ("engineering".equals(businessType)) {
      return "ER";
    }
    return "IR";
  }

  private String readResponse(HttpURLConnection connection) {
    try (InputStream inputStream = connection.getErrorStream();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      if (inputStream != null) {
        inputStream.transferTo(outputStream);
      }
      return outputStream.toString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "";
    }
  }

  private String readBody(HttpURLConnection connection) {
    try (InputStream inputStream = connection.getInputStream();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      inputStream.transferTo(outputStream);
      return outputStream.toString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "";
    }
  }

  private String text(JSONObject json, String... keys) {
    for (String key : keys) {
      Object value = json.get(key);
      if (value != null && !String.valueOf(value).trim().isEmpty()) {
        return String.valueOf(value).trim();
      }
    }
    return "";
  }

  private Integer integer(JSONObject json, String... keys) {
    String value = text(json, keys);
    if (value.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Date date(String value) {
    if (blank(value)) {
      return null;
    }
    String normalized = value.trim().replace("T", " ");
    try {
      return Date.from(LocalDateTime.parse(normalized,
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(SHANGHAI_ZONE).toInstant());
    } catch (Exception e) {
      return null;
    }
  }

  private Date defaultDate(Date value, Date fallback) {
    return value == null ? fallback : value;
  }

  private String formatDateTime(Date date) {
    if (date == null) {
      return "";
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return date.toInstant().atZone(SHANGHAI_ZONE).format(formatter);
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String defaultText(String value, String fallback) {
    return blank(value) ? fallback : value.trim();
  }

  private Integer defaultInt(Integer value, Integer fallback) {
    return value == null ? fallback : value;
  }
}
