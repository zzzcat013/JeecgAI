-- 机房运维模块归拢安装脚本，可重复执行。
-- 执行前提：必须连接到已经初始化的 JeecgAI 数据库（包含 sys_permission、sys_role_permission 等 Jeecg 自带表）。
-- 由以下分脚本按顺序合并生成：
-- biz_roomops_tables_mysql.sql
-- biz_roomops_engineering_20260808.sql
-- biz_roomops_migrate_20260808.sql
-- biz_roomops_migrate_20260809.sql
-- biz_roomops_menu.sql
-- biz_roomops_engineering_menu.sql
-- biz_roomops_edit_permission.sql
-- 注意：工程菜单中的 sys_role_permission 引用了 admin/roomops/roomops_task 角色ID，
-- 新环境请先确认这三个角色存在，或到 JeecgAI“角色授权”界面重新配置。


-- ============================================================
-- 来源文件: biz_roomops_tables_mysql.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `biz_roomops_machine_room` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `room_id` varchar(64) NOT NULL COMMENT '机房编号',
  `room_name` varchar(100) NOT NULL COMMENT '机房名称',
  `domain_code` varchar(50) NOT NULL DEFAULT 'core_network' COMMENT '专业编码',
  `domain_short_code` varchar(20) NOT NULL DEFAULT 'CORE' COMMENT '专业编号简写',
  `domain_name` varchar(100) NOT NULL DEFAULT '核心网' COMMENT '专业名称',
  `region_code` varchar(50) NOT NULL DEFAULT 'TY' COMMENT '地市编码',
  `region_name` varchar(100) NOT NULL DEFAULT '太原' COMMENT '地市名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` varchar(20) DEFAULT '1' COMMENT '状态',
  `qr_code` varchar(255) DEFAULT NULL COMMENT '绑定二维码内容',
  `latitude` decimal(18,12) DEFAULT NULL COMMENT '机房纬度',
  `longitude` decimal(18,12) DEFAULT NULL COMMENT '机房经度',
  `allowed_radius_m` int DEFAULT 300 COMMENT '允许打卡距离(米)',
  `max_accuracy_m` int DEFAULT 200 COMMENT '最大定位精度(米)',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_roomops_machine_room_room_id` (`room_id`),
  KEY `idx_biz_roomops_machine_room_domain_region` (`domain_code`, `region_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-机房基础信息';

