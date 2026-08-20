CREATE TABLE IF NOT EXISTS biz_ai5g_docfile_tombstone (
  id                 VARCHAR(32) NOT NULL COMMENT '删除标记ID',
  doc_id             VARCHAR(32) DEFAULT NULL COMMENT '原文档ID',
  display_name       VARCHAR(255) DEFAULT NULL COMMENT '文档显示名称',
  original_name      VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  source_object      VARCHAR(512) DEFAULT NULL COMMENT 'MinIO源文件对象',
  md_object          VARCHAR(512) DEFAULT NULL COMMENT 'MinIO Markdown对象',
  asset_root         VARCHAR(512) DEFAULT NULL COMMENT 'MinIO资源包前缀',
  source_package_object VARCHAR(512) DEFAULT NULL COMMENT 'MinIO原始资源包对象',
  status             VARCHAR(32) DEFAULT 'pending' COMMENT '清理状态: pending/cleaned/failed',
  error_msg          TEXT DEFAULT NULL COMMENT '清理失败原因',
  cleaned_at         DATETIME DEFAULT NULL COMMENT '清理完成时间',
  create_by          VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  create_time        DATETIME DEFAULT NULL COMMENT '创建时间',
  update_time        DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_biz_ai5g_docfile_tombstone_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI5G 文档删除清理标记';
