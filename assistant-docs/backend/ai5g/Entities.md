# 实体与表

## BizDocFile（表：biz_ai5g_docfile）
- 关键字段：`actualFileName`、`originalName`、`displayName`、`version`、`uploadTime`、`fileType`
- 类别与目录：`categoryPath`（含子目录结构），`fileYear`、`remark`
- 版本与状态：`latest`、`processStatus`
- 存储：`storagePath`、`storageUrl`、`contentType`、`size`
- Markdown：`mdConverted`、`mdPath`
- 审计：`createBy/createTime/updateBy/updateTime`

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
- 位置：[biz_tables.sql](file:///Users/zhangxj/Desktop/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/sql/biz_tables.sql)
