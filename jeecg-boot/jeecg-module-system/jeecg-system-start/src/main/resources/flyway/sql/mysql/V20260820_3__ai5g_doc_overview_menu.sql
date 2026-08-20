INSERT INTO sys_permission (
  id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type,
  sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description,
  create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external
)
SELECT
  'ai5g-doc-overview-0001', '2008525958846431233', '文档管理概览', '/ai5g/document-overview',
  'biz/ai5g/pages/DocumentOverview', NULL, NULL, 1, NULL, '0',
  2.50, NULL, 'ant-design:bar-chart-outlined', 1, NULL, 0, NULL, '上传文档状态/分类/数量概览',
  'admin', NOW(), 'admin', NOW(), 0, 0, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission WHERE id = 'ai5g-doc-overview-0001'
);

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT
  'ai5g-doc-overview-rp-0001', 'ai5g', 'ai5g-doc-overview-0001', NULL, NOW(), NULL
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission
  WHERE role_id = 'ai5g' AND permission_id = 'ai5g-doc-overview-0001'
);
