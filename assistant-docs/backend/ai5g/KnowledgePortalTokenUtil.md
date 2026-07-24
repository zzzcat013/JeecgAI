# KnowledgePortalTokenUtil

企业知识门户 Token 获取工具，用于按《网络AI平台大模型网关API Key获取知识门户 Token接口文档》生成加密请求，并调用知识门户 Token 接口换取访问令牌。

## 配置

配置位于 `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/application.yml`：

```yaml
knowledge-portal:
  base-url: http://10.186.2.176:10010/CUCCAI-new-graph/llm
  # Token接口完整地址；为空时默认使用 base-url + /external-auth/ai-api-key-token
  token-url:
  api-key: prod_xxx
  username: zhangxj200
  timeout: 30000
```

- `base-url`：知识门户业务接口基础地址，`EnterpriseKnowledgeClient` 会基于它调用 `/webApi/v1/...`。
- `token-url`：Token 接口完整地址。为空时，工具默认拼接 `base-url + /external-auth/ai-api-key-token`。
- `api-key`：网络AI平台大模型网关 API Key。
- `username`：用户 OA 账号。
- `timeout`：请求超时时间，单位毫秒。

注意：`base-url` 和 `token-url` 可能不在同一个网关上下文下。若默认拼接地址返回鉴权层错误或 404，应由知识门户服务方确认 Token 接口完整 URL，并显式配置 `token-url`。

## Token 接口

文档给出的相对路径：

```text
POST /external-auth/ai-api-key-token
Content-Type: application/json
```

请求体：

```json
{
  "apiKeyEnc": "AES加密并Base64编码后的内容",
  "currentDate": "2026-03-27 10:30:00",
  "iv": "16位随机字符串"
}
```

响应体：

```json
{
  "code": 200,
  "data": {
    "token": "8d1a8ca7-5cf0-4037-acbf-c01a44409d12"
  },
  "message": null
}
```

## 加密规则

待加密 JSON：

```json
{
  "apiKey": "knowledge-portal.api-key",
  "username": "knowledge-portal.username",
  "currentDate": "同请求体 currentDate",
  "iv": "同请求体 iv"
}
```

加密参数：

- 算法：`AES/CBC/PKCS5Padding`
- 动态密钥：`kg_ai_api_key_token_yyyy-MM-dd`
- 实际 AES key：动态密钥前 16 位
- IV：16 位随机字符串，字符范围 `A-Za-z0-9`
- 时区：`Asia/Shanghai`
- `currentDate` 格式：`yyyy-MM-dd HH:mm:ss`
- 请求时间与服务端时间差不能超过 10 分钟

## 关键方法

- `getAuthToken()`：从 Spring `Environment` 读取 `knowledge-portal.*` 配置并返回 `data.token`。
- `requestAuthToken()`：发起 Token 请求并返回完整 JSON 响应。
- `debugAuthToken()`：发起一次 Token 请求并返回脱敏调试信息。
- `buildCurlCommand()`：生成可直接执行的 curl 命令，不发请求。
- `buildTokenRequest(apiKey, username)`：生成加密后的 Token 请求体。

## 调试接口

`Ai5gDocumentController` 暴露了两个匿名调试接口：

```text
GET /ai5g/doc/debug/knowledge-portal-token
GET /ai5g/doc/debug/knowledge-portal-token-curl
```

第一个接口会实际请求 Token 服务并返回脱敏响应；第二个接口只生成 curl 命令，便于在服务器或本机直接验证。

## 当前排障记录

按当前默认配置拼出的地址为：

```text
http://10.186.2.176:10010/CUCCAI-new-graph/llm/external-auth/ai-api-key-token
```

实测该地址返回：

```json
{"code":500,"message":"未获取到Authorization"}
```

加 `Authorization: Bearer <apiKey>` 后返回：

```json
{"code":401,"message":"token认证失败"}
```

这说明该 URL 很可能进入了普通受保护业务网关，而不是文档描述的免鉴权 Token 接口。需要服务方确认 `/external-auth/ai-api-key-token` 的完整外部访问地址，然后配置到：

```yaml
knowledge-portal:
  token-url: http://真实地址/external-auth/ai-api-key-token
```

## 代码参考

- Token 工具：[KnowledgePortalTokenUtil.java](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/util/KnowledgePortalTokenUtil.java)
- 企业知识库客户端：[EnterpriseKnowledgeClient.java](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/knowledge/EnterpriseKnowledgeClient.java)
- 调试接口控制器：[Ai5gDocumentController.java](/Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/controller/Ai5gDocumentController.java)
