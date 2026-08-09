package org.jeecg.modules.biz.roomops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsDingtalkUser;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsDingtalkUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/roomops/dingtalkUser")
public class BizRoomopsDingtalkUserController {
  @Autowired
  private IBizRoomopsDingtalkUserService bizRoomopsDingtalkUserService;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsDingtalkUser entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsDingtalkUser> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("last_login_time", "create_time");
    IPage<BizRoomopsDingtalkUser> pageList = bizRoomopsDingtalkUserService.page(new Page<>(pageNo, pageSize), queryWrapper);
    return Result.ok(pageList);
  }

  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody BizRoomopsDingtalkUser entity) {
    bizRoomopsDingtalkUserService.save(entity);
    return Result.ok("添加成功！");
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:dingtalkUser:edit")
  public Result<?> edit(@RequestBody BizRoomopsDingtalkUser entity) {
    bizRoomopsDingtalkUserService.updateById(entity);
    return Result.ok("修改成功!");
  }

  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsDingtalkUserService.removeById(id);
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsDingtalkUserService.removeByIds(Arrays.asList(ids.split(",")));
    return Result.ok("批量删除成功！");
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    return Result.ok(bizRoomopsDingtalkUserService.getById(id));
  }

  @PostMapping(value = "/sync")
  public Result<?> syncFromDingtalk() {
    return Result.ok(bizRoomopsDingtalkUserService.syncFromDingtalk());
  }
}
