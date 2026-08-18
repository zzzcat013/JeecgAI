-- AI5G domain-limited query plugin. This does not modify the built-in database plugin.

CREATE TABLE IF NOT EXISTS `biz_ai5g_query_scope` (
  `scope_code` varchar(64) NOT NULL COMMENT '查询范围编码，如toc/tob',
  `scope_name` varchar(128) NOT NULL COMMENT '查询范围名称',
  `scope_type` varchar(64) NOT NULL DEFAULT 'AI5G' COMMENT '范围类型',
  `description` varchar(512) DEFAULT NULL COMMENT '范围说明',
  `allowed_tables` text NOT NULL COMMENT '允许查询的表/视图，英文逗号分隔',
  `base_prompt` text COMMENT '给大模型使用的范围提示词，可按业务调整',
  `query_rules` text COMMENT '查询规则、关联规则和禁止事项，可按业务调整',
  `examples` text COMMENT '示例问题和示例SQL，可按业务调整',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `sort_no` int NOT NULL DEFAULT 100 COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`scope_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI5G MCP/插件查询范围配置';

INSERT INTO `biz_ai5g_query_scope`
(`scope_code`, `scope_name`, `scope_type`, `description`, `allowed_tables`, `base_prompt`, `query_rules`, `examples`, `enabled`, `sort_no`)
VALUES
('toc', 'ToC随行专网查询', 'AI5G_TOC',
 '面向随行专网项目、客户、DNN、ServiceID、UPF、地址池、路由和文档片段的受限只读查询范围。',
 'biz_5g_toc_project,biz_5g_toc_project_overview_view,biz_5g_toc_network_resource,biz_5g_toc_route_config,biz_5g_toc_doc_fragment,biz_5g_toc_query_guide',
 '你是随行专网数据库查询助手。生成SQL前必须先调用ai5gQueryContext(scopeCode=toc)读取范围上下文、allowedTables、字段和queryRules。优先使用intentQuery处理summary/projects/routes/resources/documents；只有常用意图无法覆盖时才使用safeSelect。',
 '只允许SELECT。项目统计优先使用biz_5g_toc_project_overview_view，该视图主键字段是id，不是project_id。biz_5g_toc_route_config没有project_name，按项目名称查地址池或路由必须JOIN biz_5g_toc_project p ON p.id=r.project_id。资源、路由、文档表都通过project_id关联biz_5g_toc_project；按项目编码可直接使用project_code。',
 '问题：太原科技大学有哪些终端地址池？\n意图查询：intentQuery(scopeCode=toc,intent=routes,keyword=太原科技大学)\nSQL示例：SELECT r.upf_name,r.address_pool,r.destination_cidr FROM biz_5g_toc_route_config r JOIN biz_5g_toc_project p ON p.id=r.project_id WHERE p.project_name LIKE ''%太原科技大学%'' AND r.route_type=''TERMINAL_POOL''',
 1, 10),
('tob', 'ToB专网查询', 'AI5G_TOB',
 '面向ToB 5G专网项目、CPE、SIM卡、摄像头配置和文档片段的受限只读查询范围。',
 'biz_5g_tob_project,biz_5g_tob_cpe_config,biz_5g_tob_sim_card,biz_5g_tob_camera_config,biz_5g_tob_doc_fragment,biz_5g_tob_cpe_sim_view',
 '你是ToB专网数据库查询助手。生成SQL前必须先调用ai5gQueryContext(scopeCode=tob)读取范围上下文、allowedTables、字段和queryRules。优先使用intentQuery处理projects/cpeSim/cameras/documents；只有常用意图无法覆盖时才使用safeSelect。',
 '只允许SELECT。CPE与SIM卡的常规联查优先使用biz_5g_tob_cpe_sim_view。CPE配置与SIM卡按project_id + service_type + fixed_ip关联。项目名称在biz_5g_tob_project或biz_5g_tob_cpe_sim_view里，明细表按project_id关联项目表。',
 '问题：查询某项目固定IP对应的CPE和SIM信息。\n意图查询：intentQuery(scopeCode=tob,intent=cpeSim,keyword=固定IP或项目名)\nSQL示例：SELECT project_name,service_type,vehicle_no,fixed_ip,iccid,msisdn,sim_status FROM biz_5g_tob_cpe_sim_view WHERE fixed_ip LIKE ''%10.%''',
 1, 20)
ON DUPLICATE KEY UPDATE
scope_name=VALUES(scope_name),
scope_type=VALUES(scope_type),
description=VALUES(description),
allowed_tables=VALUES(allowed_tables),
base_prompt=VALUES(base_prompt),
query_rules=VALUES(query_rules),
examples=VALUES(examples),
enabled=VALUES(enabled),
sort_no=VALUES(sort_no);

INSERT INTO `airag_mcp`
(`id`, `icon`, `name`, `descr`, `category`, `type`, `endpoint`, `headers`, `tools`, `status`, `synced`, `metadata`, `create_by`, `create_time`, `update_by`, `update_time`, `sys_org_code`, `tenant_id`)
VALUES
('2078729600000000001', NULL, 'AI5G专网查询插件', '用于查询随行专网、ToB专网及后续预定义范围内的数据库信息；先取范围上下文，再执行受限只读查询。', 'plugin', 'api', '', '{"X-Sign":"true"}',
'[
  {
    "name":"ai5gListQueryScopes",
    "description":"列出AI5G专网查询工具已启用的查询范围。先用它确认scopeCode，再调用ai5gQueryContext。",
    "path":"/ai5g/mcp/domain-query/scopes",
    "method":"GET",
    "enabled":true,
    "parameters":[],
    "responses":[
      {"name":"result[].scopeCode","description":"查询范围编码，如toc/tob","type":"Array"},
      {"name":"result[].scopeName","description":"查询范围名称","type":"Array"},
      {"name":"result[].description","description":"查询范围说明","type":"Array"}
    ]
  },
  {
    "name":"ai5gQueryContext",
    "description":"查询指定scopeCode的业务查询上下文，返回可查询表、字段注释、范围提示词、查询规则和示例。生成SQL前必须先调用本工具。",
    "path":"/ai5g/mcp/domain-query/context",
    "method":"GET",
    "enabled":true,
    "parameters":[
      {"name":"scopeCode","description":"查询范围编码，toc=随行专网，tob=ToB专网；后续可在biz_ai5g_query_scope中新增","type":"String","location":"Query","required":true,"defaultValue":"toc"}
    ],
    "responses":[
      {"name":"result.basePrompt","description":"该范围可调整的提示词","type":"String"},
      {"name":"result.queryRules","description":"该范围查询规则","type":"String"},
      {"name":"result.allowedTables","description":"允许查询的表和视图","type":"Array"},
      {"name":"result.tables[].columns[]","description":"字段、类型、注释和索引信息","type":"Array"}
    ]
  },
  {
    "name":"ai5gIntentQuery",
    "description":"按预定义业务意图执行安全只读查询。ToC支持summary/projects/routes/resources/documents；ToB支持projects/cpeSim/cameras/documents。优先使用本工具，避免模型猜字段。",
    "path":"/ai5g/mcp/domain-query/intentQuery",
    "method":"POST",
    "enabled":true,
    "parameters":[
      {"name":"scopeCode","description":"查询范围编码：toc或tob","type":"String","location":"Body","required":true,"defaultValue":"toc"},
      {"name":"intent","description":"业务意图。toc: summary/projects/routes/resources/documents；tob: projects/cpeSim/cameras/documents","type":"String","location":"Body","required":true,"defaultValue":""},
      {"name":"keyword","description":"关键词，可填项目名、客户名、地址池、固定IP、ICCID、MSISDN、车号等","type":"String","location":"Body","required":false,"defaultValue":""},
      {"name":"projectCode","description":"项目编码，已知时填写可提高准确性","type":"String","location":"Body","required":false,"defaultValue":""},
      {"name":"pageNo","description":"页码，默认1","type":"Number","location":"Body","required":false,"defaultValue":"1"},
      {"name":"pageSize","description":"每页数量，默认20，最大200","type":"Number","location":"Body","required":false,"defaultValue":"20"}
    ],
    "responses":[
      {"name":"result.records","description":"查询结果列表","type":"Array"},
      {"name":"result.executedSql","description":"实际执行的查询模板","type":"String"}
    ]
  },
  {
    "name":"ai5gSafeSelect",
    "description":"在指定scopeCode的allowedTables内执行受限SELECT。仅当ai5gIntentQuery无法覆盖时使用。必须先调用ai5gQueryContext确认字段和规则；禁止查询allowedTables之外的表。",
    "path":"/ai5g/mcp/domain-query/safeSelect",
    "method":"POST",
    "enabled":true,
    "parameters":[
      {"name":"scopeCode","description":"查询范围编码：toc或tob","type":"String","location":"Body","required":true,"defaultValue":"toc"},
      {"name":"sql","description":"只读SELECT SQL。不能包含分号、注释或非SELECT关键字；表必须在allowedTables内。未写LIMIT时工具会自动分页。","type":"String","location":"Body","required":true,"defaultValue":""},
      {"name":"pageNo","description":"页码，默认1","type":"Number","location":"Body","required":false,"defaultValue":"1"},
      {"name":"pageSize","description":"每页数量，默认20，最大200","type":"Number","location":"Body","required":false,"defaultValue":"20"}
    ],
    "responses":[
      {"name":"result.records","description":"查询结果列表","type":"Array"},
      {"name":"result.executedSql","description":"实际执行SQL","type":"String"}
    ]
  }
]', 'enable', 1, '{"tokenParamName":"X-Access-Token","tool_count":4,"authType":"token","tokenParamValue":""}', 'admin', NOW(), 'admin', NOW(), 'A01', NULL)
ON DUPLICATE KEY UPDATE
name=VALUES(name),
descr=VALUES(descr),
category=VALUES(category),
type=VALUES(type),
endpoint=VALUES(endpoint),
headers=VALUES(headers),
tools=VALUES(tools),
status=VALUES(status),
synced=VALUES(synced),
metadata=VALUES(metadata),
update_by=VALUES(update_by),
update_time=NOW();
