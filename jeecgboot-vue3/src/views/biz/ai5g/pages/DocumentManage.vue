<template>
  <div class="doc-manage">
    <div class="toolbar">
      <a-space>
        <TypeCascader v-model="typeSel" @change="onTypeChange" />
        <a-input v-model:value="q.title" placeholder="名称" style="width:200px" />
        <a-input v-model:value="q.fileYear" placeholder="年份" style="width:120px" />
        <a-button type="primary" @click="load">查询</a-button>
      </a-space>
    </div>
    <a-table :columns="cols" :data-source="rows" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key==='latest'">
          <a-tag v-if="record.latest" color="blue">最新</a-tag>
          <span v-else>—</span>
        </template>
        <template v-if="column.key==='action'">
          <a-space>
            <a-button type="link" @click="openPreview(record)">预览</a-button>
            <a-button type="link" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除?" @confirm="() => del(record)">
              <a-button type="link">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="editOpen" title="编辑文档" @ok="submitEdit" @cancel="editOpen=false" :width="720" :bodyStyle="{ padding: '16px 24px' }">
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="显示名称">
          <a-input v-model:value="editForm.displayName" />
        </a-form-item>
        <a-form-item label="年份">
          <a-input v-model:value="editForm.fileYear" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="editForm.remark" />
        </a-form-item>
        <a-form-item label="状态">
          <a-input v-model:value="editForm.processStatus" placeholder="uploaded/converted/rag 等" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="previewOpen"
      :footer="null"
      :width="isFull ? '100vw' : 1200"
      :style="modalStyle"
      :wrapClassName="isFull ? 'doc-preview doc-preview-full' : 'doc-preview'"
      :bodyStyle="modalBodyStyle"
      @cancel="closePreview"
    >
      <template #title>
        <div class="modal-title" @mousedown="onTitleMouseDown">
          <span>{{ currentTitle || '预览' }}</span>
          <a-button type="link" @click="toggleFull">{{ isFull ? '退出全屏' : '全屏' }}</a-button>
        </div>
      </template>
      <div class="preview-container">
        <div v-if="isImage">
          <img :src="previewLink" style="max-width:100%; max-height:100%" />
        </div>
        <div v-else-if="isPdf">
          <iframe :src="pdfViewerLink" class="preview-iframe"></iframe>
        </div>
        <div v-else-if="isOffice">
          <div style="padding:16px">
            <p>当前类型暂不支持内嵌预览，请下载查看。</p>
            <a-button type="primary" :href="previewLink" download>下载文件</a-button>
          </div>
        </div>
        <div v-else-if="isMarkdown">
          <div class="markdown-body" v-html="mdHtml" style="height:100%;overflow:auto"></div>
        </div>
        <div v-else-if="isDocx">
          <div class="docx-body" v-html="docxHtml" style="height:100%;overflow:auto;padding:16px"></div>
        </div>
        <div v-else-if="isText">
          <pre class="preview-pre" style="height:100%;overflow:auto">{{ textContent }}</pre>
        </div>
        <div v-else>
          <iframe :src="previewLink" style="width:100%;height:100%;border:0"></iframe>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import TypeCascader from '../components/TypeCascader.vue';
import { pageDoc } from '../api/docfile.api';
import { removeDoc, updateDoc, previewUrl } from '../api/docmanage.api';
import { message } from 'ant-design-vue';
import MarkdownIt from 'markdown-it';
import { defHttp } from '/@/utils/http/axios';
import mammoth from 'mammoth';
import { filterXSS } from 'xss';

const typeSel = ref<{ l1?: string; l2?: string; l3?: string }>({});
const q = ref<{ title?: string; fileYear?: string }>({ title: '', fileYear: '' });
const rows = ref<any[]>([]);

const cols = [
  { title: '名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '类型', dataIndex: 'fileType', key: 'fileType' },
  { title: '版本', dataIndex: 'version', key: 'version' },
  { title: 'latest', dataIndex: 'latest', key: 'latest' },
  { title: '上传时间', dataIndex: 'uploadTime', key: 'uploadTime' },
  { title: '类别路径', dataIndex: 'categoryPath', key: 'categoryPath' },
  { title: '年份', dataIndex: 'fileYear', key: 'fileYear' },
  { title: '状态', dataIndex: 'processStatus', key: 'processStatus' },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'action' },
];

function onTypeChange(v: { l1?: string; l2?: string; l3?: string }) { typeSel.value = v; }

async function load() {
  try {
    const r = await pageDoc({
      pageNo: 1,
      pageSize: 100,
      typeCode1: typeSel.value.l1,
      typeCode2: typeSel.value.l2,
      typeCode3: typeSel.value.l3,
      title: q.value.title || undefined,
      fileYear: q.value.fileYear ? Number(q.value.fileYear) : undefined,
    } as any);
    rows.value = r?.records || r;
  } catch (e: any) {
    message.error(e?.message || '查询失败');
  }
}

const previewOpen = ref(false);
const previewLink = ref('');
const textContent = ref('');
const mdHtml = ref('');
const docxHtml = ref('');
const currentType = ref('');
const currentTitle = ref('');
const isFull = ref(false);
const modalStyle = computed(() => (isFull.value ? { top: '0', height: '100vh' } : {}));
const modalBodyStyle = computed(() => ({ padding: 0, height: isFull.value ? 'calc(100vh - 50px)' : '80vh' }));
const pdfViewerLink = computed(() => (previewLink.value ? `${previewLink.value}#page=1&zoom=page-width` : ''));
const md = new MarkdownIt({ linkify: true, breaks: true });
let objectUrl: string | null = null;

