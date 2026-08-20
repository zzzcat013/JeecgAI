INSERT INTO sys_permission (
  id, parent_id, name, url, component, is_route, component_name,
  menu_type, sort_no, always_show, icon, is_leaf, keep_alive,
  hidden, hide_tab, description, create_by, create_time, del_flag,
  rule_flag, status, internal_or_external
)
SELECT
  'ai5g-home-0001', '2008525958846431233', 'AI5G首页', '/ai5g/home',
  'biz/ai5g/pages/Ai5gHome', 1, 'Ai5gHome', 0, 0.50, 0,
  'ant-design:home-outlined', 1, 1, 1, 0,
  'AI5G角色独立首页：文档、知识库和项目数据查询统一入口',
  'admin', NOW(), 0, 0, '1', 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission WHERE id = 'ai5g-home-0001'
);

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT
  'ai5g-home-rp-0001', 'ai5g', 'ai5g-home-0001', NULL, NOW(), NULL
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission
  WHERE role_id = 'ai5g' AND permission_id = 'ai5g-home-0001'
);

INSERT INTO sys_role_index (
  id, role_code, url, component, is_route, priority, status,
  create_by, create_time, update_by, update_time, sys_org_code, relation_type
)
SELECT
  'ai5ghidx000000000000000000001', 'ai5g', '/ai5g/home',
  'biz/ai5g/pages/Ai5gHome', 1, 0, '1',
  'admin', NOW(), 'admin', NOW(), 'A02A01', 'ROLE'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_index WHERE role_code = 'ai5g' AND relation_type = 'ROLE' AND status = '1'
);
