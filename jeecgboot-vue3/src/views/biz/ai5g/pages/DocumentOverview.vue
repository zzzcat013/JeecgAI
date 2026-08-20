<template>
  <div class="doc-overview">
    <div class="overview-toolbar">
      <div class="overview-title">
        <h2>上传文档管理概览</h2>
        <span>统计范围：全部上传记录，包含同一文件的历史版本；最新版本以 latest=1 标记</span>
      </div>
      <a-space>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button type="primary" @click="goManage">文档管理</a-button>
      </a-space>
    </div>

    <div class="summary-grid">
      <div v-for="item in summaryCards" :key="item.key" class="summary-card">
        <div class="summary-icon" :style="{ background: item.bg, color: item.color }">
          <component :is="item.icon" />
        </div>
        <div class="summary-main">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-note">{{ item.note }}</div>
        </div>
      </div>
    </div>

    <div class="overview-grid">
      <section class="overview-panel">
        <div class="panel-head">
          <h3>转换状态</h3>
          <span class="panel-sub">同一文件的历史版本也计入数量</span>
        </div>
        <a-table :columns="statusCols" :data-source="statusRows" :pagination="false" size="middle" row-key="statusCode">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'statusCode'">
              <a-tag :color="record.meta.color">{{ record.meta.label }}</a-tag>
            </template>
            <template v-else-if="column.key === 'desc'">
              <span class="desc-cell">{{ record.meta.desc }}</span>
            </template>
          </template>
        </a-table>
      </section>

      <section class="overview-panel">
        <div class="panel-head">
          <h3>文件类型</h3>
          <span class="panel-sub">按扩展名统计</span>
        </div>
        <a-table :columns="fileTypeCols" :data-source="fileTypeRows" :pagination="false" size="middle" row-key="fileType">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileType'">
              <a-tag>{{ record.typeLabel }}</a-tag>
            </template>
            <template v-else-if="column.key === 'totalSize'">
              {{ formatSize(record.totalSize) }}
            </template>
          </template>
        </a-table>
      </section>
    </div>

    <section class="overview-panel">
      <div class="panel-head">
        <h3>分类数量</h3>
        <span class="panel-sub">按 category_path 分组，未设置分类显示为“未分类”</span>
      </div>
      <a-table
        :columns="categoryCols"
        :data-source="categoryRows"
        size="middle"
        row-key="categoryPath"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 个分类` }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'categoryName'">
            <span class="category-name">{{ record.name }}</span>
          </template>
          <template v-else-if="column.key === 'categoryPath'">
            <code v-if="record.pathText" class="path-text">{{ record.pathText }}</code>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'totalSize'">
            {{ formatSize(record.totalSize) }}
          </template>
          <template v-else-if="column.key === 'lastUploadTime'">
            {{ formatDateTime(record.lastUploadTime) }}
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue';
import { docOverview, type DocOverview } from '../api/docfile.api';
import { listTypes } from '../api/doctype.api';

const router = useRouter();
const data = ref<DocOverview | null>(null);
const typeMap = ref<Record<string, string>>({});
const loading = ref(false);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const statusMeta: Record<string, { label: string; color: string; desc: string }> = {
  uploaded: { label: '已上传', color: 'blue', desc: '文件已入库，尚未提交 AI 转 MD。' },
  processing: { label: '处理中', color: 'processing', desc: '正在排队、解析或生成 Markdown，可查看排队数和耗时。' },
  success: { label: '转MD成功', color: 'success', desc: 'Markdown 已生成，可查看 MD 结果或导入知识库。' },
  failed: { label: '转MD失败', color: 'error', desc: '转换未完成，可查看备注后重新提交。' },
  converted: { label: '已转换', color: 'cyan', desc: '历史状态，通常表示已完成转换。' },
  rag: { label: '已导入知识库', color: 'green', desc: '历史状态，通常表示已导入 AI 知识库。' },
  unset: { label: '未设置', color: 'default', desc: '数据库状态为空或未归一化，需结合备注和历史逻辑判断。' },
};

const statusOrder = ['processing', 'success', 'failed', 'uploaded', 'unset'];

const summaryCards = computed(() => {
  const s = data.value?.summary;
  return [
    {
      key: 'total',
      label: '文档记录',
      value: formatNumber(s?.total),
      note: '含历史版本',
      icon: DatabaseOutlined,
      color: '#1677ff',
      bg: '#e6f4ff',
    },
    {
      key: 'latest',
      label: '最新版本',
      value: formatNumber(s?.latest),
      note: 'latest=1',
      icon: FileTextOutlined,
      color: '#08979c',
      bg: '#e6fffb',
    },
    {
      key: 'processing',
      label: '转换处理中',
      value: formatNumber(s?.processing),
      note: '排队或解析',
      icon: ClockCircleOutlined,
      color: '#d46b08',
      bg: '#fff7e6',
    },
    {
      key: 'success',
      label: '转MD成功',
      value: formatNumber(s?.success),
      note: '可查看或导入',
      icon: CheckCircleOutlined,
      color: '#389e0d',
      bg: '#f6ffed',
    },
    {
      key: 'failed',
      label: '转MD失败',
      value: formatNumber(s?.failed),
      note: '可重新提交',
      icon: WarningOutlined,
      color: '#cf1322',
      bg: '#fff1f0',
    },
    {
      key: 'md',
      label: '可用MD',
      value: formatNumber(s?.mdConverted),
      note: 'md_converted=1',
      icon: CloudUploadOutlined,
      color: '#7c3aed',
      bg: '#f5f0ff',
    },
    {
      key: 'size',
      label: '源文件总大小',
      value: formatSize(s?.totalSize),
      note: '按原始上传文件统计',
      icon: FolderOpenOutlined,
      color: '#2f54eb',
      bg: '#f0f5ff',
    },
  ];
});

const statusCols = [
  { title: '状态', dataIndex: 'statusCode', key: 'statusCode', width: 140 },
  { title: '数量', dataIndex: 'docCount', key: 'docCount', width: 90 },
  { title: '说明', key: 'desc' },
];

const statusRows = computed(() => {
  const groups = data.value?.status || [];
  const byCode = new Map<string, number>();
  groups.forEach((item) => byCode.set(item.statusCode, Number(item.docCount || 0)));

  const ordered = [...statusOrder, ...Array.from(byCode.keys()).filter((code) => !statusOrder.includes(code))];
  return ordered.map((code) => {
    const meta = statusMeta[code] || {
      label: code || '未知',
      color: 'default',
      desc: '其他状态，具体含义以备注和业务逻辑为准。',
    };
    return { statusCode: code, docCount: byCode.get(code) || 0, meta };
  });
});

const fileTypeCols = [
  { title: '类型', dataIndex: 'fileType', key: 'fileType', width: 140 },
  { title: '全部', dataIndex: 'docCount', key: 'docCount', width: 80 },
  { title: '最新', dataIndex: 'latestCount', key: 'latestCount', width: 80 },
  { title: '已转MD', dataIndex: 'mdCount', key: 'mdCount', width: 90 },
  { title: '大小', dataIndex: 'totalSize', key: 'totalSize', width: 110 },
];

const fileTypeRows = computed(() =>
  (data.value?.fileTypes || []).map((item) => ({
    ...item,
    typeLabel: item.fileType === 'unknown' ? '未知/未设置' : item.fileType,
  }))
);

const categoryCols = [
  { title: '分类', dataIndex: 'categoryName', key: 'categoryName', width: 220 },
  { title: '分类路径', dataIndex: 'categoryPath', key: 'categoryPath', width: 180 },
  { title: '全部', dataIndex: 'docCount', key: 'docCount', width: 80 },
  { title: '最新', dataIndex: 'latestCount', key: 'latestCount', width: 80 },
  { title: '已转MD', dataIndex: 'mdCount', key: 'mdCount', width: 90 },
  { title: '大小', dataIndex: 'totalSize', key: 'totalSize', width: 110 },
  { title: '最近上传', dataIndex: 'lastUploadTime', key: 'lastUploadTime', width: 180 },
];

const categoryRows = computed(() =>
  (data.value?.categories || []).map((item) => ({
    ...item,
    name: categoryLabel(item.categoryPath),
    pathText: item.categoryPath === 'unclassified' ? '' : item.categoryPath,
  }))
);

function categoryLabel(path: string) {
  if (!path || path === 'unclassified') {
    return '未分类';
  }
  return path
    .split('/')
    .map((code) => typeMap.value[code] || code)
    .join(' / ');
}

function formatNumber(value?: number | null) {
  const n = Number(value || 0);
  return n.toLocaleString('zh-CN');
}

function formatSize(value?: number | null) {
  const n = Number(value || 0);
  if (n <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = n;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size >= 100 ? Math.round(size) : size.toFixed(1)} ${units[unitIndex]}`;
}

