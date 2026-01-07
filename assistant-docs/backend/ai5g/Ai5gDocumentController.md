# Ai5gDocumentController

- 路径前缀：`/ai5g/doc`
- 复用平台上传实现：`CommonUtils.upload` / `uploadLocal`，不改动主干代码
- 目录规则：`<documentDirectory>/<finalTypeCode>/`，类型代码即目录名

## 接口
- POST `/ai5g/doc/upload`
  - 入参：`file`(multipart)、`directoryName`、`typeCode1`、`typeCode2`、`typeCode3`、`title?`
  - 校验：`SsrfFileTypeFilter` + 业务允许类型（pdf/doc/docx/xlsx/xls/csv）
  - 返回：`BizDocument` 元数据，含 `storagePath/storageUrl`
- GET `/ai5g/doc/page`
  - 入参：`pageNo`、`pageSize`、`typeCode1/2/3?`、`title?`
  - 返回：分页 `BizDocument`
- GET `/ai5g/doc/get/{id}`
  - 返回：单条 `BizDocument`
- POST `/ai5g/doc/convert/{id}`
  - 说明：仅在本地上传模式、CSV 类型执行 Markdown 表格转换，生成 `.md` 文件并更新 `mdConverted/mdPath`

## 代码参考
- 控制器：[Ai5gDocumentController.java](file:///Users/zhangxj/Desktop/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/controller/Ai5gDocumentController.java)

