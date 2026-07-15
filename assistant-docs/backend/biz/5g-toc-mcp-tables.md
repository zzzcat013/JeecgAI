# ToC 随行专网数据库插件 / MCP 查询说明

查询前优先读取 `biz_5g_toc_query_guide`。项目统计优先使用 `biz_5g_toc_project_overview_view`，该视图一行代表一个项目，可避免因网络资源或路由一对多关联造成重复统计。

- `biz_5g_toc_project_overview_view`：项目、客户、DNN、ServiceID、带宽、UPF、预计用户数和资源数量。
- `biz_5g_toc_network_resource`：GRE 内外层 IP、IPRAN ASG、MAR、VPN、电路等资源明细。
- `biz_5g_toc_route_config`：UPF 终端地址池、客户内网网段、静态路由和下一跳。
- `biz_5g_toc_doc_fragment`：申请表、网管反馈、开通说明等文本检索；不用于数量统计。

规则：

- “有多少个项目”使用项目汇总视图 `COUNT(*)`。
- 从资源或路由表统计项目时使用 `COUNT(DISTINCT project_id)`。
- “预计用户数”使用项目汇总视图 `SUM(expected_user_count)`。
- 资源、路由、文档均通过 `project_id` 关联项目表；`project_code` 仅作为便捷查询字段。
- 只允许生成 `SELECT` 查询。
