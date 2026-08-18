-- 机房运维角色首页配置（可重复执行）。
-- 1) 新增隐藏菜单：机房运维工作台（/roomops/home），作为机房运维角色的默认首页路由；
-- 2) 将菜单授权给 管理员、机房运维(roomops)、机房运维任务管理(roomops_task)；
-- 3) 在 sys_role_index 中为 roomops / roomops_task 配置角色首页（优先级 0，最优先）。

INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`,
  `menu_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`,
  `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`,
  `rule_flag`, `status`, `internal_or_external`
) VALUES (
  'roomops000000000000000000000050', 'roomops000000000000000000000001', '机房运维工作台',
  '/roomops/home', 'biz/roomops/pages/MachineRoomHome', 1,
  'MachineRoomHome', 0, 0.10, 0, 'ant-design:home-outlined', 1, 1,
  1, 0, '机房运维角色默认首页：功能操作介绍',
  'admin', NOW(), 0, 0, '1', 0
)
ON DUPLICATE KEY UPDATE
  `parent_id`=VALUES(`parent_id`), `name`=VALUES(`name`), `url`=VALUES(`url`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
  `menu_type`=VALUES(`menu_type`), `sort_no`=VALUES(`sort_no`), `icon`=VALUES(`icon`),
  `is_leaf`=VALUES(`is_leaf`), `keep_alive`=VALUES(`keep_alive`), `hidden`=VALUES(`hidden`),
  `description`=VALUES(`description`), `del_flag`=0, `status`='1',
  `update_by`='admin', `update_time`=NOW();

INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`) VALUES
('roomopshome000000000000000000001', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000050'),
('roomopshome000000000000000000002', '2085331438676848641', 'roomops000000000000000000000050'),
('roomopshome000000000000000000003', '2085716441645244417', 'roomops000000000000000000000050');

INSERT INTO `sys_role_index` (
  `id`, `role_code`, `url`, `component`, `is_route`, `priority`, `status`,
  `create_by`, `create_time`, `update_by`, `update_time`, `sys_org_code`, `relation_type`
) VALUES
('roomopshidx000000000000000000001', 'roomops', '/roomops/home', 'biz/roomops/pages/MachineRoomHome', 1, 0, '1', 'admin', NOW(), 'admin', NOW(), 'A02A01', 'ROLE'),
('roomopshidx000000000000000000002', 'roomops_task', '/roomops/home', 'biz/roomops/pages/MachineRoomHome', 1, 0, '1', 'admin', NOW(), 'admin', NOW(), 'A02A01', 'ROLE')
ON DUPLICATE KEY UPDATE
  `url`=VALUES(`url`), `component`=VALUES(`component`), `is_route`=VALUES(`is_route`),
  `priority`=VALUES(`priority`), `status`=VALUES(`status`), `relation_type`=VALUES(`relation_type`),
  `update_by`='admin', `update_time`=NOW();
