# ai5g 后端目录

用于承载文档管理与内容检索相关的个性化后端代码，严格限定在 `org.jeecg.modules.biz.ai5g` 包下，避免改动平台主干代码。

## 建议结构
- `controller/` Web 接口层（REST 控制器）
- `service/` 业务服务层（接口与实现）
- `entity/` 数据实体（JPA/MyBatis-Plus）
- `mapper/` 持久层（MyBatis-Plus Mapper）
- `dto/vo/` 数据传输对象与视图对象
- `config/` 仅限本业务必要的局部配置
- `aspect/` 仅限 ai5g 业务的局部拦截逻辑

## 约定
- 返回统一使用平台 `Result<T>`
- 统一前缀路径建议：`/ai5g`（控制器上 `@RequestMapping("/ai5g")`）
- 依赖尽量复用平台组件（如权限、字典、文件存储、Redis、日志等）
- 文档 zip 包上传使用 MinIO 保存原始包、主 Markdown 和图片资源；Markdown 图片统一通过 `/ai5g/doc/assets/{docId}/...` 代理读取。
- Word/PDF 转 Markdown 时，Office 先通过 LibreOffice 预转 PDF，再优先走 MinerU 远程解析、失败后回退本地 MinerU；MinerU 输出目录按资源包模型保存主 Markdown 和图片。
- Pandoc/Tika 兜底仅保证文本，不保证提取并保存图片。
- 第一版不新增图片子表，文档与图片关系保存在 `biz_ai5g_docfile.asset_root`、`asset_manifest`、`source_package_path`。
- Markdown 预览接口会把 `#{domainURL}` 或相对图片路径临时改写为当前请求可访问的完整 URL；保存到 AIRag 知识库前会归一化为 `#{domainURL}/ai5g/doc/assets/{docId}/...`，避免把 `localhost` 或某台服务器域名固化进知识库。
- AI 应用回答是否展示图片，取决于应用提示词是否要求保留候选片段中的 Markdown 图片链接；后端负责保证图片链接可存储、可检索、可访问。

## 变更规则
- 原则上不得修改本目录以外的源代码；如确需修改平台通用类，请复制到本目录下并以不影响原有模块的方式改造（包路径 `org.jeecg.modules.biz.ai5g.*`）。
- 复用平台提供的工具与配置，避免新增全局配置或切面，若必须使用，请限定作用域在 `ai5g` 业务内。

## 可复用清单（示例）
- 通用返回体：`org.jeecg.common.api.vo.Result`
- 缓存：`org.jeecg.common.util.RedisUtil`
- 日志与权限：平台已有切面与注解
