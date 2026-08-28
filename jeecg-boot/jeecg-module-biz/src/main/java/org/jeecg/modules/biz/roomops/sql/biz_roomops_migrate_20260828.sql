-- 2026-08-28: 任务分派支持多机房多人员（候选执行人，抢单制）
-- 候选执行人：任务发布后仅候选名单内人员可看到并认领；为空则所有人可认领
SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_task' AND COLUMN_NAME='candidate_userids');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_task ADD COLUMN candidate_userids varchar(2000) NULL COMMENT ''候选执行人userid，逗号分隔'' AFTER assignee_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_roomops_task' AND COLUMN_NAME='candidate_names');
SET @sql := IF(@column_exists=0, 'ALTER TABLE biz_roomops_task ADD COLUMN candidate_names varchar(2000) NULL COMMENT ''候选执行人姓名，逗号分隔'' AFTER candidate_userids', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
