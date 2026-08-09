<template>
  <div class="roomops-home">
    <div class="page-title">机房运维</div>
    <a-row :gutter="16">
      <a-col :span="8" v-for="item in modules" :key="item.key">
        <a-card :title="item.title" :bordered="true">
          <p>{{ item.desc }}</p>
          <a-tag color="blue">{{ item.tag }}</a-tag>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
  const modules = [
    { key: 'room', title: '机房列表', desc: '维护机房编号、名称、专业、地市和备注。', tag: 'biz_roomops_machine_room' },
    { key: 'record', title: '业务记录', desc: '展示巡检、故障处理、工程施工主记录。', tag: 'biz_roomops_record' },
    { key: 'photo', title: '照片明细', desc: '展示照片文件、照片级定位和备注。', tag: 'biz_roomops_photo' },
    { key: 'user', title: '钉钉用户', desc: '维护人员身份和默认专业、地市。', tag: 'biz_roomops_dingtalk_user' },
    { key: 'sync', title: '同步日志', desc: '查看 VPS 前置服务同步状态。', tag: 'biz_roomops_sync_log' },
  ];
</script>

<style scoped>
  .roomops-home {
    padding: 16px;
  }

  .page-title {
    margin-bottom: 16px;
    font-size: 18px;
    font-weight: 600;
  }
</style>
