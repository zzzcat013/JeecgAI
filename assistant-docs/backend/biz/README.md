# jeecg-module-biz 约定

- 包路径：`org.jeecg.modules.biz`
- 职责：承载个性化业务功能的控制器、服务、实体、Mapper 等，避免修改核心模块，便于后续与上游合并升级。
- 访问前缀：按控制器 `@RequestMapping` 定义（示例：`/hello`）。
- 依赖：尽量复用平台提供的通用组件（如 `Result`、`RedisUtil`、`jeecg` 规范）。

## 示例
- 控制器：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java`
- 重要行：
  - `jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:20` 控制器路径定义 `/hello`
  - `jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:26` GET `/hello/test` 返回 `Result<String>`
  - `jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:34` GET `/hello/ok` 返回字符串

