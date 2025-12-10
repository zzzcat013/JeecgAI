# HelloController 文档

## 路径与职责
- 文件：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java`
- 作用：提供示例接口用于联调与验证前后端通路。

## 接口列表
- `GET /hello/test`
  - 定义：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:26`
  - 返回：`Result<String>`，`result` 字段为 `"Test Hello World!"`
- `GET /hello/ok`
  - 定义：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:34`
  - 返回：纯文本 `"Hello World!"`

## 依赖
- `RedisUtil`：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:24`
- `Result`：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/HelloController.java:4`

