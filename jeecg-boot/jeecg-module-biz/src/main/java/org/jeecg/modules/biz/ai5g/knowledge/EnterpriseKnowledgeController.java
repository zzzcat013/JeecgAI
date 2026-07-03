package org.jeecg.modules.biz.ai5g.knowledge;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业知识库读取接口.
 */
@Slf4j
@RestController
@RequestMapping("/ai5g/knowledge")
public class EnterpriseKnowledgeController {

    /**
     * 获取个人知识库列表.
     */
    @GetMapping("/person/bases")
    public Result<?> listPersonBases() {
        try {
            return Result.OK(EnterpriseKnowledgeClient.listPersonBases());
        } catch (Exception e) {
            log.error("获取个人知识库列表失败", e);
            return Result.error("获取个人知识库列表失败: " + e.getMessage());
        }
    }

    /**
     * 通过查询参数分页获取个人知识库文件列表.
     */
    @GetMapping("/person/files")
    public Result<?> listPersonFiles(@RequestParam("categoryId") String categoryId,
                                     @RequestParam(value = "categoryName", required = false) String categoryName,
                                     @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
                                     @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize) {
        try {
            return Result.OK(EnterpriseKnowledgeClient.listPersonFiles(categoryId, categoryName, pageNum, pageSize));
        } catch (Exception e) {
            log.error("获取个人知识库文件列表失败", e);
            return Result.error("获取个人知识库文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 通过请求体分页获取个人知识库文件列表, 支持文档中的全部过滤字段.
     */
    @PostMapping("/person/files")
    public Result<?> listPersonFiles(@RequestBody JSONObject body) {
        try {
            return Result.OK(EnterpriseKnowledgeClient.listPersonFiles(body));
        } catch (Exception e) {
            log.error("获取个人知识库文件列表失败", e);
            return Result.error("获取个人知识库文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 检索个人知识库内容.
     */
    @PostMapping("/person/retrieval")
    public Result<?> retrieve(@RequestBody JSONObject body) {
        try {
            return Result.OK(EnterpriseKnowledgeClient.retrieve(body));
        } catch (Exception e) {
            log.error("检索个人知识库内容失败", e);
            return Result.error("检索个人知识库内容失败: " + e.getMessage());
        }
    }
}
