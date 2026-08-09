package org.jeecg.modules.biz.roomops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsSyncLog;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsSyncLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/roomops/syncLog")
public class BizRoomopsSyncLogController {
  @Autowired
  private IBizRoomopsSyncLogService bizRoomopsSyncLogService;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsSyncLog entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsSyncLog> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("started_at", "create_time");
    IPage<BizRoomopsSyncLog> pageList = bizRoomopsSyncLogService.page(new Page<>(pageNo, pageSize), queryWrapper);
    return Result.ok(pageList);
  }

  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody BizRoomopsSyncLog entity) {
    bizRoomopsSyncLogService.save(entity);
    return Result.ok("添加成功！");
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:syncLog:edit")
  public Result<?> edit(@RequestBody BizRoomopsSyncLog entity) {
    bizRoomopsSyncLogService.updateById(entity);
    return Result.ok("修改成功!");
  }

  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsSyncLogService.removeById(id);
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsSyncLogService.removeByIds(Arrays.asList(ids.split(",")));
    return Result.ok("批量删除成功！");
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    return Result.ok(bizRoomopsSyncLogService.getById(id));
  }
}