const isImage = computed(() => ['png','jpg','jpeg','gif','bmp','webp'].includes(currentType.value.toLowerCase()));
const isPdf = computed(() => currentType.value.toLowerCase() === 'pdf');
const isMarkdown = computed(() => ['md','markdown'].includes(currentType.value.toLowerCase()));
const isDocx = computed(() => currentType.value.toLowerCase() === 'docx');
const isText = computed(() => ['txt','log','csv'].includes(currentType.value.toLowerCase()));
const isOffice = computed(() => ['doc','ppt','pptx','xls','xlsx'].includes(currentType.value.toLowerCase()));

async function openPreview(rec: any) {
  try {
    currentType.value = (rec.fileType || '').toLowerCase();
    currentTitle.value = rec.displayName || rec.originalName || '';
    if (isMarkdown.value || isText.value) {
      const resp: any = await defHttp.get({ url: previewUrl(rec.id), responseType: 'blob' }, { isReturnNativeResponse: true, isTransformResponse: false });
      const blob: Blob = resp?.data as Blob;
      const txt = await blob.text();
      if (isMarkdown.value) mdHtml.value = md.render(txt);
      else textContent.value = txt;
      previewLink.value = '';
    } else if (isDocx.value) {
      const resp: any = await defHttp.get({ url: previewUrl(rec.id), responseType: 'blob' }, { isReturnNativeResponse: true, isTransformResponse: false });
      const blob: Blob = resp?.data as Blob;
      const ab = await blob.arrayBuffer();
      const result = await mammoth.convertToHtml({ arrayBuffer: ab }, { convertImage: mammoth.images.inline() });
      const html = result?.value || '文档内容解析失败';
      docxHtml.value = filterXSS(html);
      previewLink.value = '';
    } else {
      const resp: any = await defHttp.get({ url: previewUrl(rec.id), responseType: 'blob' }, { isReturnNativeResponse: true, isTransformResponse: false });
      const blob: Blob = resp?.data as Blob;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = URL.createObjectURL(blob);
      previewLink.value = objectUrl;
    }
    previewOpen.value = true;
  } catch (e: any) {
    message.error(e?.message || '预览加载失败');
  }
}

function closePreview() {
  previewOpen.value = false;
  textContent.value = '';
  mdHtml.value = '';
  docxHtml.value = '';
  isFull.value = false;
  currentTitle.value = '';
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }
}

function toggleFull() {
  isFull.value = !isFull.value;
}

function onTitleMouseDown(e: MouseEvent) {
  if (isFull.value) return;
  const wrap = document.querySelector('.doc-preview .ant-modal') as HTMLElement | null;
  if (!wrap) return;
  const match = /translate\(([-0-9.]+)px,\s*([-0-9.]+)px\)/.exec(wrap.style.transform || '');
  const baseX = match ? parseFloat(match[1]) : 0;
  const baseY = match ? parseFloat(match[2]) : 0;
  const startX = e.clientX;
  const startY = e.clientY;
  const onMove = (ev: MouseEvent) => {
    const dx = ev.clientX - startX;
    const dy = ev.clientY - startY;
    wrap.style.transform = `translate(${baseX + dx}px, ${baseY + dy}px)`;
  };
  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
  };
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

async function del(rec: any) {
  try { await removeDoc(rec.id); message.success('删除成功'); load(); } catch (e: any) { message.error(e?.message || '删除失败'); }
}

const editOpen = ref(false);
const editForm = ref<{ id?: string; displayName?: string; remark?: string; fileYear?: string | number; processStatus?: string }>({});
function openEdit(rec: any) { editForm.value = { id: rec.id, displayName: rec.displayName, remark: rec.remark, fileYear: rec.fileYear, processStatus: rec.processStatus }; editOpen.value = true; }
async function submitEdit() {
  try {
    await updateDoc({ id: editForm.value.id!, displayName: editForm.value.displayName, remark: editForm.value.remark, fileYear: editForm.value.fileYear ? Number(editForm.value.fileYear) : undefined, processStatus: editForm.value.processStatus });
    message.success('保存成功');
    editOpen.value = false;
    load();
  } catch (e: any) { message.error(e?.message || '保存失败'); }
}

load();
</script>

<style scoped>
.doc-manage { padding: 12px; }
.toolbar { margin-bottom: 12px; }
.preview-container { padding: 0; height: 100%; display: flex; position: relative; }
.preview-iframe { position: absolute; top: 0; left: 0; right: 0; bottom: 0; width: 100%; height: 100%; border: 0; }
.doc-preview :deep(.ant-modal) { max-width: 100vw; }
.doc-preview :deep(.ant-modal-content) { display: flex; flex-direction: column; }
.doc-preview :deep(.ant-modal-body) { flex: 1; overflow: hidden; padding: 0; }
.doc-preview-full :deep(.ant-modal-content) { height: 100vh; }
.doc-preview-full :deep(.ant-modal-body) { height: calc(100vh - 50px); }
.preview-pre { white-space: pre-wrap; font-family: monospace; }
.markdown-body { padding: 16px; }
.docx-body :deep(img) { max-width: 100%; height: auto; }
.docx-body { background: #fff; }
.modal-title { display: flex; align-items: center; justify-content: space-between; }
</style>
