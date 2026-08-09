<template>
  <RoomopsCrudTable title="机房" api-base="/roomops/machineRoom" :columns="columns" :fields="fields" edit-auth="roomops:machineRoom:edit" />
</template>

<script setup lang="ts">
  import RoomopsCrudTable from '../components/RoomopsCrudTable.vue';

  const columns = [
    { title: '机房编号', dataIndex: 'roomId', key: 'roomId', width: 140, ellipsisLength: 10 },
    { title: '机房名称', dataIndex: 'roomName', key: 'roomName', width: 180, ellipsisLength: 12 },
    { title: '专业编码', dataIndex: 'domainCode', key: 'domainCode', width: 120 },
    { title: '专业简写', dataIndex: 'domainShortCode', key: 'domainShortCode', width: 90 },
    { title: '专业', dataIndex: 'domainName', key: 'domainName', width: 100 },
    { title: '地市编码', dataIndex: 'regionCode', key: 'regionCode', width: 80 },
    { title: '地市', dataIndex: 'regionName', key: 'regionName', width: 80 },
    { title: '纬度', dataIndex: 'latitude', key: 'latitude', width: 110 },
    { title: '经度', dataIndex: 'longitude', key: 'longitude', width: 110 },
    { title: '允许距离(m)', dataIndex: 'allowedRadiusM', key: 'allowedRadiusM', width: 110 },
    { title: '最大精度(m)', dataIndex: 'maxAccuracyM', key: 'maxAccuracyM', width: 110 },
    { title: '备注', dataIndex: 'remark', key: 'remark', width: 200, ellipsisLength: 14 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 70 },
  ];

  const fields = [
    { name: 'roomId', label: '机房编号' },
    { name: 'roomName', label: '机房名称' },
    { name: 'domainCode', label: '专业编码', defaultValue: 'core_network' },
    { name: 'domainShortCode', label: '专业简写', defaultValue: 'CORE' },
    { name: 'domainName', label: '专业名称', defaultValue: '核心网' },
    { name: 'regionCode', label: '地市编码', defaultValue: 'TY' },
    { name: 'regionName', label: '地市名称', defaultValue: '太原' },
    { name: 'qrCode', label: '二维码内容' },
    { name: 'latitude', label: '机房纬度' },
    { name: 'longitude', label: '机房经度' },
    { name: 'allowedRadiusM', label: '允许距离(米)', defaultValue: 300 },
    { name: 'maxAccuracyM', label: '最大定位精度(米)', defaultValue: 200 },
    { name: 'status', label: '状态', defaultValue: '1' },
    { name: 'remark', label: '备注', type: 'textarea', span: 24 },
  ];
</script>
