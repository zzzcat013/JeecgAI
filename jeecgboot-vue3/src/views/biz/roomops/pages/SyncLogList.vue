<template>
  <RoomopsCrudTable title="同步日志" api-base="/roomops/syncLog" :columns="columns" :fields="fields" edit-auth="roomops:syncLog:edit" />
</template>

<script setup lang="ts">
  import RoomopsCrudTable from '../components/RoomopsCrudTable.vue';
  import { businessTypeColumn, businessTypeField } from '../config/businessType.config';

  const columns = [
    { title: '同步批次号', dataIndex: 'syncBatchId', key: 'syncBatchId', width: 180, ellipsisLength: 14 },
    { title: '前置记录编号', dataIndex: 'sourceRecordId', key: 'sourceRecordId', width: 180, ellipsisLength: 14 },
    { title: '正式记录编号', dataIndex: 'recordId', key: 'recordId', width: 180, ellipsisLength: 14 },
    businessTypeColumn(),
    { title: '同步方向', dataIndex: 'syncDirection', key: 'syncDirection', width: 110, ellipsisLength: 12 },
    { title: '状态', dataIndex: 'syncStatus', key: 'syncStatus', width: 90, ellipsisLength: 12 },
    { title: '重试次数', dataIndex: 'retryCount', key: 'retryCount', width: 90 },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 160 },
    { title: '完成时间', dataIndex: 'finishedAt', key: 'finishedAt', width: 160 },
  ];

  const fields = [
    { name: 'syncBatchId', label: '同步批次号' },
    { name: 'sourceRecordId', label: '前置记录编号' },
    { name: 'recordId', label: '正式记录编号' },
    businessTypeField(),
    { name: 'syncDirection', label: '同步方向', defaultValue: 'vps_to_jeecg' },
    { name: 'syncStatus', label: '同步状态', defaultValue: 'pending' },
    { name: 'retryCount', label: '重试次数', type: 'number', defaultValue: 0 },
    { name: 'startedAt', label: '开始时间', type: 'datetime' },
    { name: 'finishedAt', label: '完成时间', type: 'datetime' },
    { name: 'errorMessage', label: '错误信息', type: 'textarea', span: 24 },
  ];
</script>
