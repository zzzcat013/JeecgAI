# 前端 biz 视图约定

- 目录：`jeecgboot-vue3/src/views/biz`
- 职责：承载个性化业务页面与对应 API 封装，避免改动公共与系统页面。
- 请求封装：统一使用 `defHttp`（`src/utils/http/axios/index.ts`）。

## 对接示例
- 视图组件：`jeecgboot-vue3/src/views/biz/Hello.vue`
- API：`jeecgboot-vue3/src/views/biz/hello.api.ts`

