# Ai5gDocumentController

- 路径前缀：`/ai5g/doc`
- 普通文件上传复用平台上传实现：`CommonUtils.upload` / `uploadLocal`
- zip 文档包上传仅在 biz 包内实现，要求 `jeecg.uploadType=minio`
- 目录规则：`<documentDirectory>/<finalTypeCode>/`，类型代码即目录名

## 接口
- POST `/ai5g/doc/upload`
  - 入参：`file`(multipart)、`directoryName`、`typeCode1`、`typeCode2`、`typeCode3`、`title?`
  - 校验：`SsrfFileTypeFilter` + 业务允许类型
  - 返回：`BizDocFile` 元数据，含 `storagePath/storageFilename`
- POST `/ai5g/doc/import/zip`
  - 入参：`file`(zip multipart)、`directoryName`、`typeCode1`、`typeCode2`、`typeCode3`、`mainFile?`、`title?`、`fileYear?`、`remark?`
  - 约定：zip 内包含一个主 Markdown 文档和相对路径引用的图片资源，`mainFile` 可指定主文档；未指定时自动选取层级最浅的 `.md`
  - 存储：原始 zip、主 Markdown、图片资源全部上传到 MinIO；资源对象前缀写入 `assetRoot`
  - 关系：第一版不建子表，图片清单以 JSON 写入 `assetManifest`，原始包地址写入 `sourcePackagePath`
  - Markdown 图片：保存后将本地相对图片链接改写为 `#{domainURL}/ai5g/doc/assets/{docId}/{relativePath}`，预览时按当前请求临时展开为完整 URL
- GET `/ai5g/doc/page`
  - 入参：`pageNo`、`pageSize`、`typeCode1/2/3?`、`title?`
  - 返回：分页 `BizDocFile`
- GET `/ai5g/doc/get/{id}`
  - 返回：单条 `BizDocFile`
- POST `/ai5g/doc/convert/{id}`
  - 说明：异步执行 AI 转 Markdown，更新 `processStatus`
  - CSV：直接转换为 Markdown 表格
  - Office/PDF：Office 先通过 LibreOffice 预转 PDF，再优先走 MinerU 远程解析；远程失败后回退本地 MinerU
  - MinerU API 模式：提交任务时写入 `convertStartedAt/mineruTaskId/mineruTaskStatus`，轮询时同步 `mineruQueuedAhead/mineruStartedAt/mineruCompletedAt/mineruError`
  - 启动恢复：后端启动时会恢复 `processing` 且已有 `mineruTaskId` 的任务，拉取远程结果后继续保存
  - 结果上传：只上传主 Markdown 和 Markdown 引用的图片；MinerU 生成的 `_origin.pdf`、`_layout.pdf`、`_middle.json`、`content_list*.json`、`model.json` 等中间文件不上传 MinIO
  - Pandoc/Tika：作为兜底，仅保证文本转换，不保证图片提取
- GET `/ai5g/doc/preview/{id}`
  - 说明：返回文件预览；Office 在 MinIO 模式下会临时转 PDF 预览
- GET `/ai5g/doc/preview-md/{id}`
  - 说明：返回 Markdown 预览内容；MinIO 模式下读取 `mdPath` 对象，并将资源包图片链接临时改写为当前请求可访问的完整 URL
- GET `/ai5g/doc/assets/{id}/**`
  - 说明：根据 `BizDocFile.assetRoot` 读取 MinIO 中的包内资源，用于 Markdown 图片展示；该接口允许图片标签直接访问，但路径被限制在当前文档的资源包目录内
- GET `/ai5g/doc/debug/knowledge-portal-token`
  - 说明：匿名调试接口，调用企业知识门户 Token 接口并返回脱敏后的请求 URL、时间戳、业务状态码、响应体等信息
  - 配置：读取 `knowledge-portal.base-url/token-url/api-key/username/timeout`
- GET `/ai5g/doc/debug/knowledge-portal-token-curl`
  - 说明：匿名调试接口，只生成可执行 curl 命令，不发起请求；用于排查服务端网关路径、加密参数和网络连通性

## 知识库图片 URL 归一化
- 前端导入 AIRag 知识库时会读取 `/ai5g/doc/preview-md/{id}`，该接口返回的是预览友好的完整 URL。
- 为避免把 `localhost` 或某台服务器域名固化到 `airag_knowledge_doc.content`，biz 包内 `Ai5gKnowledgeDocContentAspect` 会在 `AiragKnowledgeDocServiceImpl.editDocument(...)` 保存前，将 ai5g 图片代理 URL 归一化为：
  - `#{domainURL}/ai5g/doc/assets/{docId}/{relativePath}`
- 聊天前端和知识库内容展示已有 `#{domainURL}` 替换逻辑，因此部署到其他域名时仍能正常显示图片。
- 归一化只处理包含 `/ai5g/doc/assets/` 的图片资源 URL，不处理普通外部图片。

## 当前边界
- 图片关系第一版保存在 `BizDocFile.assetRoot/assetManifest/sourcePackagePath`，未建图片子表；后续如需资源级权限、资源审计或单图管理，可迁移到子表。
- AI 应用最终回答是否展示图片，需要应用 Prompt 要求模型保留命中片段中的 Markdown 图片链接；后端保证图片入库、检索内容携带链接、资源可访问。
- MinerU 正常输出资源目录时可保留图片；Pandoc/Tika 兜底路径偏文本抽取，不承诺图片保留。
- 大文档图片清单可能超过 `TEXT` 上限，`assetManifest` 使用 `LONGTEXT`；失败备注使用 `TEXT`，避免长异常信息导致状态无法更新。
- 前端状态展示：`success` 显示为“转MD成功”，`failed` 显示为“转MD失败”，`processing` 显示排队/解析/耗时信息。

## 代码参考
- 控制器：[Ai5gDocumentController.java](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/controller/Ai5gDocumentController.java)
- 知识库内容归一化：[Ai5gKnowledgeDocContentAspect.java](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/aspect/Ai5gKnowledgeDocContentAspect.java)
- 企业知识门户 Token：[KnowledgePortalTokenUtil.md](/Users/zhangxj/source/java/jeecgAI/JeecgAI/assistant-docs/backend/ai5g/KnowledgePortalTokenUtil.md)
