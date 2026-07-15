# ToC 随行专网数据查询模块说明

## 1. 模块概述

ToC 随行专网数据查询模块用于统一管理和检索随行专网项目资料，包括项目基本信息、DNN/APN、ServiceID、GRE/IPRAN/MAR 等网络资源、UPF 终端地址池、路由配置以及申请表和网管反馈内容。

模块同时支持两类 AI 查询：

- 数据库插件 / Chat2BI：用于项目统计、精确字段查询、资源关联和图表生成。
- AI 知识库 / RAG：用于申请表、配置说明和网管反馈的语义检索。

截至 2026-07-15，数据库中有 5 个随行专网项目、38 条网络资源、16 条地址池与路由、235 条文档检索片段。

## 2. 页面入口

菜单路径：

```text
文档管理模块 → 随行专网查询
```

直接访问地址：

```text
/ai5g/toc-private-network
```

如果菜单不可见，在“系统管理 → 角色管理 → 角色授权”中，为对应角色勾选“文档管理模块 → 随行专网查询”，保存后重新登录。

## 3. 页面功能

### 3.1 项目总览

展示项目名称、客户、DNN/APN、ServiceID、带宽、UPF、预计用户数、网络资源数量、要求开通日期和项目状态。

点击“编辑”可修改：

- 项目状态：待开通、开通中、已开通、已停用；
- 要求开通日期；
- 带宽；
- 预计用户数；
- DNN/APN；
- UPF；
- 备注。

### 3.2 网络资源

用于查询 GRE 内外层地址、IPRAN ASG、智能城域网 MAR、VPN、VLAN、Trunk 和电路等资源。可以按项目筛选，也可以输入 IP、资源名称或类型搜索。

### 3.3 地址池与路由

用于查询每个项目在不同 UPF 下的终端地址池、客户内网网段、静态路由、下一跳和 VPN/VRF。

当前导入规则支持：

- CIDR，例如 `172.22.0.0/17`；
- 起止地址，例如 `10.12.0.1-10.12.255.254`；
- 起止地址加掩码，例如 `10.189.247.1-10.189.247.254 255.255.255.0`；
- 同一单元格中的多个 UPF 地址池自动拆分为多条记录。

### 3.4 文档检索

用于查询申请表、网管反馈、GRE 参数和资源反馈中的文本内容。搜索关键词只作用于当前页签；切换页签时会清除关键词，但保留项目筛选。

文档检索数据不用于项目数量、资源数量等统计。

## 4. 数据模型

- `biz_5g_toc_project`：项目主表，一行代表一个随行专网项目。
- `biz_5g_toc_network_resource`：网络资源明细，一行代表一个资源项。
- `biz_5g_toc_route_config`：地址池和路由，一行代表一个地址池或路由。
- `biz_5g_toc_doc_fragment`：文档片段，一行代表来源文档中的一个语义片段。
- `biz_5g_toc_query_guide`：数据库插件 / MCP 查询语义指南。
- `biz_5g_toc_project_overview_view`：项目汇总视图，一行代表一个项目。

所有明细表通过 `project_id` 关联项目主表，`project_code` 用于便捷筛选和来源追踪。

## 5. 原始资料导入

原始文件目录：

```text
/Users/zhangxj/Documents/5G知识库/随行专网
```

导入脚本：

```text
scripts/import_5g_toc_documents.py
```

生成数据：

```text
tmp/5g_toc_import/biz_5g_toc_import.sql
```

脚本处理 Word 开通申请、网管反馈和 Excel 资源表，生成项目、网络资源、地址池、路由和文档片段。

### 5.1 清洗规则

导入时自动删除模板责任栏和填写提示，包括：

- 集团客户部人员填写；
- 网络侧人员填写；
- 省网管或省网管 VPDN 人员填写；
- 地市传输人员填写；
- 填写人、申请填写、反馈填写等栏目提示；
- “申请 GE 光口填写”等括号提示。

清洗仅删除说明文字，保留实际业务字段和值。清洗后整行为空的记录不导入。

### 5.2 重新导入注意事项

当前脚本采用全量重建本批 ToC 数据的方式：先清理项目、资源、路由和文档片段，再重新导入原始资料。

