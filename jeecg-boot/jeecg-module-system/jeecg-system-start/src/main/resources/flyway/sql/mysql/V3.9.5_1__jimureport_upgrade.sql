-- ---date:20260625-----for: 字典索引修改
ALTER TABLE `jimu_dict`
DROP INDEX `uk_sd_dict_code`,
ADD INDEX `idx_jd_dict_code`(`dict_code`) USING BTREE,
ADD INDEX `idx_jd_tenant_id`(`tenant_id`) USING BTREE,
ADD INDEX `idx_jd_create_by`(`create_by`) USING BTREE,
ADD INDEX `idx_jd_dict_code_tenant_id`(`dict_code`, `tenant_id`) USING BTREE,
ADD INDEX `idx_jd_dict_code_create_by`(`dict_code`, `create_by`) USING BTREE;