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
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='task_id');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN task_id varchar(100) DEFAULT NULL COMMENT ''关联任务编号'' AFTER record_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='submission_no');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN submission_no int NOT NULL DEFAULT 1 COMMENT ''任务内提交序号'' AFTER task_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='submission_type');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN submission_type varchar(20) NOT NULL DEFAULT ''FINAL'' COMMENT ''提交类型'' AFTER submission_no', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='review_status');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN review_status varchar(20) NOT NULL DEFAULT ''SUBMITTED'' COMMENT ''审核状态'' AFTER submission_type', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_record' AND COLUMN_NAME='is_current');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_record ADD COLUMN is_current tinyint NOT NULL DEFAULT 1 COMMENT ''是否当前版本'' AFTER review_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_task_round' AND COLUMN_NAME='record_id');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_task_round ADD COLUMN record_id varchar(64) DEFAULT NULL COMMENT ''本次操作关联的提交记录'' AFTER task_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE biz_roomops_record r
JOIN biz_roomops_task t ON t.record_id = r.record_id
SET r.task_id = t.task_id,
    r.submission_no = COALESCE(r.submission_no, 1),
    r.submission_type = COALESCE(NULLIF(r.submission_type, ''), 'FINAL'),
    r.review_status = CASE WHEN t.status = 'DONE' THEN 'ACCEPTED' WHEN t.status IN ('REOPENED','AVAILABLE') THEN 'REJECTED' ELSE 'SUBMITTED' END,
    r.is_current = 1
WHERE r.task_id IS NULL OR r.task_id = '';

CREATE TABLE IF NOT EXISTS `biz_roomops_template` (
  `id` varchar(32) NOT NULL,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(100) NOT NULL,
  `business_type` varchar(32) NOT NULL DEFAULT 'inspection',
  `check_items_json` longtext NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT '1',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roomops_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检模板';

CREATE TABLE IF NOT EXISTS `biz_roomops_month_plan` (
  `id` varchar(32) NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(100) NOT NULL,
  `plan_month` varchar(7) NOT NULL,
  `template_id` varchar(32) NOT NULL,
  `room_ids_json` longtext NOT NULL,
  `assignee_userid` varchar(100) DEFAULT NULL,
  `assignee_name` varchar(100) DEFAULT NULL,
  `deadline_day` int NOT NULL DEFAULT 28,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `generated_count` int NOT NULL DEFAULT 0,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roomops_plan_code` (`plan_code`),
  KEY `idx_roomops_plan_month` (`plan_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度巡检计划';

CREATE TABLE IF NOT EXISTS `biz_roomops_issue` (
  `id` varchar(32) NOT NULL,
  `issue_id` varchar(100) NOT NULL,
  `record_id` varchar(64) NOT NULL,
  `room_id` varchar(64) DEFAULT NULL,
  `room_name` varchar(100) DEFAULT NULL,
  `description` varchar(2000) NOT NULL,
  `severity` varchar(20) NOT NULL DEFAULT 'normal',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN',
  `reporter_name` varchar(100) DEFAULT NULL,
  `assignee_userid` varchar(100) DEFAULT NULL,
  `assignee_name` varchar(100) DEFAULT NULL,
  `deadline_at` datetime DEFAULT NULL,
  `rectification_result` varchar(2000) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `closed_by` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roomops_issue_record` (`record_id`),
  UNIQUE KEY `uk_roomops_issue_id` (`issue_id`),
  KEY `idx_roomops_issue_status_deadline` (`status`, `deadline_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检问题整改闭环';

INSERT INTO `biz_roomops_template`
  (`id`, `template_code`, `template_name`, `business_type`, `check_items_json`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('roomopsdefaultinspectiontemplate', 'TPL-INSPECTION-DEFAULT', '标准机房巡检模板', 'inspection',
   '[{"code":"environment","name":"环境状态","required":true},{"code":"device","name":"设备状态","required":true},{"code":"temperature","name":"温度","unit":"℃"},{"code":"humidity","name":"湿度","unit":"%"},{"code":"exception","name":"异常说明"}]',
   '1', 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE `template_name`=VALUES(`template_name`), `check_items_json`=VALUES(`check_items_json`), `update_time`=NOW();
