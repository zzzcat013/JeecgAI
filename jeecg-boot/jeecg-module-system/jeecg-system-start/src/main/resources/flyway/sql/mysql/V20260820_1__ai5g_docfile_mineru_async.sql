ALTER TABLE biz_ai5g_docfile
  ADD COLUMN convert_started_at datetime NULL COMMENT '文档转换任务提交时间' AFTER source_package_path,
  ADD COLUMN mineru_task_id varchar(64) NULL COMMENT 'MinerU异步任务ID' AFTER convert_started_at,
  ADD COLUMN mineru_task_status varchar(32) NULL COMMENT 'MinerU任务状态: pending/processing/completed/failed' AFTER mineru_task_id,
  ADD COLUMN mineru_queued_ahead int NULL COMMENT 'MinerU排队前任务数' AFTER mineru_task_status,
  ADD COLUMN mineru_error varchar(512) NULL COMMENT 'MinerU任务错误信息' AFTER mineru_queued_ahead,
  ADD COLUMN mineru_started_at datetime NULL COMMENT 'MinerU任务开始时间' AFTER mineru_error,
  ADD COLUMN mineru_completed_at datetime NULL COMMENT 'MinerU任务完成时间' AFTER mineru_started_at;

CREATE INDEX idx_biz_ai5g_docfile_mineru_task ON biz_ai5g_docfile(mineru_task_id);
