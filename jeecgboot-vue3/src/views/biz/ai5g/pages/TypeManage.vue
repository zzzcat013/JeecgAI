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
        <a-button v-if="viewTrail.length" @click="goBack">返回上级</a-button>
        <a-button @click="openAdd(1)">新增一级</a-button>
        <a-button @click="openAdd(2)">新增二级</a-button>
        <a-button @click="openAdd(3)">新增三级</a-button>
      </a-space>
    </div>
    <div class="breadcrumb" v-if="viewTrail.length">
      <span class="breadcrumb-label">当前位置</span>
      <a-breadcrumb>
        <a-breadcrumb-item>
          <a-button type="link" size="small" @click="load">根目录</a-button>
        </a-breadcrumb-item>
        <a-breadcrumb-item v-for="item in viewTrail" :key="item.code">
          {{ item.name }}
        </a-breadcrumb-item>
      </a-breadcrumb>
    </div>
    <a-table :columns="columns" :data-source="rows" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key==='code'">
          <a-button type="link" size="small" class="drill-link" :disabled="!canDrill(record)" @click="drillDown(record)">
            {{ record.code }}
          </a-button>
        </template>
        <template v-else-if="column.key==='name'">
          <a-button type="link" size="small" class="drill-link" :disabled="!canDrill(record)" @click="drillDown(record)">
            {{ record.name }}
          </a-button>
        </template>
        <template v-if="column.key==='action'">
          <a-space size="small">
            <a-button v-if="record.level > 1" type="link" @click="openMove(record)">移动</a-button>
            <a-button type="link" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除?" @confirm="() => del(record)">
              <a-button type="link">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="addOpen" :title="addModalTitle" @ok="submitAdd" @cancel="addOpen=false" :bodyStyle="{ padding: '20px 24px 8px' }">
      <a-form :model="addForm" layout="vertical">
        <a-form-item label="层级">
          <a-input :value="levelText(addForm.level)" disabled />
        </a-form-item>
        <a-form-item label="编码">
          <a-input :value="addForm.code" disabled placeholder="自动生成" />
        </a-form-item>
        <a-form-item label="上级类型" v-if="addForm.level > 1">
          <a-select
            v-model:value="addForm.parentCode"
            placeholder="选择上级类型"
            :options="parentOptions"
            show-search
            option-filter-prop="label"
            @change="handleParentChange"
          />
          <div class="form-tip">编码会按上级编码自动顺延为统一的 2 位段。</div>
        </a-form-item>
        <a-form-item label="名称">
          <a-input v-model:value="addForm.name" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="editOpen" title="编辑类型" @ok="submitEdit" @cancel="editOpen=false" :bodyStyle="{ padding: '20px 24px 8px' }">
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="层级">
          <a-select v-model:value="editForm.level" disabled>
            <a-select-option :value="1">一级</a-select-option>
            <a-select-option :value="2">二级</a-select-option>
            <a-select-option :value="3">三级</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="代码">
          <a-input v-model:value="editForm.code" disabled />
        </a-form-item>
        <a-form-item label="父级代码" v-if="editForm.level>1">
          <a-input v-model:value="editForm.parentCode" disabled />
        </a-form-item>
        <a-form-item label="名称">
          <a-input v-model:value="editForm.name" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="moveOpen"
      title="移动类型"
      ok-text="确认移动"
      @ok="submitMove"
      @cancel="moveOpen=false"
      :bodyStyle="{ padding: '20px 24px 8px' }"
    >
      <a-form :model="moveForm" layout="vertical">
        <a-form-item label="当前类型">
          <a-input :value="`${moveForm.code} - ${moveForm.name}`" disabled />
        </a-form-item>
        <a-form-item label="目标上级类型">
          <a-select
            v-model:value="moveForm.targetParentCode"
            placeholder="选择新的上级类型"
            :options="moveParentOptions"
            show-search
            option-filter-prop="label"
          />
          <div class="form-tip">移动后仅调整分类编码和分类路径，文件存储位置不变。</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
  </template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { listTypes, saveType, removeType, updateType, moveType, type DocTypeItem } from '../api/doctype.api';
import { message } from 'ant-design-vue';

const qLevel = ref<number>(1);
const qParent = ref<string>('');
const rows = ref<DocTypeItem[]>([]);
const allTypes = ref<DocTypeItem[]>([]);
const viewTrail = ref<Pick<DocTypeItem, 'level' | 'code' | 'name'>[]>([]);

const columns = [
  { title: '层级', dataIndex: 'level', key: 'level' },
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '操作', key: 'action' },
];

async function load() {
  viewTrail.value = [];
  await loadRoot();
}

async function loadRoot() {
  try {
    rows.value = await listTypes({ level: qLevel.value, parentCode: qParent.value || undefined });
  } catch (e: any) {
    message.error(e?.message || '加载失败');
    rows.value = [];
  }
}

async function drillDown(rec: DocTypeItem) {
  if (!canDrill(rec)) return;
  viewTrail.value = [...viewTrail.value, { level: rec.level, code: rec.code, name: rec.name }];
  await refreshCurrentList();
}

async function goBack() {
  if (!viewTrail.value.length) return;
  viewTrail.value = viewTrail.value.slice(0, -1);
  await refreshCurrentList();
}

async function refreshCurrentList() {
  try {
    const current = viewTrail.value[viewTrail.value.length - 1];
    if (!current) {
      rows.value = await listTypes({ level: qLevel.value, parentCode: qParent.value || undefined });
      return;
    }
    rows.value = await listTypes({ level: current.level + 1, parentCode: current.code });
  } catch (e: any) {
    message.error(e?.message || '加载失败');
    rows.value = [];
  }
}

