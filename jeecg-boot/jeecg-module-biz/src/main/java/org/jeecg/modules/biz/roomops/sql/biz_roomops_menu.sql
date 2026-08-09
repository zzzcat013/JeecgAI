-- 机房运维菜单。执行后可在“角色授权”中给相关角色授权。
INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`,
  `menu_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`,
  `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `del_flag`,
  `rule_flag`, `status`, `internal_or_external`
) VALUES
(
  'roomops000000000000000000000001', '', '机房运维模块',
  '/roomops', 'layouts/default/index', 1,
  'RoomopsModule', 0, 2.50, 0, 'ant-design:database-outlined', 0, 1,
  0, 0, '机房巡检、工程施工、故障处理基础管理',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000018', 'roomops000000000000000000000001', '任务相关',
  '/roomops/task-menu', 'layouts/default/index', 1,
  'RoomopsTaskGroup', 0, 1.00, 0, 'ant-design:profile-outlined', 0, 1,
  0, 0, '任务分派、归档、预警和我的任务',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000019', 'roomops000000000000000000000001', '记录相关',
  '/roomops/record-menu', 'layouts/default/index', 1,
  'RoomopsRecordGroup', 0, 2.00, 0, 'ant-design:file-text-outlined', 0, 1,
  0, 0, '巡检、工程施工、故障处理业务记录',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000020', 'roomops000000000000000000000001', '数据配置',
  '/roomops/config-menu', 'layouts/default/index', 1,
  'RoomopsConfigGroup', 0, 3.00, 0, 'ant-design:setting-outlined', 0, 1,
  0, 0, '机房、钉钉用户和同步日志配置',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000017', 'roomops000000000000000000000018', '任务分派',
  '/roomops/task', 'biz/roomops/pages/TaskList', 1,
  'RoomopsTaskList', 1, 1.00, 0, 'ant-design:send-outlined', 1, 1,
  0, 0, '巡检、故障、工程任务分派、接单、确认闭环',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000021', 'roomops000000000000000000000018', '已归档任务',
  '/roomops/task/archived', 'biz/roomops/pages/ArchivedTaskList', 1,
  'RoomopsArchivedTaskList', 1, 2.00, 0, 'ant-design:inbox-outlined', 1, 1,
  0, 0, '查看已归档任务并支持恢复',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000022', 'roomops000000000000000000000018', '任务预警',
  '/roomops/task/warning', 'biz/roomops/pages/WarningTaskList', 1,
  'RoomopsWarningTaskList', 1, 3.00, 0, 'ant-design:warning-outlined', 1, 1,
  0, 0, '24 小时内临近截止且未完成的任务',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000023', 'roomops000000000000000000000018', '我的任务',
  '/roomops/task/mine', 'biz/roomops/pages/MyTaskList', 1,
  'RoomopsMyTaskList', 1, 4.00, 0, 'ant-design:user-outlined', 1, 1,
  0, 0, '与我相关（派发或执行）的任务',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000012', 'roomops000000000000000000000019', '业务记录',
  '/roomops/record', 'biz/roomops/pages/RecordList', 1,
  'RoomopsRecordList', 1, 1.00, 0, 'ant-design:file-text-outlined', 0, 1,
  0, 0, '巡检、工程施工、故障处理业务记录',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000013', 'roomops000000000000000000000019', '照片明细',
  '/roomops/photo', 'biz/roomops/pages/PhotoList', 1,
  'RoomopsPhotoList', 1, 2.00, 0, 'ant-design:picture-outlined', 0, 1,
  0, 0, '照片文件、照片级定位和备注',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000011', 'roomops000000000000000000000020', '机房列表',
  '/roomops/machine-room', 'biz/roomops/pages/MachineRoomList', 1,
  'RoomopsMachineRoomList', 1, 1.00, 0, 'ant-design:bank-outlined', 0, 1,
  0, 0, '机房基础信息维护',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000014', 'roomops000000000000000000000020', '钉钉用户',
  '/roomops/dingtalk-user', 'biz/roomops/pages/DingtalkUserList', 1,
  'RoomopsDingtalkUserList', 1, 2.00, 0, 'ant-design:user-outlined', 0, 1,
  0, 0, '钉钉用户与默认专业、地市',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000015', 'roomops000000000000000000000020', '同步日志',
  '/roomops/sync-log', 'biz/roomops/pages/SyncLogList', 1,
  'RoomopsSyncLogList', 1, 3.00, 0, 'ant-design:sync-outlined', 0, 1,
  0, 0, 'VPS 前置服务同步日志',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000016', 'roomops000000000000000000000014', '同步用户数据',
  NULL, NULL, 0,
  NULL, 2, 1.00, 0, NULL, 1, 0,
  0, 0, '从钉钉通讯录同步机房运维人员',
  'admin', NOW(), 0, 0, '1', 0
),
(
  'roomops000000000000000000000024', 'roomops000000000000000000000001', '巡检治理中心',
  '/roomops/governance', 'biz/roomops/pages/GovernanceCenter', 1,
  'RoomopsGovernanceCenter', 1, 4.00, 0, 'ant-design:dashboard-outlined', 1, 1,
  0, 0, '巡检模板、月度计划、整改闭环和月度统计归档',
  'admin', NOW(), 0, 0, '1', 0
)
ON DUPLICATE KEY UPDATE
  `parent_id`=VALUES(`parent_id`), `name`=VALUES(`name`), `url`=VALUES(`url`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
  `menu_type`=VALUES(`menu_type`), `sort_no`=VALUES(`sort_no`), `icon`=VALUES(`icon`),
  `is_leaf`=VALUES(`is_leaf`), `hidden`=VALUES(`hidden`), `description`=VALUES(`description`),
  `del_flag`=0, `status`='1', `update_by`='admin', `update_time`=NOW();

INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`) VALUES
('roomopsgov000000000000000000001', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000024'),
('roomopsgov000000000000000000002', '2085331438676848641', 'roomops000000000000000000000024'),
('roomopsgov000000000000000000003', '2085716441645244417', 'roomops000000000000000000000024');
