package org.jeecg.modules.biz.roomops.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTask;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/roomops/task")
public class BizRoomopsTaskController {
  @Autowired
  private IBizRoomopsTaskService bizRoomopsTaskService;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsTask entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsTask> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    applyListViewFilters(queryWrapper, req);
    queryWrapper.orderByDesc("create_time");
    IPage<BizRoomopsTask> pageList = bizRoomopsTaskService.page(new Page<>(pageNo, pageSize), queryWrapper);
    Date warningThreshold = Date.from(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(24)
        .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
    pageList.getRecords().forEach(task -> {
      boolean warning = task.getDeadlineAt() != null
          && task.getDeadlineAt().before(warningThreshold)
          && !"DONE".equals(task.getStatus())
          && !"SUBMITTED".equals(task.getStatus());
      task.setWarning(warning);
    });
    return Result.ok(pageList);
  }

  private void applyListViewFilters(QueryWrapper<BizRoomopsTask> queryWrapper, HttpServletRequest req) {
    String warningParam = req.getParameter("warning");
    if ("true".equalsIgnoreCase(warningParam) || "1".equals(warningParam)) {
      queryWrapper.and(w -> w.isNotNull("deadline_at")
          .le("deadline_at", Date.from(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(24)
              .atZone(ZoneId.of("Asia/Shanghai")).toInstant()))
          .in("status", "AVAILABLE", "ASSIGNED", "REOPENED"));
    }
    String mineParam = req.getParameter("mine");
    if ("true".equalsIgnoreCase(mineParam) || "1".equals(mineParam)) {
      String userId = currentUserId();
      String userName = currentUserName();
      boolean hasUserId = userId != null && !userId.isEmpty();
      boolean hasUserName = userName != null && !userName.isEmpty();
      if (!hasUserId && !hasUserName) {
        queryWrapper.apply("1 = 0");
      } else {
        queryWrapper.and(w -> {
          if (hasUserId) {
            w.and(inner -> inner.eq("assigner_userid", userId).or().eq("assignee_userid", userId));
          }
          if (hasUserName) {
            if (hasUserId) {
              w.or();
            }
            w.and(inner -> inner.eq("assigner_name", userName).or().eq("assignee_name", userName));
          }
        });
      }
    }
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = false) String id,
                             @RequestParam(name = "taskId", required = false) String taskId) {
    BizRoomopsTask task;
    if (id != null && !id.isEmpty()) {
      task = bizRoomopsTaskService.getById(id);
    } else if (taskId != null && !taskId.isEmpty()) {
      task = bizRoomopsTaskService.getOne(
          new QueryWrapper<BizRoomopsTask>().eq("task_id", taskId.trim()).last("limit 1"), false);
    } else {
      return Result.error("缺少 id 或 taskId");
    }
    if (task == null) {
      return Result.error("任务不存在");
    }
    task.setRounds(bizRoomopsTaskService.listRounds(task.getTaskId()));
    return Result.ok(task);
  }

  @PostMapping(value = "/add")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> add(@RequestBody JSONObject body) {
    JSONArray roomIds = body.getJSONArray("roomIds");
    if (roomIds != null && !roomIds.isEmpty()) {
      JSONArray candidateUserids = body.getJSONArray("candidateUserids");
      body.remove("roomIds");
      body.remove("candidateUserids");
      BizRoomopsTask entity = body.toJavaObject(BizRoomopsTask.class);
      List<String> ids = roomIds.toJavaList(String.class);
      List<String> candidates = candidateUserids == null
          ? Collections.emptyList()
          : candidateUserids.toJavaList(String.class);
      List<BizRoomopsTask> saved = bizRoomopsTaskService.createTasksBatch(
          entity, ids, candidates, currentUserId(), currentUserName());
      return Result.ok(saved);
    }
    BizRoomopsTask entity = body.toJavaObject(BizRoomopsTask.class);
    BizRoomopsTask saved = bizRoomopsTaskService.createTask(entity, currentUserId(), currentUserName());
    return Result.ok(saved);
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> edit(@RequestBody BizRoomopsTask entity) {
    return Result.ok(bizRoomopsTaskService.updateTask(entity, currentUserId(), currentUserName()));
  }

  @PostMapping(value = "/confirm")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> confirm(@RequestBody JSONObject body) {
    String taskId = body.getString("taskId");
    String remark = body.getString("remark");
    if (taskId == null || taskId.trim().isEmpty()) {
      return Result.error("缺少 taskId");
    }
    bizRoomopsTaskService.confirmTask(taskId.trim(), remark, currentUserId(), currentUserName());
    return Result.ok("确认完成");
  }

  @PostMapping(value = "/reject")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> reject(@RequestBody JSONObject body) {
    String taskId = body.getString("taskId");
    if (taskId == null || taskId.trim().isEmpty()) {
      return Result.error("缺少 taskId");
    }
    bizRoomopsTaskService.rejectTask(
        taskId.trim(),
        body.getString("remark"),
        body.getString("reassignUserid"),
        body.getString("reassignName"),
        Boolean.TRUE.equals(body.getBoolean("clearAssignee")),
        currentUserId(),
        currentUserName());
    return Result.ok("已驳回并重新下发");
  }

  @PostMapping(value = "/push")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> push(@RequestBody(required = false) JSONObject body) {
    String taskId = body == null ? "" : body.getString("taskId");
    try {
      if (taskId == null || taskId.trim().isEmpty()) {
        bizRoomopsTaskService.pushAllActive();
        return Result.ok("全部活动任务已下发到小程序前置服务");
      }
      bizRoomopsTaskService.pushTask(taskId.trim());
      return Result.ok("任务已下发到小程序前置服务");
    } catch (Exception e) {
      log.error("任务下发失败", e);
      return Result.error("任务下发失败：" + e.getMessage());
    }
  }

  @PostMapping(value = "/archive")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> archive(@RequestBody JSONObject body) {
    String taskId = body.getString("taskId");
    if (taskId == null || taskId.trim().isEmpty()) {
      return Result.error("缺少 taskId");
    }
    boolean archived = Boolean.TRUE.equals(body.getBoolean("archived"));
    bizRoomopsTaskService.archiveTask(taskId.trim(), archived, currentUserId(), currentUserName());
    return Result.ok(archived ? "任务已归档" : "任务已恢复");
  }

  @DeleteMapping(value = "/delete")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsTaskService.deleteTasks(Collections.singletonList(id));
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsTaskService.deleteTasks(Arrays.asList(ids.split(",")));
    return Result.ok("批量删除成功！");
  }

  private String currentUserId() {
    Object principal = getPrincipal();
    return principal instanceof LoginUser ? ((LoginUser) principal).getUsername() : "";
  }

  private String currentUserName() {
    Object principal = getPrincipal();
    if (!(principal instanceof LoginUser loginUser)) {
      return "";
    }
    return loginUser.getRealname() == null || loginUser.getRealname().isEmpty()
        ? loginUser.getUsername()
        : loginUser.getRealname();
  }

  private Object getPrincipal() {
    try {
      return SecurityUtils.getSubject().getPrincipal();
    } catch (Exception e) {
      return null;
    }
  }
}