function canDrill(rec: DocTypeItem) {
  return rec.level < 3;
}

const addOpen = ref(false);
const addForm = ref<DocTypeItem>({ level: 1, code: '', name: '', parentCode: '' });
const addModalTitle = computed(() => `新增${levelText(addForm.value.level)}类型`);
const parentOptions = computed(() => {
  if (addForm.value.level === 2) {
    return (allTypes.value || [])
      .filter((item) => item.level === 1)
      .map((item) => ({ value: item.code, label: `${item.code} - ${item.name}` }));
  }
  if (addForm.value.level === 3) {
    return (allTypes.value || [])
      .filter((item) => item.level === 2)
      .map((item) => ({ value: item.code, label: `${item.code} - ${item.name}` }));
  }
  return [];
});

async function openAdd(level: number) {
  addForm.value = { level, code: '', name: '', parentCode: '' };
  addOpen.value = true;
  await loadAllTypes();
  if (addForm.value.level > 1 && parentOptions.value.length) {
    addForm.value.parentCode = String(parentOptions.value[0].value);
  }
  syncAddCode();
}

function levelText(level?: number) {
  const map: Record<number, string> = { 1: '一级', 2: '二级', 3: '三级' };
  return level ? map[level] || String(level) : '-';
}

async function submitAdd() {
  try {
    if (!addForm.value.code) {
      message.error('请先选择上级类型或补全编码');
      return;
    }
    await saveType(addForm.value);
    message.success('保存成功');
    addOpen.value = false;
    await loadAllTypes();
    await refreshCurrentList();
  } catch (e: any) {
    message.error(e?.message || '保存失败');
  }
}

const editOpen = ref(false);
const editForm = ref<DocTypeItem>({ id: '', level: 1, code: '', name: '', parentCode: '' } as any);

function openEdit(rec: DocTypeItem) {
  editForm.value = { id: rec.id, level: rec.level, code: rec.code, name: rec.name, parentCode: rec.parentCode } as any;
  editOpen.value = true;
}

async function submitEdit() {
  try {
    if (!editForm.value.id) {
      message.error('缺少ID');
      return;
    }
    await updateType({ id: editForm.value.id as any, name: editForm.value.name });
    message.success('保存成功');
    editOpen.value = false;
    await refreshCurrentList();
  } catch (e: any) {
    message.error(e?.message || '保存失败');
  }
}

async function loadAllTypes() {
  try {
    allTypes.value = await listTypes({});
  } catch (e) {
    allTypes.value = [];
  }
}

interface MoveForm extends DocTypeItem {
  targetParentCode?: string;
}

const moveOpen = ref(false);
const moveForm = ref<MoveForm>({ id: '', level: 1, code: '', name: '', parentCode: '', status: 1, targetParentCode: '' });
const moveParentOptions = computed(() => {
  const targetLevel = Math.max((moveForm.value.level || 1) - 1, 1);
  return (allTypes.value || [])
    .filter((item) => item.level === targetLevel)
    .map((item) => ({ value: item.code, label: `${item.code} - ${item.name}` }));
});

async function openMove(rec: DocTypeItem) {
  await loadAllTypes();
  moveForm.value = {
    id: rec.id,
    level: rec.level,
    code: rec.code,
    name: rec.name,
    parentCode: rec.parentCode,
    targetParentCode: rec.parentCode,
  } as any;
  moveOpen.value = true;
}

async function submitMove() {
  try {
    if (!moveForm.value.id || !moveForm.value.targetParentCode) {
      message.error('请选择目标上级类型');
      return;
    }
    await moveType({ id: moveForm.value.id, targetParentCode: moveForm.value.targetParentCode });
    message.success('移动成功');
    moveOpen.value = false;
    await loadAllTypes();
    await refreshCurrentList();
  } catch (e: any) {
    message.error(e?.message || '移动失败');
  }
}

function handleParentChange() {
  syncAddCode();
}

function syncAddCode() {
  const level = addForm.value.level;
  if (level === 1) {
    addForm.value.parentCode = '';
    addForm.value.code = nextCode(allTypes.value.filter((item) => item.level === 1).map((item) => item.code), 2);
    return;
  }
  if (!addForm.value.parentCode) {
    addForm.value.code = '';
    return;
  }
  const targetLevel = level;
  const prefix = addForm.value.parentCode;
  const siblings = allTypes.value
    .filter((item) => item.level === targetLevel && item.code.startsWith(prefix))
    .map((item) => item.code);
  addForm.value.code = nextCode(siblings, targetLevel * 2, prefix);
}

function nextCode(existingCodes: string[], length: number, prefix = '') {
  const nums = existingCodes
    .map((code) => code.slice(prefix.length))
    .map((suffix) => parseInt(suffix, 10))
    .filter((num) => Number.isFinite(num));
  const max = nums.length ? Math.max(...nums) : 0;
  const next = String(max + 1).padStart(length - prefix.length, '0');
  return `${prefix}${next}`;
}

async function del(rec: DocTypeItem) {
  try {
    if (!rec.id) return;
    await removeType(rec.id);
    message.success('删除成功');
    await loadAllTypes();
    await refreshCurrentList();
  } catch (e: any) {
    message.error(e?.message || '删除失败');
  }
}

onMounted(async () => {
  await loadAllTypes();
  await load();
});
</script>

<style scoped>
.type-manage {
  padding: 12px;
}

.toolbar {
  margin-bottom: 12px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f7f9fc;
  border: 1px solid #e9eef5;
  border-radius: 6px;
}

.breadcrumb-label {
  font-size: 12px;
  color: #86909c;
}

.drill-link {
  padding: 0;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #86909c;
  line-height: 1.4;
}
</style>
