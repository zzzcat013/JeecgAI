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
