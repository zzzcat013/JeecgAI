-- ai5g 文档类型种子数据（按需执行）

-- 一级：01 ai5g
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L1-01', 1, '01', 'ai5g', NULL, 1);

-- 二级：0101~0106
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L2-0101', 2, '0101', '规范', '01', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L2-0102', 2, '0102', '办公操作', '01', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L2-0103', 2, '0103', '业务办理', '01', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L2-0104', 2, '0104', '项目资料', '01', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L2-0105', 2, '0105', '案例', '01', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L2-0106', 2, '0106', 'QA', '01', 1);

-- 三级示例
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010101', 3, '010101', '专网办理规范', '0101', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010102', 3, '010102', '运维手册', '0101', 1);

INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010201', 3, '010201', '科创', '0102', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010202', 3, '010202', '未命名', '0102', 1);

INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010301', 3, '010301', '随行专网开通', '0103', 1);

INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010401', 3, '010401', '亚鑫', '0104', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010402', 3, '010402', '电机厂', '0104', 1);

INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010501', 3, '010501', '通用案例', '0105', 1);
INSERT INTO biz_ai5g_doctype(id, level, code, name, parent_code, status) VALUES ('L3-010502', 3, '010502', '运维案例', '0105', 1);

