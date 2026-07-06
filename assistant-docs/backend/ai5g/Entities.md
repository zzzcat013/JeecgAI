# 实体与表

## BizDocFile（表：biz_ai5g_docfile）
- 关键字段：`actualFileName`、`originalName`、`displayName`、`version`、`uploadTime`、`fileType`
- 类别与目录：`categoryPath`（含子目录结构），`fileYear`、`remark`
- 版本与状态：`latest`、`processStatus`
- 存储：`storagePath`、`storageFilename`、`contentType`、`size`
- Markdown：`mdConverted`、`mdPath`
- zip 资源包：`assetRoot`、`assetManifest`、`sourcePackagePath`
  - `assetRoot`：MinIO 对象前缀，例如 `doc/01/04/01/packages/{uuid}/`
  - `assetManifest`：包内图片资源清单 JSON，包含相对路径、对象名、类型、大小；第一版用于表达主文档和图片的关系，后续可迁移为子表
  - `sourcePackagePath`：原始 zip 包或原始上传文件在 MinIO 上的访问路径
- 审计：`createBy/createTime/updateBy/updateTime`

## 资源包关系模型
- 主 Markdown 与图片不建子表，关系由 `assetRoot + assetManifest + Markdown 图片链接` 三者共同表达。
- MinIO 中保存完整资源包目录，Markdown 图片统一通过后端代理读取：
  - 预览返回：完整后端 URL，如 `http://host/jeecg-boot/ai5g/doc/assets/{docId}/images/a.jpg`
  - 知识库保存：占位符 URL，如 `#{domainURL}/ai5g/doc/assets/{docId}/images/a.jpg`
- `#{domainURL}` 由前端按当前环境替换，避免知识库中固化 `localhost` 或某台服务器域名。

## BizDocType（表：biz_ai5g_doctype）
- 关键字段：`level`(1/2/3)、`code`、`name`、`parentCode`、`status`
- 分类编码建议：目录名使用纯数字编码（如 `01`、`0101`、`010101`），中文名称单独存储在 `name` 字段；若需将中文纳入目录名，可调整 `code` 为组合形式，但建议保持简洁与跨平台可用性。
- 示例种子数据位置：`ai5g/sql/biz_ai5g_doctype_seed.sql`

### 编码与名称映射示例
- `0101` → `规范`
- `0102` → `办公操作`
- `0103` → `业务办理`
- `0104` → `项目资料`
- `0105` → `案例`
- `0106` → `QA`
- `010101` → `专网办理规范`（父级：`0101`）
- `010102` → `运维手册`（父级：`0101`）

## SQL DDL
- 位置：[biz_tables.sql](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/sql/biz_tables.sql)
- MySQL DDL：[biz_tables_mysql.sql](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/sql/biz_tables_mysql.sql)
