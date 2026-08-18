-- 机房运维：任务删除墓碑表，防止 VPS 删除同步失败时被定时回拉重建
create table if not exists biz_roomops_task_tombstone (
  task_id varchar(64) not null comment '任务编号',
  deleted_at datetime not null comment '删除时间',
  primary key (task_id)
) engine=InnoDB default charset=utf8mb4 comment='机房运维任务删除墓碑';
