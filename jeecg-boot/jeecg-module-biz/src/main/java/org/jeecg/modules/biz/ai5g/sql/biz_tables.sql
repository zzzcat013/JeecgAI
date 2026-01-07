-- MySQL DDL：AI5G 文档类型与文档信息（含表与字段注释）

-- 文档类型表（三级层级）
CREATE TABLE IF NOT EXISTS biz_ai5g_doctype (
  id           VARCHAR(64) NOT NULL COMMENT '主键',
  level        INT NOT NULL COMMENT '层级(1/2/3)',
  code         VARCHAR(64) NOT NULL COMMENT '类型代码（目录名使用）',
  name         VARCHAR(255) NOT NULL COMMENT '类型名称（中文展示用）',
  parent_code  VARCHAR(64) DEFAULT NULL COMMENT '父级类型代码',
  status       TINYINT(1) DEFAULT 1 COMMENT '状态(1启用/0停用)',
  PRIMARY KEY (id),
  UNIQUE KEY idx_biz_ai5g_doctype_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI5G 文档类型（三级层级）';

-- 文档信息表（版本、分类路径、处理状态等）
CREATE TABLE IF NOT EXISTS biz_ai5g_docfile (
  id               VARCHAR(64) NOT NULL COMMENT '主键',
  actual_file_name VARCHAR(255) DEFAULT NULL COMMENT '实际文件名（存储文件名）',
  original_name    VARCHAR(255) NOT NULL COMMENT '原始文件名（上传源文件名）',
  display_name     VARCHAR(255) DEFAULT NULL COMMENT '显示文档名（业务展示名称）',
  version          INT NOT NULL COMMENT '版本号（同原始文件名自动+1）',
  upload_time      DATETIME NOT NULL COMMENT '上传时间',
  file_type        VARCHAR(32) NOT NULL COMMENT '文件类型（扩展名，如pdf/docx）',
  category_path    VARCHAR(512) DEFAULT NULL COMMENT '类别标签路径（目录/二级/三级代码）',
  file_year        INT DEFAULT NULL COMMENT '文件年份',
  remark           VARCHAR(512) DEFAULT NULL COMMENT '备注',
  latest           TINYINT(1) DEFAULT 1 COMMENT '是否最新版本',
  process_status   VARCHAR(64) DEFAULT NULL COMMENT '处理状态（uploaded/converted/rag等）',
  content_type     VARCHAR(128) DEFAULT NULL COMMENT '内容类型（MIME）',
  size             BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  storage_path     VARCHAR(512) NOT NULL COMMENT '存储相对路径',
  storage_filename VARCHAR(255) DEFAULT NULL COMMENT '存储文件名（非中文）',
  md_converted     TINYINT(1) DEFAULT 0 COMMENT '是否完成Markdown转换',
  md_path          VARCHAR(512) DEFAULT NULL COMMENT 'Markdown文件存储路径',
  create_by        VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  create_time      DATETIME DEFAULT NULL COMMENT '创建时间',
  update_by        VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  update_time      DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_biz_ai5g_docfile_org (original_name),
  KEY idx_biz_ai5g_docfile_cat (category_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI5G 文档信息文件记录';
