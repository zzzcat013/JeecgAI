-- ToC 随行专网结构化查询模型。原始申请/反馈文档导入后，MCP 优先查询汇总视图和查询指南。
CREATE TABLE IF NOT EXISTS `biz_5g_toc_project` (
  `id` varchar(36) NOT NULL COMMENT '主键', `project_code` varchar(64) NOT NULL COMMENT '稳定项目编码',
  `project_name` varchar(160) NOT NULL COMMENT '随行专网项目名称', `customer_name` varchar(160) NOT NULL COMMENT '客户名称',
  `customer_type` varchar(64) DEFAULT NULL COMMENT '客户行业类型', `customer_address` varchar(255) DEFAULT NULL COMMENT '客户地址',
  `region` varchar(128) DEFAULT NULL COMMENT '客户归属地区', `branch_company` varchar(128) DEFAULT NULL COMMENT '申请开通分公司',
  `customer_contact` varchar(64) DEFAULT NULL COMMENT '客户联系人', `customer_phone` varchar(32) DEFAULT NULL COMMENT '客户联系电话',
  `company_contact` varchar(64) DEFAULT NULL COMMENT '分公司联系人', `company_phone` varchar(32) DEFAULT NULL COMMENT '分公司联系电话',
  `business_type` varchar(32) DEFAULT 'MOBILE' COMMENT '业务类型：MOBILE/BROADBAND/MIXED',
  `business_description` text COMMENT '业务简述', `bandwidth` varchar(32) DEFAULT NULL COMMENT '专线带宽，如400M',
  `tariff` varchar(128) DEFAULT NULL COMMENT '套餐资费', `dnn` varchar(128) DEFAULT NULL COMMENT 'APN或DNN域名',
  `service_id` varchar(64) DEFAULT NULL COMMENT 'ServiceID，可保存二进制和十进制表达',
  `upf_name` varchar(128) DEFAULT NULL COMMENT '接入UPF名称', `bearer_network` varchar(64) DEFAULT NULL COMMENT '承载网络，如IPRAN/智能城域网',
  `expected_user_count` int DEFAULT NULL COMMENT '预计发展终端用户数', `online_user_limit` int DEFAULT NULL COMMENT '在线用户数限制，NULL表示不限制',
  `roaming_enabled` tinyint DEFAULT 0 COMMENT '是否支持跨省漫游：1是0否', `msisdn_bind_dnn` tinyint DEFAULT 1 COMMENT '是否需要MSISDN绑定DNN/APN',
  `fixed_ip_required` tinyint DEFAULT 0 COMMENT '是否需要MSISDN绑定固定IP', `requested_open_date` date DEFAULT NULL COMMENT '要求开通日期',
  `project_status` varchar(32) DEFAULT '待开通' COMMENT '项目状态：待开通/开通中/已开通/已停用',
  `source_file` varchar(255) DEFAULT NULL COMMENT '来源文件', `remark` text COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_biz_5g_toc_project_code` (`project_code`), KEY `idx_biz_5g_toc_project_dnn` (`dnn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToC随行专网项目主表，一行代表一个项目，项目数量直接COUNT(*)';

CREATE TABLE IF NOT EXISTS `biz_5g_toc_network_resource` (
  `id` varchar(36) NOT NULL, `project_id` varchar(36) NOT NULL COMMENT '关联项目ID', `project_code` varchar(64) NOT NULL,
  `circuit_no` varchar(64) DEFAULT NULL COMMENT '电路序号或电路编号', `resource_type` varchar(48) NOT NULL COMMENT '资源类型：GRE_OUTER_IP/GRE_INNER_IP/IPRAN_ASG_IP/MAR_IP/VPN/DNN/OTHER',
  `resource_name` varchar(128) NOT NULL COMMENT '资源中文名称', `network_side` varchar(32) DEFAULT NULL COMMENT '资源归属侧：UNICOM/CUSTOMER',
  `resource_value` varchar(255) DEFAULT NULL COMMENT '主用资源值', `backup_value` varchar(255) DEFAULT NULL COMMENT '备用资源值',
  `resource_status` varchar(32) DEFAULT '有效' COMMENT '资源状态', `sort_no` int DEFAULT 0, `remark` text,
  `source_file` varchar(255) DEFAULT NULL, `source_section` varchar(128) DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_biz_5g_toc_resource_project` (`project_id`), KEY `idx_biz_5g_toc_resource_value` (`resource_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToC随行专网网络资源明细，一行代表一个资源项，同项目可有多条';

CREATE TABLE IF NOT EXISTS `biz_5g_toc_route_config` (
  `id` varchar(36) NOT NULL, `project_id` varchar(36) NOT NULL, `project_code` varchar(64) NOT NULL,
  `upf_name` varchar(128) DEFAULT NULL, `route_type` varchar(48) DEFAULT NULL COMMENT '路由类型：TERMINAL_POOL/CUSTOMER_LAN/STATIC_ROUTE',
  `address_pool` varchar(64) DEFAULT NULL COMMENT 'UPF终端地址池CIDR', `destination_cidr` varchar(64) DEFAULT NULL COMMENT '客户内网/目的网段CIDR',
  `next_hop` varchar(64) DEFAULT NULL COMMENT '下一跳', `vrf` varchar(128) DEFAULT NULL COMMENT 'VPN/VRF名称',
  `route_status` varchar(32) DEFAULT '有效', `remark` text, `source_file` varchar(255) DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_biz_5g_toc_route_project` (`project_id`), KEY `idx_biz_5g_toc_route_cidr` (`destination_cidr`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToC随行专网地址池和路由配置，一行代表一个网段或路由';

CREATE TABLE IF NOT EXISTS `biz_5g_toc_doc_fragment` (
  `id` varchar(36) NOT NULL, `project_id` varchar(36) NOT NULL, `project_code` varchar(64) NOT NULL,
  `doc_type` varchar(64) DEFAULT NULL COMMENT '申请表/网管反馈/GRE参数/资源申请/配置说明', `title` varchar(255) DEFAULT NULL,
  `content` text NOT NULL COMMENT '用于全文检索或同步RAG的完整语义片段', `keywords` varchar(512) DEFAULT NULL,
  `source_file` varchar(255) DEFAULT NULL, `source_section` varchar(128) DEFAULT NULL, `sort_no` int DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`), KEY `idx_biz_5g_toc_doc_project` (`project_id`),
  FULLTEXT KEY `ft_biz_5g_toc_doc` (`title`,`content`,`keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToC随行专网文档片段，用于说明检索和RAG，不用于数值统计';

CREATE TABLE IF NOT EXISTS `biz_5g_toc_query_guide` (
  `id` varchar(36) NOT NULL, `table_name` varchar(128) NOT NULL, `business_name` varchar(128) NOT NULL,
  `data_grain` varchar(255) NOT NULL COMMENT '一行数据代表什么', `default_rule` text COMMENT '默认统计和过滤规则',
  `join_rule` text COMMENT '推荐关联规则', `example_question` varchar(512) DEFAULT NULL, `example_sql` text,
  `priority` int DEFAULT 100, `enabled` tinyint DEFAULT 1, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_biz_5g_toc_guide_table` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库插件/MCP查询语义指南；生成SQL前优先读取本表';

CREATE OR REPLACE VIEW `biz_5g_toc_project_overview_view` AS
SELECT p.*, COUNT(DISTINCT nr.id) AS resource_count, COUNT(DISTINCT rc.id) AS route_count
FROM biz_5g_toc_project p
LEFT JOIN biz_5g_toc_network_resource nr ON nr.project_id=p.id
LEFT JOIN biz_5g_toc_route_config rc ON rc.project_id=p.id
GROUP BY p.id;

INSERT INTO biz_5g_toc_query_guide(id,table_name,business_name,data_grain,default_rule,join_rule,example_question,example_sql,priority)
VALUES
('toc-guide-project','biz_5g_toc_project_overview_view','随行专网项目汇总','一行代表一个ToC随行专网项目','统计项目数量直接COUNT(*)；预计用户数使用SUM(expected_user_count)；不要从资源明细表统计项目数','项目明细优先直接查询本视图','有多少个已开通随行专网项目？','SELECT COUNT(*) FROM biz_5g_toc_project_overview_view WHERE project_status=''已开通''',10),
('toc-guide-resource','biz_5g_toc_network_resource','网络资源明细','一行代表一个网络资源项，同一项目有多条','统计资源项可COUNT(*)；统计项目必须COUNT(DISTINCT project_id)','通过project_id关联biz_5g_toc_project','某项目GRE隧道内外层IP是什么？','SELECT resource_name,network_side,resource_value FROM biz_5g_toc_network_resource WHERE project_code=?',20),
('toc-guide-route','biz_5g_toc_route_config','地址池与路由','一行代表一个终端地址池、客户网段或静态路由','网段数量可COUNT(*)；项目数量必须COUNT(DISTINCT project_id)','通过project_id关联biz_5g_toc_project','某项目分配了哪些终端地址池？','SELECT upf_name,address_pool,destination_cidr FROM biz_5g_toc_route_config WHERE project_code=?',30),
('toc-guide-doc','biz_5g_toc_doc_fragment','文档检索片段','一行代表来源文档中的一个语义片段','只用于说明、流程、反馈内容检索，不用于项目数、资源数等统计','通过project_id关联biz_5g_toc_project','查询某项目的网管反馈说明','SELECT title,content FROM biz_5g_toc_doc_fragment WHERE project_code=? AND content LIKE ?',40)
ON DUPLICATE KEY UPDATE business_name=VALUES(business_name),data_grain=VALUES(data_grain),default_rule=VALUES(default_rule),join_rule=VALUES(join_rule),example_question=VALUES(example_question),example_sql=VALUES(example_sql),priority=VALUES(priority);
