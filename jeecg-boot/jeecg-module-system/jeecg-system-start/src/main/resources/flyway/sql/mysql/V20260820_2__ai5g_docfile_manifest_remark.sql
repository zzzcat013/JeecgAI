ALTER TABLE biz_ai5g_docfile
  MODIFY COLUMN asset_manifest LONGTEXT NULL COMMENT 'Markdown资源清单JSON',
  MODIFY COLUMN remark TEXT NULL COMMENT '备注';
