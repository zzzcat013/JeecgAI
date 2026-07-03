-- ToB 5G专网现场查询表（含数据库插件/MCP可读表与字段注释）
-- 说明：
-- 1. 结构化查询走 biz_5g_tob_cpe_config / biz_5g_tob_sim_card / biz_5g_tob_camera_config。
-- 2. 配置说明、操作步骤、账号密码、联系人、DNN/LAC 等文档类内容走 biz_5g_tob_doc_fragment。
-- 3. CPE配置与SIM卡信息按 project_id + service_type + fixed_ip 关联。

CREATE TABLE IF NOT EXISTS `biz_5g_tob_project` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `project_code` varchar(64) NOT NULL COMMENT '项目编码，如 qxyx、djc',
  `project_name` varchar(128) NOT NULL COMMENT '项目名称',
  `customer_name` varchar(128) DEFAULT NULL COMMENT '客户名称',
  `dnn` varchar(128) DEFAULT NULL COMMENT '5G专网DNN',
  `lac` varchar(32) DEFAULT NULL COMMENT '基站LAC值',
  `plc_server_ip` varchar(64) DEFAULT NULL COMMENT 'PLC总控服务器IP',
  `ar_ip` varchar(64) DEFAULT NULL COMMENT 'AR设备IP',
  `source_file` varchar(255) DEFAULT NULL COMMENT '来源Excel文件',
  `remark` text COMMENT '项目备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_5g_tob_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToB 5G专网项目基础信息';

