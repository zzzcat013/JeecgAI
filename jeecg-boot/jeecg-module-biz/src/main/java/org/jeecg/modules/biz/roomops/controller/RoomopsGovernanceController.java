package org.jeecg.modules.biz.roomops.controller;

import com.alibaba.fastjson.JSONObject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.biz.roomops.service.RoomopsGovernanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/roomops/governance")
public class RoomopsGovernanceController {
  @Autowired
  private RoomopsGovernanceService governanceService;

  @GetMapping("/templates")
  public Result<?> templates() {
    return Result.ok(governanceService.listTemplates());
  }

  @PostMapping("/template/save")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> saveTemplate(@RequestBody JSONObject body) {
    governanceService.saveTemplate(body, currentUserName());
    return Result.ok("模板已保存");
  }

  @GetMapping("/plans")
  public Result<?> plans(@RequestParam(required = false) String month) {
    return Result.ok(governanceService.listPlans(month));
  }

  @PostMapping("/plan/save")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> savePlan(@RequestBody JSONObject body) {
    governanceService.savePlan(body, currentUserName());
    return Result.ok("月度计划已保存");
  }

  @PostMapping("/plan/generate")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> generatePlan(@RequestBody JSONObject body) {
    int count = governanceService.generatePlan(body.getString("id"), currentUserId(), currentUserName());
    return Result.ok("已生成 " + count + " 条任务");
  }

  @GetMapping("/issues")
  public Result<?> issues(@RequestParam(required = false) String status) {
    return Result.ok(governanceService.listIssues(status));
  }

  @PostMapping("/issue/update")
  @RequiresPermissions("roomops:task:edit")
  public Result<?> updateIssue(@RequestBody JSONObject body) {
    governanceService.updateIssue(body, currentUserName());
    return Result.ok("问题状态已更新");
  }

  @GetMapping("/monthly")
  public Result<?> monthly(@RequestParam(required = false) String month) {
    return Result.ok(governanceService.monthlySummary(month));
  }

  @GetMapping("/monthly/export")
  public ResponseEntity<byte[]> export(@RequestParam(required = false) String month) {
    String filename = URLEncoder.encode("机房巡检月报-" + (month == null ? "当月" : month) + ".csv", StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(governanceService.monthlyCsv(month));
  }

  private String currentUserId() {
    Object principal = SecurityUtils.getSubject().getPrincipal();
    return principal instanceof LoginUser ? ((LoginUser) principal).getUsername() : "";
  }

  private String currentUserName() {
    Object principal = SecurityUtils.getSubject().getPrincipal();
    if (!(principal instanceof LoginUser user)) {
      return "";
    }
    return user.getRealname() == null || user.getRealname().isEmpty() ? user.getUsername() : user.getRealname();
  }
}
