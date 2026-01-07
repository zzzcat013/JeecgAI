# ai5g 后端目录

用于承载文档管理与内容检索相关的个性化后端代码，严格限定在 `org.jeecg.modules.biz.ai5g` 包下，避免改动平台主干代码。

## 建议结构
- `controller/` Web 接口层（REST 控制器）
- `service/` 业务服务层（接口与实现）
- `entity/` 数据实体（JPA/MyBatis-Plus）
- `mapper/` 持久层（MyBatis-Plus Mapper）
- `dto/vo/` 数据传输对象与视图对象
- `config/` 仅限本业务必要的局部配置

## 约定
- 返回统一使用平台 `Result<T>`
- 统一前缀路径建议：`/ai5g`（控制器上 `@RequestMapping("/ai5g")`）
- 依赖尽量复用平台组件（如权限、字典、文件存储、Redis、日志等）

## 变更规则
- 原则上不得修改本目录以外的源代码；如确需修改平台通用类，请复制到本目录下并以不影响原有模块的方式改造（包路径 `org.jeecg.modules.biz.ai5g.*`）。
- 复用平台提供的工具与配置，避免新增全局配置或切面，若必须使用，请限定作用域在 `ai5g` 业务内。

## 可复用清单（示例）
- 通用返回体：`org.jeecg.common.api.vo.Result`
- 缓存：`org.jeecg.common.util.RedisUtil`
- 日志与权限：平台已有切面与注解
