<template>
  <RoomopsCrudTable ref="tableRef" title="钉钉用户" api-base="/roomops/dingtalkUser" :columns="columns" :fields="fields" edit-auth="roomops:dingtalkUser:edit">
    <template #toolbar-extra>
      <a-button :loading="syncing" @click="syncUsers">同步用户数据</a-button>
    </template>
  </RoomopsCrudTable>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { defHttp } from '/@/utils/http/axios';
  import { message } from 'ant-design-vue';
  import RoomopsCrudTable from '../components/RoomopsCrudTable.vue';

  const tableRef = ref();
  const syncing = ref(false);

  const columns = [
    { title: '姓名', dataIndex: 'name', key: 'name', width: 120 },
    { title: '钉钉用户ID', dataIndex: 'dingtalkUserid', key: 'dingtalkUserid', width: 180, ellipsisLength: 16 },
    { title: 'UnionId', dataIndex: 'dingtalkUnionid', key: 'dingtalkUnionid', width: 240, ellipsisLength: 18 },
    { title: '通讯录同步', dataIndex: 'dingtalkSynced', key: 'dingtalkSynced', width: 110, valueMap: { 0: '否', 1: '是' } },
    { title: '默认专业', dataIndex: 'defaultDomainName', key: 'defaultDomainName', width: 100 },
    { title: '专业简写', dataIndex: 'defaultDomainShortCode', key: 'defaultDomainShortCode', width: 90 },
    { title: '默认地市', dataIndex: 'defaultRegionName', key: 'defaultRegionName', width: 90 },
    { title: '最近登录', dataIndex: 'lastLoginTime', key: 'lastLoginTime', width: 160 },
    { title: '状态', dataIndex: 'active', key: 'active', width: 70 },
  ];

  const fields = [
    { name: 'name', label: '姓名' },
    { name: 'dingtalkUserid', label: '钉钉用户ID' },
    { name: 'dingtalkUnionid', label: '钉钉UnionId' },
    { name: 'mobile', label: '手机号' },
    { name: 'deptId', label: '部门ID' },
    { name: 'deptName', label: '部门名称' },
    { name: 'defaultDomainCode', label: '默认专业编码', defaultValue: 'core_network' },
    { name: 'defaultDomainShortCode', label: '默认专业简写', defaultValue: 'CORE' },
    { name: 'defaultDomainName', label: '默认专业名称', defaultValue: '核心网' },
    { name: 'defaultRegionCode', label: '默认地市编码', defaultValue: 'TY' },
    { name: 'defaultRegionName', label: '默认地市名称', defaultValue: '太原' },
    { name: 'active', label: '状态', defaultValue: '1' },
    { name: 'dingtalkSynced', label: '通讯录同步标志', type: 'number', defaultValue: 0 },
    { name: 'lastLoginTime', label: '最近登录时间', type: 'datetime' },
    { name: 'lastSyncTime', label: '最近同步时间', type: 'datetime' },
    { name: 'avatar', label: '头像', span: 24 },
  ];

  async function syncUsers() {
    syncing.value = true;
    try {
      const result: any = await defHttp.post({ url: '/roomops/dingtalkUser/sync' });
      message.success(
        `同步完成：部门 ${result?.departmentCount || 0} 个，用户 ${result?.fetchedUserCount || 0} 人，新增 ${result?.createdCount || 0}，更新 ${result?.updatedCount || 0}`
      );
      await tableRef.value?.load?.();
    } catch (e: any) {
      message.error(e?.message || '同步用户数据失败');
    } finally {
      syncing.value = false;
    }
  }
</script>