> 注意：页面中手工编辑的项目状态、带宽、DNN、UPF、预计用户数、日期和备注，可能在重新全量导入后被原始资料覆盖。重新导入前应确认手工修改是否已同步回原始资料，或先做好数据库备份。

## 6. 数据库插件与 Chat2BI

查询前应先读取：

```sql
SELECT *
FROM biz_5g_toc_query_guide
WHERE enabled = 1
ORDER BY priority;
```

推荐智能体提示词：

```text
你负责查询ToC随行专网数据。

查询前优先读取 biz_5g_toc_query_guide。
项目查询使用 biz_5g_toc_project_overview_view。
网络资源查询使用 biz_5g_toc_network_resource。
地址池和路由查询使用 biz_5g_toc_route_config。
申请表、网管反馈和说明内容查询使用 biz_5g_toc_doc_fragment。

统计项目数量时使用项目汇总视图 COUNT(*)。
从资源或路由表统计项目时使用 COUNT(DISTINCT project_id)。
文档片段不用于项目数、资源数等数值统计。
只执行 SELECT，不执行增删改操作。
```

示例问题：

- 查询所有随行专网项目的 DNN、带宽和开通状态；
- 太原科技大学的两个地址池是什么；
- 山西大学主备 GRE 隧道参数是什么；
- 按项目统计网络资源数量并生成柱状图；
- 查询包含 keepalive 的网管反馈。

## 7. AI 知识库与 RAG

导入脚本会按项目生成 5 份 Markdown：

```text
tmp/5g_toc_rag/*.md
```

使用步骤：

1. 进入“AI应用平台 → AI知识库”。
2. 新建或打开“ToC随行专网”知识库。
3. 上传 `tmp/5g_toc_rag` 下的 Markdown 文件。
4. 对文档执行“向量化/重建”。
5. 在智能体配置中关联该知识库。
6. 同时关联数据库插件：知识库负责语义问答，数据库插件负责精确查询和统计。

原始资料变化后，重新运行导入脚本会重新生成 Markdown。知识库中的旧文档需要删除或替换后重新向量化，避免新旧版本同时参与召回。

## 8. 接口

接口统一前缀：

```text
/ai5g/toc-private-network
```

- `GET /summary`：汇总指标。
- `GET /projects`：项目列表。
- `PUT /projects/{projectCode}`：编辑项目。
- `GET /resources`：分页查询网络资源。
- `GET /routes`：分页查询地址池与路由。
- `GET /documents`：分页查询文档片段。
- `GET /guides`：查询 MCP 语义指南。

分页接口支持 `pageNo`、`pageSize`、`projectCode` 和 `keyword` 参数，单页最大 200 条。

## 9. 常见问题

### 菜单在角色授权中不可见

确认已执行 `biz_5g_toc_menu.sql`，然后重新进入角色授权页面。授权保存后需要重新登录。

### 父菜单打开后不显示子菜单

父菜单“文档管理模块”应保持 `is_leaf=0`、`always_show=0`，新增子菜单时不要修改父菜单的路由属性。

### 搜索文档后其他页签无数据

当前版本在切换页签时会自动清除关键词。如果仍出现旧行为，应重新部署前端并清除浏览器缓存或 PWA 缓存。

### 项目数量统计重复

项目统计必须使用 `biz_5g_toc_project_overview_view`。从网络资源或路由表统计项目时必须使用 `COUNT(DISTINCT project_id)`。

### RAG 搜不到新资料

确认新 Markdown 已替换旧文档，并且知识库文档状态已经完成向量化，而不是草稿或待处理状态。

## 10. 相关文件

- 建表 SQL：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/sql/biz_5g_toc_tables.sql`
- 菜单 SQL：`jeecg-boot/jeecg-module-biz/src/main/java/org/jeecg/modules/biz/ai5g/sql/biz_5g_toc_menu.sql`
- 导入脚本：`scripts/import_5g_toc_documents.py`
- 后端接口：`TocPrivateNetworkQueryController.java`
- 前端页面：`TocPrivateNetworkQuery.vue`
- MCP 简要说明：`assistant-docs/backend/biz/5g-toc-mcp-tables.md`
