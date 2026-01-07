package org.jeecg.modules.biz.ai5g.controller;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.biz.ai5g.entity.BizDocType;
import org.jeecg.modules.biz.ai5g.service.IBizDocTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Slf4j
@RestController
@RequestMapping("/ai5g/type")
public class Ai5gDocTypeController {

  @Autowired
  private IBizDocTypeService bizDocTypeService;

  @PostMapping("/save")
  public Result<?> save(@RequestBody BizDocType type) {
    if (type.getLevel() == null || type.getCode() == null) {
      return Result.error("level/code 不能为空");
    }
    int len = type.getCode().length();
    if ((type.getLevel() == 1 && len != 2) || (type.getLevel() == 2 && len != 4) || (type.getLevel() == 3 && len != 6)) {
      return Result.error("代码长度与层级不匹配: L" + type.getLevel());
    }
    if (type.getLevel() > 1) {
      if (type.getParentCode() == null) return Result.error("父级代码不能为空");
      int pLen = type.getLevel() == 2 ? 2 : 4;
      if (!type.getCode().substring(0, pLen).equals(type.getParentCode())) {
        return Result.error("父级代码与当前代码不匹配");
      }
    }
    // 唯一性校验
    QueryWrapper<BizDocType> qw = new QueryWrapper<>();
    qw.eq("code", type.getCode());
    if (bizDocTypeService.getOne(qw, false) != null) {
      return Result.error("类型代码已存在: " + type.getCode());
    }
    bizDocTypeService.save(type);
    return Result.OK(type);
  }

  @GetMapping("/list")
  public Result<?> list(@RequestParam(value = "level", required = false) Integer level,
                        @RequestParam(value = "parentCode", required = false) String parentCode) {
    QueryWrapper<BizDocType> qw = new QueryWrapper<>();
    if (level != null) qw.eq("level", level);
    if (parentCode != null && !parentCode.isEmpty()) qw.eq("parent_code", parentCode);
    qw.orderByAsc("level", "code");
    return Result.OK(bizDocTypeService.list(qw));
  }

  @PutMapping("/update")
  public Result<?> update(@RequestBody BizDocType type) {
    if (type.getId() == null) return Result.error("id 不能为空");
    // 限制更新范围：仅允许更新 name/status
    BizDocType origin = bizDocTypeService.getById(type.getId());
    if (origin == null) return Result.error("未找到类型");
    origin.setName(type.getName());
    origin.setStatus(type.getStatus());
    bizDocTypeService.updateById(origin);
    return Result.OK(origin);
  }

  @DeleteMapping("/remove/{id}")
  public Result<?> remove(@PathVariable("id") String id) {
    boolean ok = bizDocTypeService.removeById(id);
    return ok ? Result.OK(true) : Result.error("删除失败");
  }
}
