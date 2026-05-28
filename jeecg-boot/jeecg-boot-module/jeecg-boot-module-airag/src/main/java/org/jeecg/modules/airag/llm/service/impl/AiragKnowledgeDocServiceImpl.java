package org.jeecg.modules.airag.llm.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.config.mqtoken.UserTokenContext;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.*;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragKnowledge;
import org.jeecg.modules.airag.llm.entity.AiragKnowledgeDoc;
import org.jeecg.modules.airag.llm.handler.EmbeddingHandler;
import org.jeecg.modules.airag.llm.mapper.AiragKnowledgeDocMapper;
import org.jeecg.modules.airag.llm.mapper.AiragKnowledgeMapper;
import org.jeecg.modules.airag.llm.service.IAiragKnowledgeDocService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jeecg.modules.airag.llm.util.AiragZipUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;

import static org.jeecg.modules.airag.llm.consts.LLMConsts.*;

/**
 * @Description: airag知识库文档
 * @Author: jeecg-boot
 * @Date: 2025-02-18
 * @Version: V1.0
 */
@Slf4j
@Service
public class AiragKnowledgeDocServiceImpl extends ServiceImpl<AiragKnowledgeDocMapper, AiragKnowledgeDoc> implements IAiragKnowledgeDocService {

    @Autowired
    private AiragKnowledgeDocMapper airagKnowledgeDocMapper;

    @Autowired
    private AiragKnowledgeMapper airagKnowledgeMapper;

    @Autowired
    EmbeddingHandler embeddingHandler;


    @Value(value = "${jeecg.path.upload:}")
    private String uploadpath;

    /**
     * 支持的文档类型
     */
    private static final List<String> SUPPORT_DOC_TYPE = Arrays.asList("txt", "pdf", "docx", "doc", "pptx", "ppt", "xlsx", "xls", "md");

    /**
     * 向量化线程池大小
     */
    private static final int THREAD_POOL_SIZE = 10;

    /**
     * 向量化文档线程池
     */
    private static final ExecutorService buildDocExecutorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    // 解压文件:单个文件最大150MB
    private static final long MAX_FILE_SIZE = 150 * 1024 * 1024;
    // 解压文件:总解压大小1024MB
    private static final long MAX_TOTAL_SIZE = 1024 * 1024 * 1024;
    // 解压文件:最多解压10000个Entry
    private static final int MAX_ENTRY_COUNT = 10000;

    @Transactional(rollbackFor = {Exception.class})
    @Override
    public Result<?> editDocument(AiragKnowledgeDoc airagKnowledgeDoc) {
        AssertUtils.assertNotEmpty("文档不能未空", airagKnowledgeDoc);
        AssertUtils.assertNotEmpty("知识库不能未空", airagKnowledgeDoc.getKnowledgeId());
        AssertUtils.assertNotEmpty("文档标题不能未空", airagKnowledgeDoc.getTitle());
        AssertUtils.assertNotEmpty("文档类型不能未空", airagKnowledgeDoc.getType());
        if (KNOWLEDGE_DOC_TYPE_TEXT.equals(airagKnowledgeDoc.getType())) {
            AssertUtils.assertNotEmpty("文档内容不能为空", airagKnowledgeDoc.getContent());
        }

        airagKnowledgeDoc.setStatus(KNOWLEDGE_DOC_STATUS_DRAFT);
        // 保存到数据库
        if (this.saveOrUpdate(airagKnowledgeDoc)) {
            // 重建向量
            return this.rebuildDocument(airagKnowledgeDoc.getId());
        } else {
            return Result.error("保存失败");
        }
    }

    @Override
    public Result<?> rebuildDocumentByKnowId(String knowId) {
        AssertUtils.assertNotEmpty("知识库id不能为空", knowId);
        List<AiragKnowledgeDoc> docList = airagKnowledgeDocMapper.selectList(Wrappers.lambdaQuery(AiragKnowledgeDoc.class).eq(AiragKnowledgeDoc::getKnowledgeId, knowId));
        if (oConvertUtils.isObjectEmpty(docList)) {
            return Result.OK();
        }
        String docIds = docList.stream().map(AiragKnowledgeDoc::getId).collect(Collectors.joining(","));
        return rebuildDocument(docIds);
    }

