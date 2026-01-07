<template>
  <div class="type-manage">
    <div class="toolbar">
      <a-space>
        <a-select v-model:value="qLevel" style="width: 160px" placeholder="层级">
          <a-select-option :value="1">一级</a-select-option>
          <a-select-option :value="2">二级</a-select-option>
          <a-select-option :value="3">三级</a-select-option>
        </a-select>
        <a-input v-model:value="qParent" style="width: 200px" placeholder="父级代码(可选)" />
        <a-button type="primary" @click="load">查询</a-button>
        <a-button @click="openAdd">新增</a-button>
      </a-space>
    </div>
    <a-table :columns="columns" :data-source="rows" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key==='action'">
          <a-space>
            <a-popconfirm title="确定删除?" @confirm="() => del(record)">
              <a-button type="link">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="addOpen" title="新增类型" @ok="submitAdd" @cancel="addOpen=false">
      <a-form :model="addForm" layout="vertical">
        <a-form-item label="层级">
          <a-select v-model:value="addForm.level">
            <a-select-option :value="1">一级</a-select-option>
            <a-select-option :value="2">二级</a-select-option>
            <a-select-option :value="3">三级</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="代码">
          <a-input v-model:value="addForm.code" placeholder="如 01/0101/010101" />
        </a-form-item>
        <a-form-item label="父级代码" v-if="addForm.level>1">
          <a-input v-model:value="addForm.parentCode" placeholder="如 01 或 0101" />
        </a-form-item>
        <a-form-item label="名称">
          <a-input v-model:value="addForm.name" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
  </template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { listTypes, saveType, removeType, type DocTypeItem } from '../api/doctype.api';
import { message } from 'ant-design-vue';

const qLevel = ref<number>(1);
const qParent = ref<string>('');
const rows = ref<DocTypeItem[]>([]);

const columns = [
  { title: '层级', dataIndex: 'level', key: 'level' },
  { title: '代码', dataIndex: 'code', key: 'code' },
  { title: '父级', dataIndex: 'parentCode', key: 'parentCode' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '操作', key: 'action' },
];

async function load() {
  try {
    rows.value = await listTypes({ level: qLevel.value, parentCode: qParent.value || undefined });
  } catch (e: any) {
    message.error(e?.message || '加载失败');
    rows.value = [];
  }
}

onMounted(load);

const addOpen = ref(false);
const addForm = ref<DocTypeItem>({ level: 1, code: '', name: '', parentCode: '' });

function openAdd() { addForm.value = { level: 1, code: '', name: '', parentCode: '' }; addOpen.value = true; }

async function submitAdd() {
  try {
    await saveType(addForm.value);
    message.success('保存成功');
    addOpen.value = false;
    await load();
  } catch (e: any) {
    message.error(e?.message || '保存失败');
  }
}

async function del(rec: DocTypeItem) {
  try {
    if (!rec.id) return;
    await removeType(rec.id);
    message.success('删除成功');
    await load();
  } catch (e: any) {
    message.error(e?.message || '删除失败');
  }
}
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
</style>
