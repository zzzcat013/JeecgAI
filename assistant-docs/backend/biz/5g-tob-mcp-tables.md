# ToB 5G专网数据库插件查询说明

## 表用途

`biz_5g_tob_cpe_sim_view`

现场查询优先使用这个视图。它已按 `project_id + service_type + fixed_ip` 关联 CPE 配置和 SIM 卡信息，适合回答：

- 某个固定 IP 对应哪个项目、车辆、CPE、SIM 卡；
- 某辆车的 CPE 登录地址、录像机地址、ICCID、IMSI、MSISDN；
- 某个 ICCID/MSISDN 对应哪个固定 IP 和 CPE。

`biz_5g_tob_cpe_config`

CPE 原始配置表。适合按项目、业务类型、车辆编号、固定 IP、IMEI、ICCID 查询 CPE 配置。

`biz_5g_tob_sim_card`

SIM 卡信息表。适合按固定 IP、ICCID、IMSI、MSISDN、资费计划、通信计划查询卡信息。

`biz_5g_tob_camera_config`

视频监控摄像头配置表。适合查询车辆摄像头数量、摄像头 IP 范围、拟配置 IP 范围、映射端口和 AR 放通端口。

`biz_5g_tob_doc_fragment`

配置说明文档片段表。适合检索 DNN、LAC、账号密码、联系人、DNAT、VXLAN、链路检测、操作步骤等说明类内容。

## 查询规则

- CPE 与 SIM 卡关联字段：`project_id + service_type + fixed_ip`。
- 固定 IP 是现场查询的主入口字段。
- ToB 项目编码：
  - `qxyx`：清徐亚鑫5G专网
  - `djc`：电机厂5G专网
- 业务类型：
  - `PLC`：PLC通信
  - `VIDEO`：视频监控
  - `GENERAL`：通用CPE
- 仅生成 `SELECT` 查询，不生成增删改 SQL。

## 示例 SQL

按固定 IP 查 CPE 和 SIM：

```sql
SELECT *
FROM biz_5g_tob_cpe_sim_view
WHERE fixed_ip = '10.250.12.168';
```

按车辆编号查配置：

```sql
SELECT *
FROM biz_5g_tob_cpe_sim_view
WHERE project_code = 'qxyx'
  AND vehicle_no = 'tjc-3';
```

按 ICCID 查卡和设备：

```sql
SELECT *
FROM biz_5g_tob_cpe_sim_view
WHERE iccid = '8986062236005475703H';
```

查未匹配到 CPE 的 SIM 卡：

```sql
SELECT s.*
FROM biz_5g_tob_sim_card s
LEFT JOIN biz_5g_tob_cpe_config c
  ON c.project_id = s.project_id
 AND c.service_type = s.service_type
 AND c.fixed_ip = s.fixed_ip
WHERE c.id IS NULL;
```

查配置说明：

```sql
SELECT project_code, doc_type, title, content
FROM biz_5g_tob_doc_fragment
WHERE content LIKE '%VXLAN%'
   OR content LIKE '%DNN%'
   OR content LIKE '%密码%';
```

## 当前已知数据质量提示

- 清徐亚鑫视频监控 `tjc-3` 的 ICCID 为 `8986062236005475703H`，含字母 `H`，已标记为 `data_status='疑似异常'`。
- 清徐亚鑫 PLC SIM 卡中有 3 个固定 IP 暂未匹配到 CPE：`10.250.12.150`、`10.250.12.156`、`10.250.12.165`。
- 清徐亚鑫视频 SIM 卡中有 1 个固定 IP 暂未匹配到 CPE：`10.250.12.169`。