    @Transactional(rollbackFor = {java.lang.Exception.class})
    @Override
    public Result<?> rebuildDocument(String docIds) {
        AssertUtils.assertNotEmpty("请选择要重建的文档", docIds);
        List<String> docIdList = Arrays.asList(docIds.split(","));
        // 查询数据
        List<AiragKnowledgeDoc> docList = airagKnowledgeDocMapper.selectBatchIds(docIdList);
        AssertUtils.assertNotEmpty("文档不存在", docList);

        // 检查状态
        List<AiragKnowledgeDoc> knowledgeDocs = docList.stream()
                .filter(doc -> {
                    //update-begin---author:chenrui ---date:20250410  for：[QQYUN-11943]【ai】ai知识库 上传完文档 一直显示构建中？------------
                    if(KNOWLEDGE_DOC_STATUS_BUILDING.equalsIgnoreCase(doc.getStatus())){
                        Date updateTime = doc.getUpdateTime();
                        if (updateTime != null) {
                            // 向量化超过了5分钟,重新向量化
                            long timeDifference = System.currentTimeMillis() - updateTime.getTime();
                            return timeDifference > 5 * 60 * 1000;
                        }else{
                            return true;
                        }
                    } else {
                        return true;
                    }
                    //update-end---author:chenrui ---date:20250410  for：[QQYUN-11943]【ai】ai知识库 上传完文档 一直显示构建中？------------
                })
                .peek(doc -> {
                    doc.setStatus(KNOWLEDGE_DOC_STATUS_BUILDING);
                })
                .collect(Collectors.toList());
        if (oConvertUtils.isObjectEmpty(knowledgeDocs)) {
            return Result.ok("操作成功");
        }
        if (oConvertUtils.isObjectEmpty(knowledgeDocs)) {
            return Result.ok("操作成功");
        }
        // 更新状态
        this.updateBatchById(knowledgeDocs);
        // 异步重建文档
        String tenantId = TenantContext.getTenant();
        String token = TokenUtils.getTokenByRequest();
        knowledgeDocs.forEach((doc) -> {
            CompletableFuture.runAsync(() -> {
                UserTokenContext.setToken(token);
                TenantContext.setTenant(tenantId);
                String knowId = doc.getKnowledgeId();
                log.info("开始重建文档, 知识库id: {}, 文档id: {}", knowId, doc.getId());
                doc.setStatus(KNOWLEDGE_DOC_STATUS_BUILDING);
                this.updateById(doc);
                //update-begin---author:chenrui ---date:20250410  for：[QQYUN-11943]【ai】ai知识库 上传完文档 一直显示构建中？------------
                try {
                    Map<String, Object> metadata = embeddingHandler.embeddingDocument(knowId, doc);
                    // 更新数据 date:2025/2/18
                    if (null != metadata) {
                        if (Boolean.TRUE.equals(metadata.get("waitConfirm"))) {
                            doc.setStatus(KNOWLEDGE_DOC_STATUS_WAIT_CONFIRM);
                            this.updateById(doc);
                            log.info("重建文档发现图片，进入等待确认状态, 知识库id: {}, 文档id: {}", knowId, doc.getId());
                        } else {
                            doc.setStatus(KNOWLEDGE_DOC_STATUS_COMPLETE);
                            this.updateById(doc);
                            log.info("重建文档成功, 知识库id: {}, 文档id: {}", knowId, doc.getId());
                        }
                    } else {
                        this.handleDocBuildFailed(doc, "向量化失败");
                        log.info("重建文档失败, 知识库id: {}, 文档id: {}", knowId, doc.getId());
                    }
                }catch (Throwable t){
                    this.handleDocBuildFailed(doc, t.getMessage());
                    log.error("重建文档失败:" + t.getMessage() + ", 知识库id: " + knowId + ", 文档id: " + doc.getId(), t);
                }
                //update-end---author:chenrui ---date:20250410  for：[QQYUN-11943]【ai】ai知识库 上传完文档 一直显示构建中？------------
            }, buildDocExecutorService);
        });
        log.info("返回操作成功");
        return Result.ok("操作成功");
    }

