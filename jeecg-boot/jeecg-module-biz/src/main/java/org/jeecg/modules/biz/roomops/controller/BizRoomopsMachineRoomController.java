package org.jeecg.modules.biz.roomops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsMachineRoom;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsMachineRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/roomops/machineRoom")
public class BizRoomopsMachineRoomController {
  @Autowired
  private IBizRoomopsMachineRoomService bizRoomopsMachineRoomService;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsMachineRoom entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsMachineRoom> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("create_time");
    IPage<BizRoomopsMachineRoom> pageList = bizRoomopsMachineRoomService.page(new Page<>(pageNo, pageSize), queryWrapper);
    return Result.ok(pageList);
  }

  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody BizRoomopsMachineRoom entity) {
    bizRoomopsMachineRoomService.save(entity);
    return Result.ok("添加成功！");
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:machineRoom:edit")
  public Result<?> edit(@RequestBody BizRoomopsMachineRoom entity) {
    bizRoomopsMachineRoomService.updateById(entity);
    return Result.ok("修改成功!");
  }

  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsMachineRoomService.removeById(id);
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsMachineRoomService.removeByIds(Arrays.asList(ids.split(",")));
    return Result.ok("批量删除成功！");
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    return Result.ok(bizRoomopsMachineRoomService.getById(id));
  }
}