CREATE TABLE IF NOT EXISTS `biz_5g_tob_cpe_config` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `project_id` varchar(36) NOT NULL COMMENT '项目ID，关联biz_5g_tob_project.id',
  `project_code` varchar(64) NOT NULL COMMENT '项目编码，冗余便于查询',
  `service_type` varchar(32) NOT NULL COMMENT '业务类型：PLC=PLC通信，VIDEO=视频监控，GENERAL=通用CPE',
  `vehicle_type` varchar(64) DEFAULT NULL COMMENT '车辆/设备类型，如装煤车、推焦车、拦焦车、熄焦车',
  `vehicle_no` varchar(64) DEFAULT NULL COMMENT '车辆/设备编号，如 zmc-1、tjc-1',
  `placement` varchar(128) DEFAULT NULL COMMENT 'CPE放置位置',
  `wifi_name` varchar(128) DEFAULT NULL COMMENT 'CPE WiFi名称',
  `fixed_ip` varchar(64) NOT NULL COMMENT 'SIM卡固定IP，CPE与SIM卡关联主字段',
  `cpe_login_addr` varchar(64) DEFAULT NULL COMMENT 'CPE登录地址或配置网关地址',
  `recorder_ip` varchar(64) DEFAULT NULL COMMENT '录像机地址',
  `config_ip1` varchar(64) DEFAULT NULL COMMENT 'CPE配置IP1',
  `config_ip2` varchar(64) DEFAULT NULL COMMENT 'CPE配置IP2',
  `interface_ip` varchar(64) DEFAULT NULL COMMENT '接口IP/动态IP',
  `imei` varchar(32) DEFAULT NULL COMMENT 'CPE IMEI',
  `iccid` varchar(32) DEFAULT NULL COMMENT 'CPE内置SIM卡ICCID',
  `imsi` varchar(32) DEFAULT NULL COMMENT 'CPE内置SIM卡IMSI',
  `cpe_serial_no` varchar(64) DEFAULT NULL COMMENT 'CPE序列号/设备序列号',
  `plc_ip` varchar(64) DEFAULT NULL COMMENT 'PLC IP地址',
  `plc_mac` varchar(64) DEFAULT NULL COMMENT 'PLC MAC地址',
  `lac` varchar(32) DEFAULT NULL COMMENT '基站LAC值',
  `data_status` varchar(32) DEFAULT '正常' COMMENT '数据状态：正常/疑似异常',
  `remark` text COMMENT '备注',
  `source_file` varchar(255) DEFAULT NULL COMMENT '来源Excel文件',
  `source_sheet` varchar(128) DEFAULT NULL COMMENT '来源Sheet',
  `source_row` int DEFAULT NULL COMMENT '来源Excel行号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_5g_tob_cpe_fixed_ip` (`fixed_ip`),
  KEY `idx_biz_5g_tob_cpe_project_service` (`project_id`, `service_type`),
  KEY `idx_biz_5g_tob_cpe_vehicle` (`vehicle_type`, `vehicle_no`),
  KEY `idx_biz_5g_tob_cpe_iccid` (`iccid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToB 5G专网CPE配置表，现场按固定IP、车号、ICCID查询CPE配置';

CREATE TABLE IF NOT EXISTS `biz_5g_tob_sim_card` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `project_id` varchar(36) NOT NULL COMMENT '项目ID，关联biz_5g_tob_project.id',
  `project_code` varchar(64) NOT NULL COMMENT '项目编码，冗余便于查询',
  `service_type` varchar(32) NOT NULL COMMENT '业务类型：PLC=PLC通信，VIDEO=视频监控，GENERAL=通用CPE',
  `fixed_ip` varchar(64) NOT NULL COMMENT 'SIM卡固定IP，CPE与SIM卡关联主字段',
  `iccid` varchar(32) DEFAULT NULL COMMENT 'SIM卡ICCID',
  `msisdn` varchar(32) DEFAULT NULL COMMENT 'SIM卡MSISDN/卡号',
  `imsi` varchar(32) DEFAULT NULL COMMENT 'SIM卡IMSI',
  `sim_status` varchar(64) DEFAULT NULL COMMENT 'SIM卡状态',
  `tariff_plan` varchar(128) DEFAULT NULL COMMENT '资费计划',
  `communication_plan` varchar(128) DEFAULT NULL COMMENT '通信计划',
  `operator_account_id` varchar(64) DEFAULT NULL COMMENT '运营商账户ID',
  `add_time` datetime DEFAULT NULL COMMENT '添加日期',
  `source_file` varchar(255) DEFAULT NULL COMMENT '来源Excel文件',
  `source_sheet` varchar(128) DEFAULT NULL COMMENT '来源Sheet',
  `source_row` int DEFAULT NULL COMMENT '来源Excel行号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_5g_tob_sim_fixed_ip` (`fixed_ip`),
  KEY `idx_biz_5g_tob_sim_project_service` (`project_id`, `service_type`),
  KEY `idx_biz_5g_tob_sim_iccid` (`iccid`),
  KEY `idx_biz_5g_tob_sim_msisdn` (`msisdn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToB 5G专网SIM卡信息表，按固定IP关联CPE配置';

CREATE TABLE IF NOT EXISTS `biz_5g_tob_camera_config` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `project_id` varchar(36) NOT NULL COMMENT '项目ID，关联biz_5g_tob_project.id',
  `project_code` varchar(64) NOT NULL COMMENT '项目编码，冗余便于查询',
  `vehicle_name` varchar(64) DEFAULT NULL COMMENT '车辆名称，如1号推焦车',
  `camera_count` int DEFAULT NULL COMMENT '摄像头数量',
  `actual_ip_range` text COMMENT '当前摄像头IP地址或IP范围',
  `planned_ip_range` text COMMENT '实际拟配置IP地址或IP范围',
  `mapping_port` varchar(64) DEFAULT NULL COMMENT '需配置的映射端口号',
  `ar_open_port` varchar(64) DEFAULT NULL COMMENT 'AR放通端口号',
  `remark` text COMMENT '备注',
  `source_file` varchar(255) DEFAULT NULL COMMENT '来源Excel文件',
  `source_sheet` varchar(128) DEFAULT NULL COMMENT '来源Sheet',
  `source_row` int DEFAULT NULL COMMENT '来源Excel行号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_5g_tob_camera_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToB 5G专网视频监控摄像头配置表，保存车辆摄像头数量、IP范围和端口规划';

CREATE TABLE IF NOT EXISTS `biz_5g_tob_doc_fragment` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `project_id` varchar(36) NOT NULL COMMENT '项目ID，关联biz_5g_tob_project.id',
  `project_code` varchar(64) NOT NULL COMMENT '项目编码，冗余便于查询',
  `doc_type` varchar(64) DEFAULT NULL COMMENT '文档类型：视频监控CPE配置说明/PLC通信CPE配置说明/CPE配置说明等',
  `title` varchar(255) DEFAULT NULL COMMENT '片段标题',
  `content` text NOT NULL COMMENT '文档片段内容，可用于RAG或全文检索',
  `keywords` varchar(512) DEFAULT NULL COMMENT '检索关键词，如DNN、LAC、密码、DNAT、VXLAN',
  `source_file` varchar(255) DEFAULT NULL COMMENT '来源Excel文件',
  `source_sheet` varchar(128) DEFAULT NULL COMMENT '来源Sheet',
  `source_row` int DEFAULT NULL COMMENT '来源Excel行号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_5g_tob_doc_project` (`project_id`),
  KEY `idx_biz_5g_tob_doc_type` (`doc_type`),
  FULLTEXT KEY `ft_biz_5g_tob_doc_content` (`title`, `content`, `keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToB 5G专网文档片段表，保存说明、步骤、账号密码、DNN/LAC等，用于RAG检索';

CREATE OR REPLACE VIEW `biz_5g_tob_cpe_sim_view` AS
SELECT
  c.project_code AS `project_code`,
  p.project_name AS `project_name`,
  c.service_type AS `service_type`,
  c.vehicle_type AS `vehicle_type`,
  c.vehicle_no AS `vehicle_no`,
  c.placement AS `placement`,
  c.wifi_name AS `wifi_name`,
  c.fixed_ip AS `fixed_ip`,
  c.cpe_login_addr AS `cpe_login_addr`,
  c.recorder_ip AS `recorder_ip`,
  c.config_ip1 AS `config_ip1`,
  c.config_ip2 AS `config_ip2`,
  c.interface_ip AS `interface_ip`,
  c.imei AS `cpe_imei`,
  COALESCE(c.iccid, s.iccid) AS `iccid`,
  s.msisdn AS `msisdn`,
  COALESCE(c.imsi, s.imsi) AS `imsi`,
  c.cpe_serial_no AS `cpe_serial_no`,
  c.plc_ip AS `plc_ip`,
  c.plc_mac AS `plc_mac`,
  s.sim_status AS `sim_status`,
  s.tariff_plan AS `tariff_plan`,
  s.communication_plan AS `communication_plan`,
  s.operator_account_id AS `operator_account_id`,
  c.data_status AS `data_status`,
  c.remark AS `cpe_remark`
FROM `biz_5g_tob_cpe_config` c
JOIN `biz_5g_tob_project` p ON p.id = c.project_id
LEFT JOIN `biz_5g_tob_sim_card` s
  ON s.project_id = c.project_id
 AND s.service_type = c.service_type
 AND s.fixed_ip = c.fixed_ip;