    /**
     * 处理文档构建失败
     */
    private void handleDocBuildFailed(AiragKnowledgeDoc doc, String failedReason) {
        doc.setStatus(KNOWLEDGE_DOC_STATUS_FAILED);

        String metadataStr = doc.getMetadata();
        JSONObject metadata;
        if (oConvertUtils.isEmpty(metadataStr)) {
            metadata = new JSONObject();
        } else {
            metadata = JSONObject.parseObject(metadataStr);
        }
        metadata.put("failedReason", failedReason);
        doc.setMetadata(metadata.toJSONString());

        this.updateById(doc);
    }

    @Override
    public Result<?> removeByKnowIds(List<String> knowIds) {
        AssertUtils.assertNotEmpty("选择知识库", knowIds);
        for (String knowId : knowIds) {
            AiragKnowledge airagKnowledge = airagKnowledgeMapper.selectById(knowId);
            AssertUtils.assertNotEmpty("知识库不存在", airagKnowledge);
            AssertUtils.assertNotEmpty("请先为知识库配置向量模型库", airagKnowledge.getEmbedId());
            // 异步删除向量数据
            final String embedId = airagKnowledge.getEmbedId();
            final String finalKnowId = knowId;
            CompletableFuture.runAsync(() -> {
                try {
                    embeddingHandler.deleteEmbedDocsByKnowId(finalKnowId, embedId);
                } catch (Throwable ignore) {
                }
            });
            // 删除数据
            airagKnowledgeDocMapper.deleteByMainId(knowId);
        }
        return Result.OK();
    }

    @Override
    public Result<?> removeDocByIds(List<String> docIds) {
        AssertUtils.assertNotEmpty("请选择要删除的文档", docIds);
        // 查询数据
        List<AiragKnowledgeDoc> docList = airagKnowledgeDocMapper.selectBatchIds(docIds);
        AssertUtils.assertNotEmpty("文档不存在", docList);
        // 整理数据
        Map<String, List<String>> knowledgeDocs = docList.stream().collect(Collectors.groupingBy(
                AiragKnowledgeDoc::getKnowledgeId,
                Collectors.mapping(AiragKnowledgeDoc::getId, Collectors.toList())
        ));
        if (oConvertUtils.isObjectEmpty(knowledgeDocs)) {
            return Result.ok("success");
        }
        knowledgeDocs.forEach((knowId, groupedDocIds) -> {
            AiragKnowledge airagKnowledge = airagKnowledgeMapper.selectById(knowId);
            AssertUtils.assertNotEmpty("知识库不存在", airagKnowledge);
            AssertUtils.assertNotEmpty("请先为知识库配置向量模型库", airagKnowledge.getEmbedId());
            // 异步删除向量数据
            final String embedId = airagKnowledge.getEmbedId();
            final List<String> docIdsToDelete = new ArrayList<>(groupedDocIds);
            CompletableFuture.runAsync(() -> {
                try {
                    embeddingHandler.deleteEmbedDocsByDocIds(docIdsToDelete, embedId);
                } catch (Throwable ignore) {
                }
            });
            // 删除数据
            airagKnowledgeDocMapper.deleteBatchIds(groupedDocIds);
        });
        return Result.ok("success");
    }

