-- ToC 随行专网查询菜单。执行后可在“角色授权 -> 文档管理模块”下授权。
INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`,
  `menu_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`,
  `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`,
  `rule_flag`, `status`, `internal_or_external`
) VALUES (
  'toc5gprivatequery00000000000001', '2008525958846431233', '随行专网查询',
  '/ai5g/toc-private-network', 'biz/ai5g/pages/TocPrivateNetworkQuery', 1,
  'TocPrivateNetworkQuery', 1, 5.00, 0, 'ant-design:global-outlined', 1, 1,
  0, 0, 'ToC随行专网项目、网络资源、地址池路由和文档查询',
  'admin', NOW(), 0, 0, '1', 0
)
ON DUPLICATE KEY UPDATE
  `parent_id`=VALUES(`parent_id`), `name`=VALUES(`name`), `url`=VALUES(`url`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
  `menu_type`=VALUES(`menu_type`), `sort_no`=VALUES(`sort_no`), `icon`=VALUES(`icon`),
  `is_leaf`=VALUES(`is_leaf`), `hidden`=VALUES(`hidden`), `description`=VALUES(`description`),
  `del_flag`=0, `status`='1', `update_by`='admin', `update_time`=NOW();

-- 不修改父菜单的 is_leaf/always_show 等路由属性；父子关系由 parent_id 自动识别。
