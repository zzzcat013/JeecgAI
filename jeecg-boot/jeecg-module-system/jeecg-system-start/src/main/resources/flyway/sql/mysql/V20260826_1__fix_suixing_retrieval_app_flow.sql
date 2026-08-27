-- 随行专网项目数据检索智能体仍绑定旧 Chat2BI/数据库插件流程，模型会调用仅管理员可用的 queryTableMetadata。
-- 切到已验证的 AI5G 流程，该流程的 ToC/ToB LLM 节点已绑定 AI5G专网查询插件。

UPDATE `airag_app`
SET `flow_id` = '2082795096418247001',
    `update_by` = 'admin',
    `update_time` = NOW()
WHERE `id` = '2077722659066368002'
  AND `flow_id` = '2077719401256538114';
