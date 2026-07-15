package org.jeecg.modules.biz.ai5g.controller;

import lombok.RequiredArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.biz.ai5g.mapper.TocPrivateNetworkQueryMapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ToC 随行专网数据查询（只读）。 */
@RestController
@RequestMapping("/ai5g/toc-private-network")
@RequiredArgsConstructor
public class TocPrivateNetworkQueryController {
    private static final int MAX_PAGE_SIZE = 200;
    private final TocPrivateNetworkQueryMapper queryMapper;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        List<Map<String, Object>> rows = queryMapper.selectSummary();
        return Result.OK(rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0));
    }

    @GetMapping("/projects")
    public Result<List<Map<String, Object>>> projects(@RequestParam(required=false) String keyword,
                                                       @RequestParam(required=false) String status) {
        return Result.OK(queryMapper.selectProjects(keyword, status));
    }

    @GetMapping("/resources")
    public Result<Map<String, Object>> resources(@RequestParam(required=false) String projectCode,
                                                  @RequestParam(required=false) String keyword,
                                                  @RequestParam(defaultValue="1") Integer pageNo,
                                                  @RequestParam(defaultValue="20") Integer pageSize) {
        PageRange page = pageRange(pageNo, pageSize);
        return Result.OK(pageResult(queryMapper.selectResources(projectCode, keyword, page.offset, page.pageSize),
                queryMapper.countResources(projectCode, keyword), page));
    }

    @GetMapping("/routes")
    public Result<Map<String, Object>> routes(@RequestParam(required=false) String projectCode,
                                               @RequestParam(required=false) String keyword,
                                               @RequestParam(defaultValue="1") Integer pageNo,
                                               @RequestParam(defaultValue="20") Integer pageSize) {
        PageRange page = pageRange(pageNo, pageSize);
        return Result.OK(pageResult(queryMapper.selectRoutes(projectCode, keyword, page.offset, page.pageSize),
                queryMapper.countRoutes(projectCode, keyword), page));
    }

    @GetMapping("/documents")
    public Result<Map<String, Object>> documents(@RequestParam(required=false) String projectCode,
                                                  @RequestParam(required=false) String keyword,
                                                  @RequestParam(defaultValue="1") Integer pageNo,
                                                  @RequestParam(defaultValue="20") Integer pageSize) {
        PageRange page = pageRange(pageNo, pageSize);
        return Result.OK(pageResult(queryMapper.selectDocuments(projectCode, keyword, page.offset, page.pageSize),
                queryMapper.countDocuments(projectCode, keyword), page));
    }

    @GetMapping("/guides")
    public Result<List<Map<String, Object>>> guides() { return Result.OK(queryMapper.selectGuides()); }

    @PutMapping("/projects/{projectCode}")
    public Result<Boolean> updateProject(@PathVariable String projectCode, @RequestBody Map<String, Object> body) {
        String status = stringValue(body.get("projectStatus"));
        if (status != null && !Set.of("待开通", "开通中", "已开通", "已停用").contains(status)) {
            return Result.error("无效的项目状态");
        }
        Integer expected = body.get("expectedUserCount") instanceof Number
                ? ((Number) body.get("expectedUserCount")).intValue() : null;
        int changed = queryMapper.updateProject(projectCode, status, stringValue(body.get("bandwidth")),
                stringValue(body.get("dnn")), stringValue(body.get("upfName")), expected,
                stringValue(body.get("requestedOpenDate")), stringValue(body.get("remark")));
        return changed > 0 ? Result.OK(true) : Result.error("项目不存在");
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value).trim(); }

    private PageRange pageRange(Integer no, Integer size) {
        int pageNo = no == null || no < 1 ? 1 : no;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        return new PageRange(pageNo, pageSize, (long)(pageNo - 1) * pageSize);
    }
    private Map<String,Object> pageResult(List<Map<String,Object>> records,long total,PageRange page) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("records",records); result.put("total",total); result.put("current",page.pageNo);
        result.put("size",page.pageSize); result.put("pages",total == 0 ? 0 : (total + page.pageSize - 1) / page.pageSize);
        return result;
    }
    private static class PageRange {
        final int pageNo; final int pageSize; final long offset;
        PageRange(int pageNo,int pageSize,long offset){this.pageNo=pageNo;this.pageSize=pageSize;this.offset=offset;}
    }
}