CREATE TABLE IF NOT EXISTS `biz_roomops_record` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `record_id` varchar(64) NOT NULL COMMENT '业务记录编号',
  `task_id` varchar(100) DEFAULT NULL COMMENT '关联任务编号',
  `submission_no` int NOT NULL DEFAULT '1' COMMENT '任务内提交序号',
  `submission_type` varchar(20) NOT NULL DEFAULT 'FINAL' COMMENT '提交类型: PROGRESS/FINAL',
  `review_status` varchar(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '审核状态',
  `is_current` tinyint NOT NULL DEFAULT '1' COMMENT '是否当前版本',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型: inspection/fault/engineering',
  `domain_code` varchar(50) NOT NULL DEFAULT 'core_network' COMMENT '专业编码',
  `domain_short_code` varchar(20) NOT NULL DEFAULT 'CORE' COMMENT '专业编号简写',
  `domain_name` varchar(100) NOT NULL DEFAULT '核心网' COMMENT '专业名称',
  `region_code` varchar(50) NOT NULL DEFAULT 'TY' COMMENT '地市编码',
  `region_name` varchar(100) NOT NULL DEFAULT '太原' COMMENT '地市名称',
  `room_id` varchar(64) DEFAULT NULL COMMENT '机房编号',
  `room_name` varchar(100) DEFAULT NULL COMMENT '机房名称',
  `inspector_name` varchar(100) DEFAULT NULL COMMENT '人员名称',
  `dingtalk_userid` varchar(100) DEFAULT NULL COMMENT '钉钉用户ID',
  `dingtalk_unionid` varchar(100) DEFAULT NULL COMMENT '钉钉UnionId',
  `latitude` decimal(18,12) DEFAULT NULL COMMENT '纬度',
  `longitude` decimal(18,12) DEFAULT NULL COMMENT '经度',
  `accuracy` decimal(18,6) DEFAULT NULL COMMENT '定位精度',
  `temperature` decimal(8,2) DEFAULT NULL COMMENT '温度(℃)',
  `humidity` decimal(8,2) DEFAULT NULL COMMENT '湿度(%)',
  `captured_at` datetime DEFAULT NULL COMMENT '现场采集时间',
  `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
  `environment_status` varchar(50) DEFAULT NULL COMMENT '环境状态',
  `device_status` varchar(50) DEFAULT NULL COMMENT '设备状态',
  `exception_desc` varchar(1000) DEFAULT NULL COMMENT '异常描述',
  `upload_mode` varchar(32) DEFAULT NULL COMMENT '上传方式: direct/offline_retry',
  `source` varchar(32) DEFAULT NULL COMMENT '来源系统',
  `fault_order_no` varchar(100) DEFAULT NULL COMMENT '故障工单号',
  `handling_result` varchar(1000) DEFAULT NULL COMMENT '处理情况',
  `construction_content` varchar(1000) DEFAULT NULL COMMENT '施工内容',
  `site_problems` varchar(1000) DEFAULT NULL COMMENT '现场发现的问题',
  `remaining_issues` varchar(1000) DEFAULT NULL COMMENT '遗留问题',
  `remark_note` varchar(1000) DEFAULT NULL COMMENT '备注说明',
  `raw_form_json` longtext COMMENT '原始表单JSON',
  `check_items_json` longtext COMMENT '结构化检查项JSON',
  `room_proof` varchar(255) DEFAULT NULL COMMENT '二维码或NFC现场凭证',
  `evidence_status` varchar(32) DEFAULT NULL COMMENT '服务端证据校验状态',
  `evidence_distance_m` decimal(10,2) DEFAULT NULL COMMENT '距机房距离(米)',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_roomops_record_record_id` (`record_id`),
  KEY `idx_biz_roomops_record_business_type` (`business_type`),
  KEY `idx_biz_roomops_record_task` (`task_id`, `submission_no`),
  KEY `idx_biz_roomops_record_domain_region` (`domain_code`, `region_code`),
  KEY `idx_biz_roomops_record_room_id` (`room_id`),
  KEY `idx_biz_roomops_record_submitted_at` (`submitted_at`),
  KEY `idx_biz_roomops_record_fault_order_no` (`fault_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-业务主记录';

CREATE TABLE IF NOT EXISTS `biz_roomops_photo` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `record_id` varchar(64) NOT NULL COMMENT '业务记录编号',
  `photo_index` int NOT NULL COMMENT '照片序号',
  `photo_total` int DEFAULT NULL COMMENT '照片总数',
  `original_filename` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `stored_filename` varchar(255) DEFAULT NULL COMMENT '存储文件名',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
  `content_type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `photo_captured_at` datetime DEFAULT NULL COMMENT '照片采集时间',
  `photo_latitude` decimal(18,12) DEFAULT NULL COMMENT '照片纬度',
  `photo_longitude` decimal(18,12) DEFAULT NULL COMMENT '照片经度',
  `photo_accuracy` decimal(18,6) DEFAULT NULL COMMENT '照片定位精度',
  `photo_remark` varchar(500) DEFAULT NULL COMMENT '照片备注',
  `watermarked` tinyint(1) DEFAULT '0' COMMENT '是否加水印',
  `uploaded_at` datetime DEFAULT NULL COMMENT '上传时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_roomops_photo_record_index` (`record_id`, `photo_index`),
  KEY `idx_biz_roomops_photo_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-照片明细';

CREATE TABLE IF NOT EXISTS `biz_roomops_dingtalk_user` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `dingtalk_userid` varchar(100) NOT NULL COMMENT '钉钉用户ID',
  `dingtalk_unionid` varchar(100) DEFAULT NULL COMMENT '钉钉UnionId',
  `name` varchar(100) NOT NULL COMMENT '用户姓名',
  `mobile` varchar(50) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像',
  `dept_id` varchar(100) DEFAULT NULL COMMENT '钉钉部门ID',
  `dept_name` varchar(200) DEFAULT NULL COMMENT '部门名称',
  `default_domain_code` varchar(50) NOT NULL DEFAULT 'core_network' COMMENT '默认专业编码',
  `default_domain_short_code` varchar(20) NOT NULL DEFAULT 'CORE' COMMENT '默认专业编号简写',
  `default_domain_name` varchar(100) NOT NULL DEFAULT '核心网' COMMENT '默认专业名称',
  `default_region_code` varchar(50) NOT NULL DEFAULT 'TY' COMMENT '默认地市编码',
  `default_region_name` varchar(100) NOT NULL DEFAULT '太原' COMMENT '默认地市名称',
  `active` varchar(20) DEFAULT '1' COMMENT '是否有效',
  `dingtalk_synced` tinyint(1) DEFAULT '0' COMMENT '是否来自钉钉通讯录同步',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最近同步时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_roomops_dingtalk_user_userid` (`dingtalk_userid`),
  KEY `idx_biz_roomops_dingtalk_user_unionid` (`dingtalk_unionid`),
  KEY `idx_biz_roomops_dingtalk_user_default_scope` (`default_domain_code`, `default_region_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-钉钉用户';

CREATE TABLE IF NOT EXISTS `biz_roomops_sync_log` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `sync_batch_id` varchar(64) NOT NULL COMMENT '同步批次号',
  `source_record_id` varchar(64) DEFAULT NULL COMMENT 'VPS前置记录编号',
  `record_id` varchar(64) DEFAULT NULL COMMENT '正式业务记录编号',
  `business_type` varchar(32) DEFAULT NULL COMMENT '业务类型',
  `sync_direction` varchar(32) NOT NULL DEFAULT 'vps_to_jeecg' COMMENT '同步方向',
  `sync_status` varchar(32) NOT NULL DEFAULT 'pending' COMMENT '同步状态',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '错误信息',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  KEY `idx_biz_roomops_sync_log_batch` (`sync_batch_id`),
  KEY `idx_biz_roomops_sync_log_record` (`record_id`),
  KEY `idx_biz_roomops_sync_log_status` (`sync_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-同步日志';

CREATE TABLE IF NOT EXISTS `biz_roomops_task` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `task_id` varchar(100) NOT NULL COMMENT '任务编号',
  `record_id` varchar(64) DEFAULT NULL COMMENT '本次操作关联的提交记录',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型: inspection/fault/engineering',
  `task_title` varchar(200) DEFAULT NULL COMMENT '任务标题',
  `task_content` varchar(2000) DEFAULT NULL COMMENT '任务内容',
  `domain_code` varchar(50) NOT NULL DEFAULT 'core_network' COMMENT '专业编码',
  `domain_short_code` varchar(20) NOT NULL DEFAULT 'CORE' COMMENT '专业编号简写',
  `domain_name` varchar(100) NOT NULL DEFAULT '核心网' COMMENT '专业名称',
  `region_code` varchar(50) NOT NULL DEFAULT 'TY' COMMENT '地市编码',
  `region_name` varchar(100) NOT NULL DEFAULT '太原' COMMENT '地市名称',
  `room_id` varchar(64) DEFAULT NULL COMMENT '机房编号',
  `room_name` varchar(100) DEFAULT NULL COMMENT '机房名称',
  `assigner_userid` varchar(100) DEFAULT NULL COMMENT '派单人钉钉用户ID',
  `assigner_name` varchar(100) DEFAULT NULL COMMENT '派单人姓名',
  `assignee_userid` varchar(100) DEFAULT NULL COMMENT '执行人钉钉用户ID',
  `assignee_name` varchar(100) DEFAULT NULL COMMENT '执行人姓名',
  `status` varchar(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态: AVAILABLE/ASSIGNED/SUBMITTED/REOPENED/DONE',
  `priority` varchar(20) NOT NULL DEFAULT 'normal' COMMENT '优先级: low/normal/high/urgent',
  `round_count` int NOT NULL DEFAULT '1' COMMENT '当前轮次',
  `deadline_at` datetime DEFAULT NULL COMMENT '截止时间',
  `assigned_at` datetime DEFAULT NULL COMMENT '派单时间',
  `claimed_at` datetime DEFAULT NULL COMMENT '接单时间',
  `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认闭环时间',
  `reject_remark` varchar(1000) DEFAULT NULL COMMENT '驳回备注',
  `confirm_remark` varchar(1000) DEFAULT NULL COMMENT '确认备注',
  `confirm_by` varchar(100) DEFAULT NULL COMMENT '确认人',
  `confirm_userid` varchar(100) DEFAULT NULL COMMENT '确认人用户ID',
  `record_id` varchar(100) DEFAULT NULL COMMENT '关联业务记录编号',
  `project_id` varchar(100) DEFAULT NULL COMMENT '关联工程编号',
  `archived` tinyint NOT NULL DEFAULT '0' COMMENT '是否归档: 0/1',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `archived_by` varchar(100) DEFAULT NULL COMMENT '归档人',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_roomops_task_task_id` (`task_id`),
  KEY `idx_biz_roomops_task_business_type` (`business_type`),
  KEY `idx_biz_roomops_task_status` (`status`),
  KEY `idx_biz_roomops_task_assignee` (`assignee_userid`),
  KEY `idx_biz_roomops_task_room_id` (`room_id`),
  KEY `idx_biz_roomops_task_project_id` (`project_id`),
  KEY `idx_biz_roomops_task_archived` (`archived`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-任务分派主表';

CREATE TABLE IF NOT EXISTS `biz_roomops_task_round` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `task_id` varchar(100) NOT NULL COMMENT '任务编号',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '轮次',
  `action` varchar(32) NOT NULL COMMENT '动作: CREATE/UPDATE/REASSIGN/CLAIM/SUBMIT/REJECT/CONFIRM',
  `from_status` varchar(32) DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(32) DEFAULT NULL COMMENT '新状态',
  `operator_userid` varchar(100) DEFAULT NULL COMMENT '操作人用户ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `action_time` datetime DEFAULT NULL COMMENT '操作时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_roomops_task_round_task` (`task_id`, `round_no`),
  KEY `idx_biz_roomops_task_round_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-任务流转记录';

-- ============================================================
-- 来源文件: biz_roomops_engineering_20260808.sql
-- ============================================================

-- 机房运维-工程管理 20260808，可重复执行。

CREATE TABLE IF NOT EXISTS `biz_roomops_engineering_project` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `project_id` varchar(100) NOT NULL COMMENT '工程编号',
  `project_name` varchar(200) NOT NULL COMMENT '工程名称',
  `category` varchar(100) DEFAULT NULL COMMENT '工程类别',
  `ownership` varchar(200) DEFAULT NULL COMMENT '归属单位/部门',
  `domain_code` varchar(50) NOT NULL DEFAULT 'core_network' COMMENT '专业编码',
  `domain_short_code` varchar(20) NOT NULL DEFAULT 'CORE' COMMENT '专业简写',
  `domain_name` varchar(100) NOT NULL DEFAULT '核心网' COMMENT '专业名称',
  `region_code` varchar(50) NOT NULL DEFAULT 'TY' COMMENT '地市编码',
  `region_name` varchar(100) NOT NULL DEFAULT '太原' COMMENT '地市名称',
  `room_id` varchar(64) DEFAULT NULL COMMENT '机房编号',
  `room_name` varchar(100) DEFAULT NULL COMMENT '机房名称',
  `status` varchar(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED/STARTED/IN_PROGRESS/COMPLETED/ACCEPTED',
  `start_report_date` date DEFAULT NULL COMMENT '开工报告日期',
  `start_report_company` varchar(200) DEFAULT NULL COMMENT '开工报告单位',
  `start_report_person` varchar(100) DEFAULT NULL COMMENT '开工报告负责人',
  `start_report_content` varchar(2000) DEFAULT NULL COMMENT '开工报告内容',
  `description` varchar(2000) DEFAULT NULL COMMENT '工程说明',
  `archived` tinyint NOT NULL DEFAULT '0' COMMENT '是否归档: 0/1',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `archived_by` varchar(100) DEFAULT NULL COMMENT '归档人',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_roomops_engineering_project_id` (`project_id`),
  KEY `idx_biz_roomops_engineering_project_status` (`status`),
  KEY `idx_biz_roomops_engineering_project_room` (`room_id`),
  KEY `idx_biz_roomops_engineering_project_archived` (`archived`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-工程项目';

CREATE TABLE IF NOT EXISTS `biz_roomops_engineering_attachment` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `project_id` varchar(100) NOT NULL COMMENT '工程编号',
  `doc_type` varchar(32) NOT NULL DEFAULT 'OTHER' COMMENT '附件类型: START_REPORT/PLAN/TECHNICAL/SAFETY/OTHER',
  `original_filename` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `stored_filename` varchar(255) DEFAULT NULL COMMENT '存储文件名',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
  `content_type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `file_md5` varchar(64) DEFAULT NULL COMMENT '文件MD5，用于附件去重',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `uploader_userid` varchar(100) DEFAULT NULL COMMENT '上传人用户ID',
  `uploader_name` varchar(100) DEFAULT NULL COMMENT '上传人姓名',
  `uploaded_at` datetime DEFAULT NULL COMMENT '上传时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `sys_org_code` varchar(64) DEFAULT NULL COMMENT '所属部门',
  PRIMARY KEY (`id`),
  KEY `idx_biz_roomops_eng_att_project` (`project_id`),
  KEY `idx_biz_roomops_eng_att_type` (`doc_type`),
  UNIQUE KEY `uk_biz_roomops_eng_att_duplicate` (`project_id`, `doc_type`, `file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房运维-工程附件';

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_engineering_attachment' AND COLUMN_NAME = 'file_md5'
);
SET @sql := IF(@column_exists = 0,
  'ALTER TABLE `biz_roomops_engineering_attachment` ADD COLUMN `file_md5` varchar(64) DEFAULT NULL COMMENT ''文件MD5，用于附件去重''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_engineering_attachment' AND INDEX_NAME = 'uk_biz_roomops_eng_att_duplicate'
);
SET @sql := IF(@index_exists = 0,
  'ALTER TABLE `biz_roomops_engineering_attachment` ADD UNIQUE KEY `uk_biz_roomops_eng_att_duplicate` (`project_id`, `doc_type`, `file_md5`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_task' AND COLUMN_NAME = 'project_id'
);
SET @sql := IF(@column_exists = 0,
  'ALTER TABLE `biz_roomops_task` ADD COLUMN `project_id` varchar(100) DEFAULT NULL COMMENT ''关联工程编号''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_task' AND INDEX_NAME = 'idx_biz_roomops_task_project_id'
);
SET @sql := IF(@index_exists = 0,
  'ALTER TABLE `biz_roomops_task` ADD KEY `idx_biz_roomops_task_project_id` (`project_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 来源文件: biz_roomops_migrate_20260808.sql
-- ============================================================

-- 机房运维增量迁移 20260808：任务归档字段。可在现有 jeecgai 库重复执行。

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_task' AND COLUMN_NAME = 'archived'
);
SET @sql := IF(@column_exists = 0,
  'ALTER TABLE `biz_roomops_task` ADD COLUMN `archived` tinyint NOT NULL DEFAULT 0 COMMENT ''是否归档: 0/1''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_task' AND COLUMN_NAME = 'archived_at'
);
SET @sql := IF(@column_exists = 0,
  'ALTER TABLE `biz_roomops_task` ADD COLUMN `archived_at` datetime DEFAULT NULL COMMENT ''归档时间''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_task' AND COLUMN_NAME = 'archived_by'
);
SET @sql := IF(@column_exists = 0,
  'ALTER TABLE `biz_roomops_task` ADD COLUMN `archived_by` varchar(100) DEFAULT NULL COMMENT ''归档人''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_roomops_task' AND INDEX_NAME = 'idx_biz_roomops_task_archived'
);
SET @sql := IF(@index_exists = 0,
  'ALTER TABLE `biz_roomops_task` ADD KEY `idx_biz_roomops_task_archived` (`archived`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 来源文件: biz_roomops_migrate_20260809.sql
-- ============================================================

SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_machine_room' AND COLUMN_NAME='qr_code');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_machine_room ADD COLUMN qr_code varchar(255) DEFAULT NULL COMMENT ''绑定二维码内容''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_machine_room' AND COLUMN_NAME='latitude');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_machine_room ADD COLUMN latitude decimal(18,12) DEFAULT NULL COMMENT ''机房纬度''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_machine_room' AND COLUMN_NAME='longitude');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_machine_room ADD COLUMN longitude decimal(18,12) DEFAULT NULL COMMENT ''机房经度''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_machine_room' AND COLUMN_NAME='allowed_radius_m');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_machine_room ADD COLUMN allowed_radius_m int DEFAULT 300 COMMENT ''允许打卡距离(米)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_machine_room' AND COLUMN_NAME='max_accuracy_m');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_machine_room ADD COLUMN max_accuracy_m int DEFAULT 200 COMMENT ''最大定位精度(米)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='temperature');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN temperature decimal(8,2) DEFAULT NULL COMMENT ''温度(℃)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='humidity');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN humidity decimal(8,2) DEFAULT NULL COMMENT ''湿度(%)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='check_items_json');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN check_items_json longtext COMMENT ''结构化检查项JSON''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='room_proof');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN room_proof varchar(255) DEFAULT NULL COMMENT ''二维码或NFC现场凭证''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='evidence_status');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN evidence_status varchar(32) DEFAULT NULL COMMENT ''服务端证据校验状态''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='evidence_distance_m');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN evidence_distance_m decimal(10,2) DEFAULT NULL COMMENT ''距机房距离(米)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `biz_roomops_template` (
  `id` varchar(32) NOT NULL, `template_code` varchar(64) NOT NULL, `template_name` varchar(100) NOT NULL,
  `business_type` varchar(32) NOT NULL DEFAULT 'inspection', `check_items_json` longtext NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT '1', `create_by` varchar(50) DEFAULT NULL, `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL, `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_roomops_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检模板';

CREATE TABLE IF NOT EXISTS `biz_roomops_month_plan` (
  `id` varchar(32) NOT NULL, `plan_code` varchar(64) NOT NULL, `plan_name` varchar(100) NOT NULL,
  `plan_month` varchar(7) NOT NULL, `template_id` varchar(32) NOT NULL, `room_ids_json` longtext NOT NULL,
  `assignee_userid` varchar(100) DEFAULT NULL, `assignee_name` varchar(100) DEFAULT NULL,
  `deadline_day` int NOT NULL DEFAULT 28, `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `generated_count` int NOT NULL DEFAULT 0, `create_by` varchar(50) DEFAULT NULL, `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL, `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_roomops_plan_code` (`plan_code`), KEY `idx_roomops_plan_month` (`plan_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度巡检计划';

CREATE TABLE IF NOT EXISTS `biz_roomops_issue` (
  `id` varchar(32) NOT NULL, `issue_id` varchar(100) NOT NULL, `record_id` varchar(64) NOT NULL,
  `room_id` varchar(64) DEFAULT NULL, `room_name` varchar(100) DEFAULT NULL, `description` varchar(2000) NOT NULL,
  `severity` varchar(20) NOT NULL DEFAULT 'normal', `status` varchar(20) NOT NULL DEFAULT 'OPEN',
  `reporter_name` varchar(100) DEFAULT NULL, `assignee_userid` varchar(100) DEFAULT NULL,
  `assignee_name` varchar(100) DEFAULT NULL, `deadline_at` datetime DEFAULT NULL,
  `rectification_result` varchar(2000) DEFAULT NULL, `resolved_at` datetime DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL, `closed_by` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL, `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_roomops_issue_record` (`record_id`),
  UNIQUE KEY `uk_roomops_issue_id` (`issue_id`), KEY `idx_roomops_issue_status_deadline` (`status`, `deadline_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检问题整改闭环';

INSERT INTO `biz_roomops_template`
  (`id`, `template_code`, `template_name`, `business_type`, `check_items_json`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('roomopsdefaultinspectiontemplate', 'TPL-INSPECTION-DEFAULT', '标准机房巡检模板', 'inspection',
   '[{"code":"environment","name":"环境状态","required":true},{"code":"device","name":"设备状态","required":true},{"code":"temperature","name":"温度","unit":"℃"},{"code":"humidity","name":"湿度","unit":"%"},{"code":"exception","name":"异常说明"}]',
   '1', 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE `template_name`=VALUES(`template_name`), `check_items_json`=VALUES(`check_items_json`), `update_time`=NOW();

-- ============================================================
-- 来源文件: biz_roomops_menu.sql
-- ============================================================

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

-- ============================================================
-- 来源文件: biz_roomops_engineering_menu.sql
-- ============================================================

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

-- ============================================================
-- 来源文件: biz_roomops_edit_permission.sql
-- ============================================================

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
('roomops000000000000000000000034', 'roomops000000000000000000000015', '编辑同步日志', NULL, NULL, NULL, 2, 'roomops:syncLog:edit', '0', 2.00, 1, 0, '1', NOW()),
('roomops000000000000000000000035', 'roomops000000000000000000000017', '编辑任务', NULL, NULL, NULL, 2, 'roomops:task:edit', '0', 2.00, 1, 0, '1', NOW());

UPDATE `sys_permission`
SET `is_leaf` = 0, `update_time` = NOW()
WHERE `id` IN (
  'roomops000000000000000000000011',
  'roomops000000000000000000000012',
  'roomops000000000000000000000013',
  'roomops000000000000000000000014',
  'roomops000000000000000000000015',
  'roomops000000000000000000000017'
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
('roomopsedit000000000000000000010', '2085716441645244417', 'roomops000000000000000000000034'),
-- 任务编辑
('roomopsedit000000000000000000011', 'f6817f48af4fb3af11b9e8bf182f618b', 'roomops000000000000000000000035'),
('roomopsedit000000000000000000012', '2085331438676848641', 'roomops000000000000000000000035'),
('roomopsedit000000000000000000013', '2085716441645244417', 'roomops000000000000000000000035');

-- ================= 机房运维角色首页配置 =================
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
