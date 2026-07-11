package org.jeecg.modules.biz.ai5g.controller;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.biz.ai5g.entity.BizDocFile;
import org.jeecg.modules.biz.ai5g.entity.BizDocType;
import org.jeecg.modules.biz.ai5g.service.IBizDocFileService;
import org.jeecg.modules.biz.ai5g.service.IBizDocTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Slf4j
@RestController
@RequestMapping("/ai5g/type")
public class Ai5gDocTypeController {

  @Autowired
  private IBizDocTypeService bizDocTypeService;

  @Autowired
  private IBizDocFileService bizDocFileService;

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

  @PostMapping("/move")
  @Transactional(rollbackFor = Exception.class)
  public Result<?> move(@RequestBody MoveTypeReq req) {
    if (req == null || req.getId() == null || req.getTargetParentCode() == null || req.getTargetParentCode().isEmpty()) {
      return Result.error("id/targetParentCode 不能为空");
    }
    BizDocType origin = bizDocTypeService.getById(req.getId());
    if (origin == null) {
      return Result.error("未找到类型");
    }
    if (origin.getLevel() == null || origin.getLevel() < 2) {
      return Result.error("一级类型无需移动");
    }
    int targetParentLevel = origin.getLevel() - 1;
    BizDocType targetParent = findByCodeAndLevel(req.getTargetParentCode(), targetParentLevel);
    if (targetParent == null) {
      return Result.error("目标上级类型不存在或层级不匹配");
    }
    if (req.getTargetParentCode().equals(origin.getParentCode())) {
      return Result.OK(origin);
    }

    String oldCode = origin.getCode();
    String newCode = buildNextSiblingCode(req.getTargetParentCode(), origin.getLevel(), oldCode);
    if (oldCode.equals(newCode)) {
      return Result.OK(origin);
    }

    java.util.List<BizDocType> subtree = bizDocTypeService.list(
        new QueryWrapper<BizDocType>().likeRight("code", oldCode).orderByAsc("length(code)").orderByAsc("code"));
    if (subtree == null || subtree.isEmpty()) {
      subtree = new java.util.ArrayList<>();
      subtree.add(origin);
    }

    java.util.Map<String, String> codeMap = new java.util.LinkedHashMap<>();
    codeMap.put(oldCode, newCode);
    for (BizDocType item : subtree) {
      if (oldCode.equals(item.getCode())) {
        continue;
      }
      String suffix = item.getCode().substring(oldCode.length());
      codeMap.put(item.getCode(), newCode + suffix);
    }

    ensureNoCodeConflict(codeMap, oldCode);

    for (BizDocType item : subtree) {
      String mappedCode = codeMap.get(item.getCode());
      if (mappedCode == null) {
        continue;
      }
      boolean isRoot = oldCode.equals(item.getCode());
      item.setCode(mappedCode);
      if (isRoot) {
        item.setParentCode(req.getTargetParentCode());
      } else {
        String mappedParent = codeMap.get(item.getParentCode());
        if (mappedParent != null) {
          item.setParentCode(mappedParent);
        }
      }
      bizDocTypeService.updateById(item);
    }

    rewriteDocCategoryPath(codeMap, oldCode);
    return Result.OK(true);
  }

  @DeleteMapping("/remove/{id}")
  public Result<?> remove(@PathVariable("id") String id) {
    boolean ok = bizDocTypeService.removeById(id);
    return ok ? Result.OK(true) : Result.error("删除失败");
  }

  private BizDocType findByCodeAndLevel(String code, int level) {
    QueryWrapper<BizDocType> qw = new QueryWrapper<>();
    qw.eq("code", code).eq("level", level);
    return bizDocTypeService.getOne(qw, false);
  }

  private String buildNextSiblingCode(String parentCode, Integer level, String currentCode) {
    int codeLen = level == 2 ? 4 : 6;
    QueryWrapper<BizDocType> qw = new QueryWrapper<>();
    qw.eq("level", level).likeRight("code", parentCode).select("code");
    java.util.List<BizDocType> siblings = bizDocTypeService.list(qw);
    int max = 0;
    if (siblings != null) {
      for (BizDocType sibling : siblings) {
        if (sibling == null || sibling.getCode() == null) {
          continue;
        }
        if (sibling.getCode().equals(currentCode)) {
          continue;
        }
        String suffix = sibling.getCode().substring(parentCode.length());
        try {
          max = Math.max(max, Integer.parseInt(suffix));
        } catch (Exception ignored) {
        }
      }
    }
    return parentCode + String.format("%0" + (codeLen - parentCode.length()) + "d", max + 1);
  }

  private void ensureNoCodeConflict(java.util.Map<String, String> codeMap, String oldCode) {
    java.util.Collection<String> newCodes = codeMap.values();
    if (newCodes.isEmpty()) {
      return;
    }
    QueryWrapper<BizDocType> qw = new QueryWrapper<>();
    qw.in("code", newCodes).ne("code", oldCode);
    java.util.List<BizDocType> conflicts = bizDocTypeService.list(qw);
    if (conflicts != null && !conflicts.isEmpty()) {
      throw new IllegalArgumentException("目标分类下已存在相同编码，请先调整目标父类的子节点");
    }
  }

  private void rewriteDocCategoryPath(java.util.Map<String, String> codeMap, String oldCode) {
    if (codeMap == null || codeMap.isEmpty()) {
      return;
    }
    String oldPathPrefix = buildPathPrefix(oldCode);
    QueryWrapper<BizDocFile> qw = new QueryWrapper<>();
    qw.likeRight("category_path", oldPathPrefix);
    java.util.List<BizDocFile> docs = bizDocFileService.list(qw);
    if (docs == null || docs.isEmpty()) {
      return;
    }
    for (BizDocFile doc : docs) {
      if (doc == null || doc.getCategoryPath() == null) {
        continue;
      }
      String[] parts = doc.getCategoryPath().split("/");
      boolean changed = false;
      for (int i = 0; i < parts.length; i++) {
        String mapped = codeMap.get(parts[i]);
        if (mapped != null) {
          parts[i] = mapped;
          changed = true;
        }
      }
      if (changed) {
        doc.setCategoryPath(String.join("/", parts));
        bizDocFileService.updateById(doc);
      }
    }
  }

  private String buildPathPrefix(String code) {
    if (code == null || code.isEmpty()) {
      return code;
    }
    if (code.length() == 2) {
      return code;
    }
    if (code.length() == 4) {
      return code.substring(0, 2) + "/" + code;
    }
    if (code.length() == 6) {
      return code.substring(0, 2) + "/" + code.substring(0, 4) + "/" + code;
    }
    return code;
  }

  public static class MoveTypeReq {
    private String id;
    private String targetParentCode;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getTargetParentCode() {
      return targetParentCode;
    }

    public void setTargetParentCode(String targetParentCode) {
      this.targetParentCode = targetParentCode;
    }
  }
}
