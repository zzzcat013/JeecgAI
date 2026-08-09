package org.jeecg.modules.biz.roomops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsRecord;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsPhoto;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsPhotoService;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/roomops/record")
public class BizRoomopsRecordController {
  @Autowired
  private IBizRoomopsRecordService bizRoomopsRecordService;

  @Autowired
  private IBizRoomopsPhotoService bizRoomopsPhotoService;

  @GetMapping(value = "/list")
  public Result<?> queryPageList(BizRoomopsRecord entity,
                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                 HttpServletRequest req) {
    QueryWrapper<BizRoomopsRecord> queryWrapper = QueryGenerator.initQueryWrapper(entity, req.getParameterMap());
    queryWrapper.orderByDesc("submitted_at", "create_time");
    IPage<BizRoomopsRecord> pageList = bizRoomopsRecordService.page(new Page<>(pageNo, pageSize), queryWrapper);
    pageList.getRecords().forEach(record -> record.setPhotoCount(
        bizRoomopsPhotoService.count(new QueryWrapper<BizRoomopsPhoto>().eq("record_id", record.getRecordId()))));
    return Result.ok(pageList);
  }

  @PostMapping(value = "/add")
  public Result<?> add(@RequestBody BizRoomopsRecord entity) {
    bizRoomopsRecordService.save(entity);
    return Result.ok("添加成功！");
  }

  @PutMapping(value = "/edit")
  @RequiresPermissions("roomops:record:edit")
  public Result<?> edit(@RequestBody BizRoomopsRecord entity) {
    bizRoomopsRecordService.updateById(entity);
    return Result.ok("修改成功!");
  }

  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    bizRoomopsRecordService.removeById(id);
    return Result.ok("删除成功!");
  }

  @DeleteMapping(value = "/deleteBatch")
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    bizRoomopsRecordService.removeByIds(Arrays.asList(ids.split(",")));
    return Result.ok("批量删除成功！");
  }

  @GetMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    return Result.ok(bizRoomopsRecordService.getById(id));
  }

  @GetMapping(value = "/queryByRecordId")
  public Result<?> queryByRecordId(@RequestParam(name = "recordId", required = true) String recordId) {
    BizRoomopsRecord record = bizRoomopsRecordService.getOne(
        new QueryWrapper<BizRoomopsRecord>().eq("record_id", recordId.trim()).last("limit 1"), false);
    if (record == null) {
      return Result.error("业务记录不存在：" + recordId);
    }
    record.setPhotoCount(
        bizRoomopsPhotoService.count(new QueryWrapper<BizRoomopsPhoto>().eq("record_id", record.getRecordId())));
    return Result.ok(record);
  }
}