function formatDateTime(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function goManage() {
  router.push('/ai5g/doc-manage');
}

async function load() {
  if (loading.value) return;
  loading.value = true;
  try {
    const [overview, types] = await Promise.all([docOverview(), listTypes({})]);
    data.value = overview;
    const map: Record<string, string> = {};
    (types || []).forEach((item) => {
      if (item.code) map[item.code] = item.name;
    });
    typeMap.value = map;
    schedulePolling(Number(overview?.summary?.processing || 0));
  } catch (e: any) {
    message.error(e?.message || '概览加载失败');
    stopPolling();
  } finally {
    loading.value = false;
  }
}

function schedulePolling(processingCount: number) {
  if (processingCount > 0) {
    if (!pollTimer) {
      pollTimer = setInterval(load, 15000);
    }
  } else {
    stopPolling();
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

onMounted(load);
onUnmounted(stopPolling);
</script>

<style scoped>
.doc-overview {
  padding: 16px 20px;
  min-height: calc(100vh - 120px);
}

.overview-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.overview-title h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.overview-title span {
  font-size: 12px;
  color: #888;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 86px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.summary-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  font-size: 20px;
  flex-shrink: 0;
}

.summary-main {
  min-width: 0;
}

.summary-label {
  font-size: 12px;
  color: #666;
}

.summary-value {
  margin-top: 2px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: rgba(0, 0, 0, 0.88);
}

.summary-note {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.overview-panel {
  min-width: 0;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.panel-sub {
  font-size: 12px;
  color: #888;
}

.desc-cell {
  display: block;
  color: #555;
  line-height: 1.5;
}

.category-name {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);
}

.path-text {
  font-size: 12px;
  color: #666;
}

.muted {
  color: #bbb;
}

@media (max-width: 900px) {
  .overview-toolbar {
    flex-direction: column;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
