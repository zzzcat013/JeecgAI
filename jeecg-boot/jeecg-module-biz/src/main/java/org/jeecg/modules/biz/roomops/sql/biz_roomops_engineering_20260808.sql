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
