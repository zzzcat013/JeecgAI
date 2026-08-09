-- 机房运维编辑按钮权限，可重复执行。
-- 默认授权给 admin、机房运维(roomops)、机房运维任务管理(roomops_task)；
-- 如需调整角色，修改下面 sys_role_permission 插入中的 role_id 即可。

INSERT IGNORE INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `component_name`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `is_leaf`, `del_flag`, `status`, `create_time`
) VALUES
('roomops000000000000000000000030', 'roomops000000000000000000000012', '编辑业务记录', NULL, NULL, NULL, 2, 'roomops:record:edit', '0', 2.00, 1, 0, '1', NOW()),
('roomops000000000000000000000031', 'roomops000000000000000000000013', '编辑照片明细', NULL, NULL, NULL, 2, 'roomops:photo:edit', '0', 2.00, 1, 0, '1', NOW()),
('roomops000000000000000000000032', 'roomops000000000000000000000011', '编辑机房', NULL, NULL, NULL, 2, 'roomops:machineRoom:edit', '0', 2.00, 1, 0, '1', NOW()),
('roomops000000000000000000000033', 'roomops000000000000000000000014', '编辑钉钉用户', NULL, NULL, NULL, 2, 'roomops:dingtalkUser:edit', '0', 2.00, 1, 0, '1', NOW()),
('roomops000000000000000000000034', 'roomops000000000000000000000015', '编辑同步日志', NULL, NULL, NULL, 2, 'roomops:syncLog:edit', '0', 2.00, 1, 0, '1', NOW());

UPDATE `sys_permission`
SET `is_leaf` = 0, `update_time` = NOW()
WHERE `id` IN (
  'roomops000000000000000000000011',
  'roomops000000000000000000000012',
  'roomops000000000000000000000013',
  'roomops000000000000000000000014',
  'roomops000000000000000000000015'
);

INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`) VALUES
-- admin
('roomopsedit000000000000000000001', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000030'),
('roomopsedit000000000000000000002', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000031'),
('roomopsedit000000000000000000003', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000032'),
('roomopsedit000000000000000000004', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000033'),
('roomopsedit000000000000000000005', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000034'),
-- 机房运维：业务记录、照片明细
('roomopsedit000000000000000000006', '2085331438676848641', 'roomops000000000000000000000030'),
('roomopsedit000000000000000000007', '2085331438676848641', 'roomops000000000000000000000031'),
-- 机房运维任务管理：数据配置
('roomopsedit000000000000000000008', '2085716441645244417', 'roomops000000000000000000000032'),
('roomopsedit000000000000000000009', '2085716441645244417', 'roomops000000000000000000000033'),
('roomopsedit000000000000000000010', '2085716441645244417', 'roomops000000000000000000000034');
