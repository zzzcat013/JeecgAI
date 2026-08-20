# ai5g 前端目录

用于承载文档管理与内容检索相关的个性化前端页面与逻辑，严格限定在 `src/views/biz/ai5g` 下，避免改动公共模块。

## 建议结构
- `pages/` 具体页面（列表、详情、检索）
- `components/` 复用组件（上传、预览、过滤器）
- `api/` 本业务接口封装（基于 `defHttp`）
- `hooks/` 组合式函数（检索、分页、表单）
- `types/` TypeScript 类型定义

## 约定
- 请求统一使用 `defHttp`（`/src/utils/http/axios`）
- 路由按既有方式在上层配置，页面文件仅放置于此，不修改公共路由与系统文件

## 文档状态展示
- `DocumentManage.vue` 中 `success` 显示为“转MD成功”，`failed` 显示为“转MD失败”。
- `processing` 根据 `mineruTaskStatus` 显示“排队中/解析中/处理中”，并展示排队数和已耗时。
- 有 `processing` 记录时页面每 3 秒轮询一次，任务结束后自动停止轮询。

## 变更规则
- 原则上不得修改本目录以外的源代码；如需调整通用组件或样式，请复制到本目录下对应子目录再做改造，避免影响平台主干。
- 优先复用平台提供的组件与工具（`ant-design-vue`、`vxe-table`、消息与弹窗、国际化等），遵循现有编码与交互规范。

## 可复用清单（示例）
- UI：`ant-design-vue@4`（表单、Modal、Upload、Tabs 等）
- 表格：`vxe-table`（列表、分页、筛选）
- 请求：`defHttp` 封装（拦截器、错误处理、token）
- 工具：`dayjs`、`lodash-es`、`xe-utils`
