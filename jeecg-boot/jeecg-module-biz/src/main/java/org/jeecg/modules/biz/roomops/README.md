# 机房运维模块

本模块承载机房巡检、工程施工、故障处理三类业务。

完整说明见 [docs/机房运维模块说明.md](docs/机房运维模块说明.md)，SQL 归拢脚本见 `sql/biz_roomops_install_all.sql`。

## 表名前缀

所有业务表使用 `biz_roomops_` 前缀。

## 当前表

- `biz_roomops_machine_room`：机房基础信息
- `biz_roomops_record`：三类业务共用主记录，差异字段单列
- `biz_roomops_photo`：照片明细、照片级定位和备注
- `biz_roomops_dingtalk_user`：钉钉用户与专业、地市默认选择
- `biz_roomops_sync_log`：VPS 前置接收服务与正式库的数据同步日志

## 基础维度

- 默认专业：`core_network` / `核心网`，编号简写 `CORE`
- 默认地市：`TY` / `太原`

业务记录编号建议包含业务类型、专业简写、地市、机房和时间，例如：

`IR-CORE-TY-TY01ROOM1500-20260806143000`

当前默认测试机房：

- 机房编号：`TY01ROOM1500`
- 机房名称：`一枢纽15楼机房`

## 文件存储

- MinIO 配置项：`jeecg.roomops.minio.bucketName`
- 开发环境默认 bucket：`room-check-docs`

## VPS 主动拉取同步

接口：`POST /roomops/sync/pull`

当前采用 JeecgAI 主动向 VPS 拉取数据的方式：

- VPS 有新数据时通知 JeecgAI：`POST /roomops/sync/notify`
- JeecgAI 调用 VPS：`GET /api/sync/pending`
- JeecgAI 下载照片：`GET /api/sync/photo/{photoId}`
- JeecgAI 同步成功后确认：`POST /api/sync/ack`

JeecgAI 配置项：

- `jeecg.roomops.sync.vpsBaseUrl`
- `jeecg.roomops.sync.pullToken`
- `jeecg.roomops.sync.notifyToken`

VPS 的 notify 只提醒 JeecgAI 有新数据，不传业务数据和照片；正式数据仍由 JeecgAI 主动拉取并写入。