    @Override
    public Result<?> deleteAllByKnowId(String knowId) {
        if (oConvertUtils.isEmpty(knowId)) {
            return Result.error("知识库id不能为空");
        }
        LambdaQueryWrapper<AiragKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiragKnowledgeDoc::getKnowledgeId, knowId);
        //noinspection unchecked
        wrapper.select(AiragKnowledgeDoc::getId);
        List<AiragKnowledgeDoc> docList = airagKnowledgeDocMapper.selectList(wrapper);
        if (docList.isEmpty()) {
            return Result.ok("暂无文档");
        }
        List<String> docIds = docList.stream().map(AiragKnowledgeDoc::getId).collect(Collectors.toList());
        this.removeDocByIds(docIds);
        return Result.ok("清空完成");
    }

    @Transactional(rollbackFor = {java.lang.Exception.class})
    @Override
    public Result<?> packageAndImportFromLocal(String knowId, String localPath, String originDocId) {
        log.info("执行本地打包导入: knowId={}, localPath={}, originDocId={}", knowId, localPath, originDocId);
        AssertUtils.assertNotEmpty("请先选择知识库", knowId);
        AssertUtils.assertNotEmpty("请输入本地目录路径", localPath);
        
        File localDir = new File(localPath);
        if (!localDir.exists() || !localDir.isDirectory()) {
            return Result.error("本地目录不存在或不是文件夹: " + localPath);
        }

        String tempZipPath = uploadpath + File.separator + "tmp" + File.separator + "pkg_" + System.currentTimeMillis() + ".zip";
        File tempZipFile = new File(tempZipPath);
        tempZipFile.getParentFile().mkdirs();

        try {
            File imagesRoot = new File(uploadpath + File.separator + "temp" + File.separator + "images");
            if (imagesRoot.exists() && imagesRoot.isDirectory()) {
                File targetAigc = new File(localPath + File.separator + "aigc");
                FileUtils.forceMkdir(targetAigc);
                Collection<File> imgs = FileUtils.listFiles(imagesRoot, new String[]{"png","jpg","jpeg","gif","svg","bmp","webp"}, false);
                for (File img : imgs) {
                    FileUtils.copyFileToDirectory(img, targetAigc);
                }
            }
            // 1. 打包目录
            boolean success = AiragZipUtils.packageToKnowledgeZip(localPath, tempZipPath);
            if (!success) {
                return Result.error("打包本地目录失败");
            }

            // 2. 导入 ZIP
            Result<?> result = importDocumentFromZipFile(knowId, tempZipFile);
            
            // 3. 如果是从特定文档触发的，且导入成功，则删除原文档
            if (result.isSuccess() && oConvertUtils.isNotEmpty(originDocId)) {
                log.info("本地打包导入成功，准备删除原文档: {}", originDocId);
                boolean removed = this.removeById(originDocId);
                log.info("原文档删除结果: {}, id={}", removed, originDocId);
            }
            
            return result;
        } catch (Exception e) {
            log.error("本地打包导入失败", e);
            return Result.error("操作失败: " + e.getMessage());
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete();
            }
        }
    }

    @Transactional(rollbackFor = {java.lang.Exception.class})
    @Override
    public Result<?> importDocumentFromZip(String knowId, MultipartFile zipFile) {
        AssertUtils.assertNotEmpty("请先选择知识库", knowId);
        AssertUtils.assertNotEmpty("请上传文件", zipFile);
        
        String bizPath = knowId + File.separator + UUIDGenerator.generate();
        try {
            String uploadedZipPath = CommonUtils.uploadLocal(zipFile, bizPath, uploadpath);
            File file = new File(uploadpath + File.separator + uploadedZipPath);
            return importDocumentFromZipFile(knowId, file);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 从本地 ZIP 文件导入文档库
     */
    private Result<?> importDocumentFromZipFile(String knowId, File zipFile) {
        long startTime = System.currentTimeMillis();
        log.info("开始导入文档库(zip), 知识库id: {}, 文件名: {}", knowId, zipFile.getName());

        try {
            String shortKnow = knowId != null && knowId.length() > 8 ? knowId.substring(knowId.length() - 8) : knowId;
            String shortId = UUIDGenerator.generate().replaceAll("[^a-zA-Z0-9]", "").substring(0, 8);
            String bizPath = "kb" + File.separator + shortKnow + File.separator + shortId;
            
            // 处理 uploadpath 是相对路径的情况
            String tempUploadPath = uploadpath;
            if (!new File(uploadpath).isAbsolute()) {
                // 1. 获取当前 user.dir (即命令启动目录)
                // 在您的场景下，user.dir 就是 /Users/zhangxj/Desktop/source/java/jeecgAI/JeecgAI/jeecg-boot
                String userDir = System.getProperty("user.dir");
                tempUploadPath = new File(userDir, uploadpath).getAbsolutePath();
                
                // 2. 移除之前画蛇添足的 "jeecg-module-system/jeecg-system-start" 判断逻辑
                // 因为既然您在 jeecg-boot 根目录启动，CommonController 看到的 user.dir 也是 jeecg-boot 根目录
                // 所以它也会去 jeecg-boot/uploads 下找文件
                // 我们只需要简单直接地拼在 user.dir 下即可
            }
            final String absoluteUploadPath = tempUploadPath;
            
            // 确保 workDir 是基于 uploadpath 的绝对路径
            String workDir = new File(absoluteUploadPath, bizPath).getAbsolutePath() + File.separator;
            
            // 确保目录存在！否则解压可能失败或解压到别处
            new File(workDir).mkdirs();
            
            String sourcesPath = workDir + "files";

            // 通过filePath 检查文件是不是压缩包(zip)
            String zipFileName = FilenameUtils.getBaseName(zipFile.getName());
            String fileExt = FilenameUtils.getExtension(zipFile.getName());
            if (null == fileExt || !fileExt.equalsIgnoreCase("zip")) {
                throw new JeecgBootException("请上传zip压缩包");
            }

            //update-begin---wangshuai---date:20260414  for：【QQYUN-14932】创建知识库时，可以创建一个分段策略，知识库里面的文档默认使用知识库的分段策略------------
            // 判断知识库是否配置了默认分段策略
            boolean knowledgeHasDefaultSegment = false;
            AiragKnowledge knowledge = airagKnowledgeMapper.selectById(knowId);
            if (knowledge != null && oConvertUtils.isNotEmpty(knowledge.getMetadata())) {
                try {
                    JSONObject kmeta = JSONObject.parseObject(knowledge.getMetadata());
                    knowledgeHasDefaultSegment = Boolean.TRUE.equals(kmeta.getBoolean(LLMConsts.ENABLE_SEGMENT));
                } catch (Exception ignore) {}
            }
            final boolean useKnowledgeDefault = knowledgeHasDefaultSegment;
            //update-end---author:wangshuai ---date:20260414  for：【QQYUN-14932】创建知识库时，可以创建一个分段策略，知识库里面的文档默认使用知识库的分段策略------------
            
            // 解压缩文件
            List<AiragKnowledgeDoc> docList = new ArrayList<>();
            // 传入 sourcesPath 给 lambda 表达式使用
            final String finalSourcesPath = sourcesPath;
            
            unzipFile(zipFile.getAbsolutePath(), sourcesPath, uploadedFile -> {
                String fileName = uploadedFile.getName();
                if (!SUPPORT_DOC_TYPE.contains(FilenameUtils.getExtension(fileName).toLowerCase())) {
                    return;
                }
                String baseName = FilenameUtils.getBaseName(fileName);
                AiragKnowledgeDoc doc = new AiragKnowledgeDoc();
                doc.setKnowledgeId(knowId);
                doc.setTitle(baseName);
                doc.setType(LLMConsts.KNOWLEDGE_DOC_TYPE_FILE);
                doc.setStatus(LLMConsts.KNOWLEDGE_DOC_STATUS_DRAFT);

                // 计算相对路径：bizPath + /files/ + 相对文件路径
                // 先计算文件在 sourcesPath 下的相对路径
                // 使用绝对路径进行计算，避免相对路径的歧义
                String absUploadedFile = uploadedFile.getAbsolutePath();
                String absSourcesPath = new File(finalSourcesPath).getAbsolutePath();
                
                String relativeInSource = "";
                if (absUploadedFile.startsWith(absSourcesPath)) {
                    relativeInSource = absUploadedFile.substring(absSourcesPath.length());
                } else {
                    // Fallback: 如果路径不匹配，尝试直接用文件名
                    relativeInSource = File.separator + fileName;
                }
                
                // 确保以 / 开头
                if (!relativeInSource.startsWith(File.separator)) {
                    relativeInSource = File.separator + relativeInSource;
                }
                
                // 拼接完整相对路径：knowId/uuid/files/doc.md
                // 注意：数据库存储的路径是相对于 uploadpath 的
                // 1. 如果 uploadpath 是绝对路径，那么 fullRelativePath = workDir(abs) - uploadpath(abs) + relativeInSource
                // 2. 如果 uploadpath 是相对路径(uploads)，那么 CommonController 找文件是去 {user.dir}/uploads/xxx
                //    所以数据库里应该存 xxx，即相对于 {user.dir}/uploads 的路径
                
                String fullRelativePath = bizPath + File.separator + "files" + relativeInSource;
                
                // 确保不以 / 开头（CommonController 拼接时是用 uploadpath + File.separator + imgPath）
                // 如果 imgPath 以 / 开头，File.separator 也是 /，就会变成 //，虽然大多数系统兼容，但最好去掉
                if (fullRelativePath.startsWith(File.separator)) {
                    fullRelativePath = fullRelativePath.substring(1);
                }
                
                // 统一转为 Unix 风格路径，因为数据库通常存 /
                fullRelativePath = fullRelativePath.replace("\\", "/").replaceAll("//+", "/");
                
                // 同样的逻辑处理 sourcesPath 的相对路径记录
                String relativeSourcePath = bizPath + File.separator + "files";
                relativeSourcePath = relativeSourcePath.replace("\\", "/").replaceAll("//+", "/");

                JSONObject metadata = new JSONObject();
                metadata.put(LLMConsts.KNOWLEDGE_DOC_METADATA_FILEPATH, fullRelativePath);
                metadata.put(LLMConsts.KNOWLEDGE_DOC_METADATA_SOURCES_PATH, sourcesPath);
                //update-begin---wangshuai---date:20260414  for：【QQYUN-14932】创建知识库时，可以创建一个分段策略，知识库里面的文档默认使用知识库的分段策略------------
                // 知识库有默认分段策略，文档标记使用知识库默认
                if (useKnowledgeDefault) {
                    metadata.put(LLMConsts.USE_KNOWLEDGE_DEFAULT, true);
                }
                //update-end---wangshuai---date:20260414  for：【QQYUN-14932】创建知识库时，可以创建一个分段策略，知识库里面的文档默认使用知识库的分段策略------------
                doc.setMetadata(metadata.toJSONString());
                docList.add(doc);
            });
            
            AssertUtils.assertNotEmpty("压缩包中没有符合要求的文档", docList);
            this.saveBatch(docList);
            
            String docIds = docList.stream().map(AiragKnowledgeDoc::getId).filter(oConvertUtils::isObjectNotEmpty).collect(Collectors.joining(","));
            rebuildDocument(docIds);
            
            log.info("导入文档库(zip)成功, 耗时: {}ms", (System.currentTimeMillis() - startTime));
            return Result.ok("导入成功");
        } catch (Exception e) {
            log.error("ZIP 导入解析失败", e);
            throw new JeecgBootException("ZIP 导入解析失败: " + e.getMessage());
        }
    }

    /**
     * 解压缩文件
     *
     * @param zipFilePath 压缩文件路径
     * @param destDir    目标文件夹
     * @param afterExtract 解压完成后回调
     * @throws IOException
     * @author chenrui
     * @date 2025/3/20 14:37
     */
    public static void unzipFile(String zipFilePath, String destDir, Consumer<File> afterExtract) throws IOException {
        unzipFile(Paths.get(zipFilePath), Paths.get(destDir), afterExtract);
    }


    /**
     * 解压缩文件
     *
     * @param zipFilePath  压缩文件路径
     * @param targetDir    目标文件夹
     * @param afterExtract 解压完成后回调
     * @throws IOException
     * @author chenrui
     * @date 2025/4/28 17:02
     */
    private static void unzipFile(Path zipFilePath, Path targetDir, Consumer<File> afterExtract) throws IOException {
        long totalUnzippedSize = 0;
        int entryCount = 0;

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("解压文件数量超限，可能是zip bomb攻击");
                }

                //update-begin---author:scott ---date:2026-04-16  for：【issues/9551】macOS压缩包隐藏文件过滤-----------
                if (shouldSkipZipEntry(entry.getName())) {
                    log.info("跳过压缩包中的隐藏文件: {}", entry.getName());
                    continue;
                }
                //update-end---author:scott ---date:2026-04-16  for：【issues/9551】macOS压缩包隐藏文件过滤-----------

                Path newPath = safeResolve(targetDir, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry);
                         OutputStream os = Files.newOutputStream(newPath)) {

                        long bytesCopied = copyLimited(is, os, MAX_FILE_SIZE);
                        totalUnzippedSize += bytesCopied;

                        if (totalUnzippedSize > MAX_TOTAL_SIZE) {
                            throw new IOException("解压总大小超限，可能是zip bomb攻击");
                        }
                    }

                    // 解压完成后回调
                    if (afterExtract != null) {
                        afterExtract.accept(newPath.toFile());
                    }
                }
            }
        }
    }

    //update-begin---author:scott ---date:2026-04-16  for：【issues/9551】macOS压缩包隐藏文件过滤-----------
    /**
     * 过滤压缩包中的系统隐藏文件，例如 macOS 自动生成的 __MACOSX 和 ._ 文件。
     */
    static boolean shouldSkipZipEntry(String entryName) {
        if (oConvertUtils.isEmpty(entryName)) {
            return true;
        }
        String normalizedName = entryName.replace("\\", "/");
        if (normalizedName.startsWith("__MACOSX/")) {
            return true;
        }
        String fileName = Paths.get(normalizedName).getFileName().toString();
        return fileName.startsWith("._") || fileName.equals(".DS_Store");
    }
    //update-end---author:scott ---date:2026-04-16  for：【issues/9551】macOS压缩包隐藏文件过滤-----------

    /**
     * 安全解析路径，防止Zip Slip攻击
     *
     * @param targetDir
     * @param entryName
     * @return
     * @throws IOException
     * @author chenrui
     * @date 2025/4/28 16:46
     */
    private static Path safeResolve(Path targetDir, String entryName) throws IOException {
        Path resolvedPath = targetDir.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(targetDir)) {
            throw new IOException("ZIP 路径穿越攻击被阻止:" + entryName);
        }
        return resolvedPath;
    }

    /**
     * 复制输入流到输出流，并限制最大字节数
     *
     * @param in
     * @param out
     * @param maxBytes
     * @return
     * @throws IOException
     * @author chenrui
     * @date 2025/4/28 17:03
     */
    private static long copyLimited(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long totalCopied = 0;
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            totalCopied += bytesRead;
            if (totalCopied > maxBytes) {
                throw new IOException("单个文件解压超限，可能是zip bomb攻击");
            }
            out.write(buffer, 0, bytesRead);
        }
        return totalCopied;
    }

    @Override
    public Result<?> confirmSingleImport(String docId) {
        AssertUtils.assertNotEmpty("文档id不能为空", docId);
        AiragKnowledgeDoc doc = this.getById(docId);
        AssertUtils.assertNotEmpty("文档不存在", doc);

        String metadataStr = doc.getMetadata();
        JSONObject metadata = oConvertUtils.isEmpty(metadataStr) ? new JSONObject() : JSONObject.parseObject(metadataStr);
        metadata.put("skipImageCheck", true);
        doc.setMetadata(metadata.toJSONString());
        doc.setStatus(KNOWLEDGE_DOC_STATUS_DRAFT);
        this.updateById(doc);

        return this.rebuildDocument(docId);
    }
}
