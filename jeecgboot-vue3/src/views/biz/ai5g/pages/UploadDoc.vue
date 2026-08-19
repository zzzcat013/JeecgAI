<template>
  <div class="upload-doc">
    <a-form :model="form" layout="vertical">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="存储目录预览">
            <a-input :value="previewPath" disabled />
            <div class="help">目录规则：固定子目录 doc + 类型代码分段，如 01/01/01</div>
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="选择类型">
            <TypeCascader v-model="typeSel" @change="onTypeChange" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="显示名称(可选)">
            <a-input v-model:value="form.title" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="文件年份(可选)">
            <a-input v-model:value="form.fileYear" placeholder="如 2026" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="备注(可选)">
            <a-input v-model:value="form.remark" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16" align="middle">
        <a-col :span="16">
          <a-form-item label="选择文件">
            <input ref="fileInputRef" type="file" @change="onFileChange" />
          </a-form-item>
        </a-col>
        <a-col :span="8" class="actions">
          <a-space>
            <a-button type="primary" :disabled="!canSubmit" :loading="uploading" @click="submit">上传</a-button>
          </a-space>
        </a-col>
      </a-row>
    </a-form>

    <div class="list">
      <a-space>
        <a-input v-model:value="q.title" placeholder="名称" style="width: 200px" />
        <a-button @click="loadList">查询</a-button>
      </a-space>
      <a-table :columns="listCols" :data-source="listRows" row-key="id" :pagination="false" style="margin-top:12px">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'latest'">
            <a-tag v-if="record.latest" color="blue">最新</a-tag>
            <span v-else>—</span>
          </template>
        </template>
      </a-table>
    </div>
  </div>
  </template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import TypeCascader from '../components/TypeCascader.vue';
import { uploadDoc, pageDoc } from '../api/docfile.api';
import { message } from 'ant-design-vue';

const form = ref<{ title?: string; fileYear?: string | number; remark?: string }>({ title: '', fileYear: '', remark: '' });
const typeSel = ref<{ l1?: string; l2?: string; l3?: string }>({});
const fileObj = ref<File | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const uploading = ref(false);

function onTypeChange(v: { l1?: string; l2?: string; l3?: string }) { typeSel.value = v; }
function onFileChange(e: Event) { const f = (e.target as HTMLInputElement).files?.[0] || null; fileObj.value = f; }

const previewPath = computed(() => {
  const c3 = typeSel.value.l3 || '';
  if (c3.length === 6) {
    return `doc/${c3.substring(0,2)}/${c3.substring(2,4)}/${c3.substring(4,6)}`;
  }
  return c3 ? `doc/${c3}` : 'doc/—';
});

const canSubmit = computed(() => !!(typeSel.value.l1 && typeSel.value.l2 && typeSel.value.l3 && fileObj.value));

async function submit() {
  try {
    if (!canSubmit.value || !fileObj.value) return;
    uploading.value = true;
    const r = await uploadDoc(fileObj.value, {
      directoryName: 'doc',
      typeCode1: typeSel.value.l1!,
      typeCode2: typeSel.value.l2!,
      typeCode3: typeSel.value.l3!,
      title: form.value.title || undefined,
      fileYear: form.value.fileYear ? Number(form.value.fileYear) : undefined,
      remark: form.value.remark || undefined,
    });
    if (r?.success === true || r?.code === 200) {
      message.success(r?.message || '上传成功');
    } else {
      throw new Error(r?.message || '上传失败');
    }
    fileObj.value = null;
    if (fileInputRef.value) {
      fileInputRef.value.value = '';
    }
    await loadList();
  } catch (e: any) {
    message.error(e?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
}

const q = ref<{ title?: string }>({ title: '' });
const listRows = ref<any[]>([]);
const listCols = [
  { title: '名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '类型', dataIndex: 'fileType', key: 'fileType' },
  { title: '版本', dataIndex: 'version', key: 'version' },
  { title: '最新', dataIndex: 'latest', key: 'latest' },
  { title: '上传时间', dataIndex: 'uploadTime', key: 'uploadTime' },
  { title: '类别路径', dataIndex: 'categoryPath', key: 'categoryPath' },
  { title: '年份', dataIndex: 'fileYear', key: 'fileYear' },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '状态', dataIndex: 'processStatus', key: 'processStatus' },
];

async function loadList() {
  try {
    const r = await pageDoc({ pageNo: 1, pageSize: 50, title: q.value.title || undefined });
    listRows.value = r?.records || r;
  } catch (e: any) {
    message.error(e?.message || '加载列表失败');
    listRows.value = [];
  }
}

loadList();
</script>

<style scoped>
.upload-doc { padding: 12px; }
.list { margin-top: 16px; }
.help { margin-top: 4px; font-size: 12px; color: #888; }
.actions { text-align: right; }
</style>
