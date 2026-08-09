-- 机房运维-工程管理菜单与编辑权限，可重复执行。
-- 默认授权给 admin、机房运维(roomops)、机房运维任务管理(roomops_task)；
-- 如需调整，修改 sys_role_permission 中的 role_id 或到“角色授权”里重新配置。

INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`,
  `menu_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`,
  `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`,
  `rule_flag`, `status`, `internal_or_external`
) VALUES
(
  'roomops000000000000000000000040', 'roomops000000000000000000000001', '工程管理',
  '/roomops/engineering-menu', 'layouts/default/index', 1,
  'RoomopsEngineeringGroup', 0, 1.50, 0, 'ant-design:tool-outlined', 0, 1,
  0, 0, '工程录入、工程列表、工程任务与归档管理',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000041', 'roomops000000000000000000000040', '工程录入',
  '/roomops/engineering/entry', 'biz/roomops/pages/EngineeringProjectEntry', 1,
  'RoomopsEngineeringEntry', 1, 1.00, 0, 'ant-design:form-outlined', 1, 1,
  0, 0, '结构化开工报告及施工方案、技术交底、安全交底等附件录入',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000042', 'roomops000000000000000000000040', '工程列表',
  '/roomops/engineering/list', 'biz/roomops/pages/EngineeringProjectList', 1,
  'RoomopsEngineeringList', 1, 2.00, 0, 'ant-design:project-outlined', 0, 1,
  0, 0, '工程查询、状态更新、归档、恢复和任务派发',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000043', 'roomops000000000000000000000040', '工程任务',
  '/roomops/engineering/task', 'biz/roomops/pages/EngineeringTaskList', 1,
  'RoomopsEngineeringTaskList', 1, 3.00, 0, 'ant-design:send-outlined', 1, 1,
  0, 0, '工程随工任务分派、接单、提交和确认闭环',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000044', 'roomops000000000000000000000040', '已归档工程',
  '/roomops/engineering/archived', 'biz/roomops/pages/ArchivedEngineeringProjectList', 1,
  'RoomopsArchivedEngineeringList', 1, 4.00, 0, 'ant-design:inbox-outlined', 1, 1,
  0, 0, '查看已归档工程并支持恢复',
  'admin', NOW(), 0, 0, '1', 0
)
ON DUPLICATE KEY UPDATE
  `parent_id`=VALUES(`parent_id`), `name`=VALUES(`name`), `url`=VALUES(`url`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
  `menu_type`=VALUES(`menu_type`), `sort_no`=VALUES(`sort_no`), `icon`=VALUES(`icon`),
  `is_leaf`=VALUES(`is_leaf`), `hidden`=VALUES(`hidden`), `description`=VALUES(`description`),
  `del_flag`=0, `status`='1', `update_by`='admin', `update_time`=NOW();

INSERT IGNORE INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `component_name`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `is_leaf`, `del_flag`, `status`, `create_time`
) VALUES
('roomops000000000000000000000045', 'roomops000000000000000000000042', '编辑工程', NULL, NULL, NULL, 2, 'roomops:engineering:edit', '0', 2.00, 1, 0, '1', NOW());

UPDATE `sys_permission`
SET `is_leaf` = 0, `update_time` = NOW()
WHERE `id` IN (
  'roomops000000000000000000000040',
  'roomops000000000000000000000042'
);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`) VALUES
-- admin
('roomopseng0000000001', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000040'),
('roomopseng0000000002', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000041'),
('roomopseng0000000003', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000042'),
('roomopseng0000000004', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000043'),
('roomopseng0000000005', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000044'),
('roomopseng0000000006', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000045'),
-- 机房运维
('roomopseng0000000007', '2085331438676848641', 'roomops000000000000000000000040'),
('roomopseng0000000008', '2085331438676848641', 'roomops000000000000000000000041'),
('roomopseng0000000009', '2085331438676848641', 'roomops000000000000000000000042'),
('roomopseng0000000010', '2085331438676848641', 'roomops000000000000000000000043'),
('roomopseng0000000011', '2085331438676848641', 'roomops000000000000000000000044'),
('roomopseng0000000012', '2085331438676848641', 'roomops000000000000000000000045'),
-- 机房运维任务管理
('roomopseng0000000013', '2085716441645244417', 'roomops000000000000000000000040'),
('roomopseng0000000014', '2085716441645244417', 'roomops000000000000000000000041'),
('roomopseng0000000015', '2085716441645244417', 'roomops000000000000000000000042'),
('roomopseng0000000016', '2085716441645244417', 'roomops000000000000000000000043'),
('roomopseng0000000017', '2085716441645244417', 'roomops000000000000000000000044')
ON DUPLICATE KEY UPDATE
  `role_id`=VALUES(`role_id`), `permission_id`=VALUES(`permission_id`);
