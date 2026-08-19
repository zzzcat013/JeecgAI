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
        <template v-if="column.key==='processStatus'">
          <a-tag v-if="record.processStatus === 'processing'" color="processing">
             <template #icon><loading-outlined /></template> 处理中
          </a-tag>
          <a-tag v-else-if="record.processStatus === 'success'" color="success">成功</a-tag>
          <a-tag v-else-if="record.processStatus === 'failed'" color="error">失败</a-tag>
          <span v-else>{{ record.processStatus }}</span>
        </template>
        <template v-if="column.key==='action'">
          <a-space size="small">
            <a-button type="link" @click="openPreview(record)">预览</a-button>
            <a-button type="link" @click="openEdit(record)">编辑</a-button>
            <a-dropdown>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="md-convert" @click="toMd(record)">AI转MD</a-menu-item>
                  <a-menu-item v-if="record.mdConverted" key="md-result" @click="openMdPreview(record)">MD结果</a-menu-item>
                  <a-menu-item key="import-kb" @click="openImportToKb(record)">导入知识库</a-menu-item>
                  <a-menu-item key="delete">
                    <a-popconfirm title="确定删除?" @confirm="() => del(record)">
                      <a>删除</a>
                    </a-popconfirm>
                  </a-menu-item>
                </a-menu>
              </template>
              <a-button type="link">更多</a-button>
            </a-dropdown>
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
        <a-form-item label="是否最新">
          <a-switch v-model:checked="editForm.latest" :checked-value="true" :un-checked-value="false" />
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
          <a-space>
            <a-button type="link" @click="toggleFull">{{ isFull ? '退出全屏' : '全屏' }}</a-button>
            <a-button type="link" @click="downloadOrigin">下载原件</a-button>
          </a-space>
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
        <div v-else-if="isMarkdownResult">
           <div v-if="!mdEditing" class="markdown-body" v-html="mdHtml" style="height:100%;overflow:auto"></div>
           <a-textarea v-else v-model:value="mdContent" style="width:100%;height:100%;resize:none" />
        </div>
        <div v-else-if="isMarkdown">
          <div class="markdown-body" v-html="mdHtml" style="height:100%;overflow:auto"></div>
        </div>
        <div v-else-if="isDocx">
          <div ref="docxEl" class="docx-body" style="height:100%;overflow:auto;padding:16px"></div>
        </div>
        <div v-else-if="isText">
          <pre class="preview-pre" style="height:100%;overflow:auto">{{ textContent }}</pre>
        </div>
        <div v-else>
          <iframe :src="previewLink" style="width:100%;height:100%;border:0"></iframe>
        </div>
      </div>
    </a-modal>

    <!-- 独立 Markdown 编辑器 -->
    <MarkdownEditor
      v-if="mdEditorShow"
      :id="mdEditorId"
      :title="mdEditorTitle"
      @close="onMdEditorClose"
      @saved="onMdEditorSaved"
    />

    <!-- 导入到知识库 -->
    <a-modal
      v-model:open="importKbOpen"
      title="导入到知识库"
      :footer="null"
      :width="560"
      :bodyStyle="{ padding: '20px 24px' }"
      @cancel="importKbOpen=false"
    >
      <a-form layout="vertical">
        <a-form-item label="选择知识库">
          <a-select v-model:value="selectedKbId" placeholder="请选择知识库" :options="kbOptions" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="importLoading" @click="confirmImportToKb">导入</a-button>
            <a-button @click="importKbOpen=false">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, shallowRef } from 'vue';
import TypeCascader from '../components/TypeCascader.vue';
import { pageDoc } from '../api/docfile.api';
import { removeDoc, updateDoc, previewUrl } from '../api/docmanage.api';
import { message } from 'ant-design-vue';
import MarkdownIt from 'markdown-it';
import { defHttp } from '/@/utils/http/axios';
// mammoth & xss 暂不使用，保留备用
import { renderAsync } from 'docx-preview';
import MarkdownEditor from '../components/MarkdownEditor.vue';
import { LoadingOutlined } from '@ant-design/icons-vue';
import BizKnowledgeDocListModal from '../components/BizKnowledgeDocListModal.vue';
import BizKnowledgeDocTextModal from '../components/BizKnowledgeDocTextModal.vue';
import BizTextDescModal from '../components/BizTextDescModal.vue';
import BizLocalImportModal from '../components/BizLocalImportModal.vue';

const typeSel = ref<{ l1?: string; l2?: string; l3?: string }>({});
const q = ref<{ title?: string; fileYear?: string }>({ title: '', fileYear: '' });
const rows = ref<any[]>([]);

const cols = [
  { title: '名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '类型', dataIndex: 'fileType', key: 'fileType' },
  { title: '版本', dataIndex: 'version', key: 'version' },
  { title: '最新', dataIndex: 'latest', key: 'latest' },
  { title: '上传时间', dataIndex: 'uploadTime', key: 'uploadTime' },
  { title: '类别路径', dataIndex: 'categoryPath', key: 'categoryPath' },
  { title: '年份', dataIndex: 'fileYear', key: 'fileYear' },
  { title: '状态', dataIndex: 'processStatus', key: 'processStatus' },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'action' },
];

function onTypeChange(v: { l1?: string; l2?: string; l3?: string }) { typeSel.value = v; }

// 轮询定时器
let pollTimer: any = null;

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
    
    // 检查是否有正在处理中的任务，如果有则开启轮询
    const hasProcessing = rows.value.some((item: any) => item.processStatus === 'processing');
    if (hasProcessing) {
        startPolling();
    } else {
        stopPolling();
    }
  } catch (e: any) {
    message.error(e?.message || '查询失败');
    stopPolling();
  }
}

function startPolling() {
    if (pollTimer) return;
    pollTimer = setInterval(() => {
        load();
    }, 3000); // 每3秒轮询一次
}

function stopPolling() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
}

// 组件销毁时清除定时器
import { onUnmounted } from 'vue';
onUnmounted(() => {
    stopPolling();
});

const previewOpen = ref(false);
const previewLink = ref('');
const textContent = ref('');
const mdHtml = ref('');
const mdContent = ref('');
const mdEditing = ref(false);
const isMarkdownResult = ref(false);
const docxEl = ref<HTMLElement | null>(null);
const currentType = ref('');
const currentTitle = ref('');
const currentId = ref('');
const isFull = ref(false);
const modalStyle = computed(() => (isFull.value ? { top: '0', height: '100vh' } : {}));
const modalBodyStyle = computed(() => ({ padding: 0, height: isFull.value ? 'calc(100vh - 50px)' : '80vh' }));
const pdfViewerLink = computed(() => (previewLink.value ? `${previewLink.value}#page=1&zoom=page-width` : ''));
const md = new MarkdownIt({ linkify: true, breaks: true });
let objectUrl: string | null = null;
const domainUrl = (window as any)._CONFIG?.domianURL || window.location.origin;

const isImage = computed(() => ['png','jpg','jpeg','gif','bmp','webp'].includes(currentType.value.toLowerCase()));
const isPdf = computed(() => currentType.value.toLowerCase() === 'pdf');
const isMarkdown = computed(() => ['md','markdown'].includes(currentType.value.toLowerCase()));
const isDocx = computed(() => currentType.value.toLowerCase() === 'docx');
const isText = computed(() => ['txt','log','csv'].includes(currentType.value.toLowerCase()));
const isOffice = computed(() => ['doc','ppt','pptx','xls','xlsx'].includes(currentType.value.toLowerCase()));

const previewMdUrl = (id: string) => `${(window as any)._CONFIG['domianURL'] || '/jeecg-boot'}/ai5g/doc/preview-md/${id}`;

function normalizeMarkdownContent(markdown: string) {
  return (markdown || '').replace(/#\s*{\s*domainURL\s*}/g, domainUrl);
}

async function openMdPreview(rec: any) {
  mdEditorId.value = rec.id;
  mdEditorTitle.value = (rec.displayName || rec.originalName || '') + ' (MD结果)';
  mdEditorShow.value = true;
}

async function openPreview(rec: any) {
  try {
    currentType.value = (rec.fileType || '').toLowerCase();
    currentTitle.value = rec.displayName || rec.originalName || '';
    currentId.value = rec.id;
    if (isMarkdown.value || isText.value) {
      const targetUrl = isMarkdown.value ? previewMdUrl(rec.id) : previewUrl(rec.id);
      console.log('Preview URL (Text/MD):', targetUrl);
      const resp: any = await defHttp.get({ url: targetUrl, responseType: 'blob', timeout: 120000 }, { isReturnNativeResponse: true, isTransformResponse: false });
      const blob: Blob = resp?.data as Blob;
      const txt = await blob.text();
      if (isMarkdown.value) mdHtml.value = md.render(normalizeMarkdownContent(txt));
      else textContent.value = txt;
      previewLink.value = '';
    } else if (isDocx.value || isOffice.value) {
      console.log('Preview URL (Office):', previewUrl(rec.id));
      const resp: any = await defHttp.get({ url: previewUrl(rec.id), responseType: 'blob', timeout: 120000 }, { isReturnNativeResponse: true, isTransformResponse: false });

      const ct = String(resp?.headers?.['content-type'] || resp?.headers?.['Content-Type'] || '').toLowerCase();
      const blob: Blob = resp?.data as Blob;
      if (ct.includes('application/pdf')) {
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        objectUrl = URL.createObjectURL(blob);
        previewLink.value = objectUrl;
        currentType.value = 'pdf';
      } else if (isDocx.value) {
        previewLink.value = '';
        if (docxEl.value) {
          docxEl.value.innerHTML = '';
          await renderAsync(blob, docxEl.value, undefined, {
            inWrapper: true,
            ignoreWidth: false,
            ignoreHeight: false,
            className: 'docx-preview',
            breakPages: true,
          });
        }
      } else {
        // Excel/PPT Fallback to download if not PDF
        previewLink.value = URL.createObjectURL(blob);
        // currentType is still office, so it will show fallback download button
      }
    } else {
      const resp: any = await defHttp.get({ url: previewUrl(rec.id), responseType: 'blob', timeout: 120000 }, { isReturnNativeResponse: true, isTransformResponse: false });
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

async function downloadOrigin() {
  try {
    const resp: any = await defHttp.get({ url: `/ai5g/doc/download/${currentId.value}`, responseType: 'blob' }, { isReturnNativeResponse: true, isTransformResponse: false });
    const blob: Blob = resp?.data as Blob;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = currentTitle.value || 'file';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e: any) {
    message.error(e?.message || '下载失败');
  }
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

async function toMd(rec: any) {
  if (rec.processStatus === 'processing') {
    message.warning('当前文档正在处理中，请稍后查看结果');
    return;
  }
  try {
    message.loading({ content: '正在提交AI转MD任务...', key: 'md' });
    const r = await defHttp.post({ url: `/ai5g/doc/convert/${rec.id}` }, { isTransformResponse: false });
    if (r?.success) {
      rec.processStatus = 'processing';
      message.info({ content: r?.message || '转换任务已提交，后台处理中', key: 'md' });
      startPolling();
      load();
    } else {
      message.error({ content: r?.message || '转换失败', key: 'md' });
    }
  } catch (e: any) {
    message.error({ content: e?.message || '请求失败', key: 'md' });
  }
}

const editOpen = ref(false);
const editForm = ref<{ id?: string; displayName?: string; remark?: string; fileYear?: string | number; processStatus?: string; latest?: boolean }>({});
function openEdit(rec: any) { editForm.value = { id: rec.id, displayName: rec.displayName, remark: rec.remark, fileYear: rec.fileYear, processStatus: rec.processStatus, latest: Boolean(rec.latest) }; editOpen.value = true; }
async function submitEdit() {
  try {
    await updateDoc({ id: editForm.value.id!, displayName: editForm.value.displayName, remark: editForm.value.remark, fileYear: editForm.value.fileYear ? Number(editForm.value.fileYear) : undefined, processStatus: editForm.value.processStatus, latest: editForm.value.latest });
    message.success('保存成功');
    editOpen.value = false;
    load();
  } catch (e: any) { message.error(e?.message || '保存失败'); }
}

// 独立 Markdown 编辑器
const mdEditorShow = ref(false);
const mdEditorId = ref('');
const mdEditorTitle = ref('');

// 导入知识库
const importKbOpen = ref(false);
const importLoading = ref(false);
const selectedKbId = ref('');
const kbOptions = ref<{ label: string; value: string }[]>([]);
let pendingImportDoc: { id: string; title: string; content: string } | null = null;



function onMdEditorClose() {
  mdEditorShow.value = false;
}

function onMdEditorSaved() {
  // 可选：刷新列表或提示
  message.success('Markdown 已保存');
}

async function openImportToKb(rec: any) {
  try {
    message.loading({ content: '加载MD内容...', key: 'imp' });
    const resp: any = await defHttp.get({ url: `/ai5g/doc/preview-md/${rec.id}`, responseType: 'blob', timeout: 30000 }, { isReturnNativeResponse: true, isTransformResponse: false });
    const txt = await (resp.data as Blob).text();
    pendingImportDoc = {
      id: rec.id,
      title: rec.displayName || rec.originalName || '文档',
      content: normalizeMarkdownForAirag(normalizeMarkdownContent(txt)),
    };

    // 加载知识库列表
    const list: any = await defHttp.get({ url: '/airag/knowledge/list', params: { pageNo: 1, pageSize: 1000 } }, { isTransformResponse: false });
    const records = list?.result?.records || list?.result || [];
    kbOptions.value = (records || []).map((k: any) => ({ label: k.name || k.title || k.id, value: k.id }));
    selectedKbId.value = kbOptions.value.length ? kbOptions.value[0].value : '';

    importKbOpen.value = true;
    message.destroy('imp');
  } catch (e: any) {
    message.error({ content: e?.message || '加载失败', key: 'imp' });
  }
}

async function confirmImportToKb() {
  if (!selectedKbId.value) { message.warn('请选择知识库'); return; }
  if (!pendingImportDoc) { message.error('缺少文档数据'); return; }
  try {
    importLoading.value = true;
    // 规范标题长度，避免数据库长度约束
    let sanitizedTitle = (pendingImportDoc.title || '文档').trim();
    if (sanitizedTitle.length > 100) sanitizedTitle = sanitizedTitle.slice(0, 100);
    // 检查是否已存在同名文档，存在则更新；否则新增（一次写入完整内容）
    const existList: any = await defHttp.get({ url: '/airag/knowledge/doc/list', params: { knowledgeId: selectedKbId.value, title: sanitizedTitle, pageNo: 1, pageSize: 1 } }, { isTransformResponse: false });
    const existId = existList?.result?.records?.[0]?.id || existList?.result?.[0]?.id;
    const payload: any = {
      id: existId,
      knowledgeId: selectedKbId.value,
      title: sanitizedTitle,
      type: 'md',
      content: pendingImportDoc.content,
    };
    const r: any = await defHttp.post({ url: '/airag/knowledge/doc/edit', data: payload }, { isTransformResponse: false });
    importLoading.value = false;
    if (r?.success) {
      message.success('导入成功');
      importKbOpen.value = false;
    } else {
      message.error(r?.message || '导入失败');
    }
  } catch (e: any) {
    importLoading.value = false;
    message.error(e?.message || '导入失败');
  }
}

function normalizeMarkdownForAirag(markdown: string) {
  if (!markdown) {
    return markdown;
  }
  const imageReg = /!\[([^\]]*)\]\(([^)]+)\)/g;

  return markdown.replace(imageReg, (match, alt, rawUrl) => {
    const url = String(rawUrl || '').trim();
    if (!url) {
      return match;
    }
    const collapsedUrl = url.replace(/^(?:#\{domainURL\}){2,}/, '#{domainURL}');
    if (collapsedUrl.startsWith('#{domainURL}')) {
      return `![${alt}](${collapsedUrl})`;
    }
    if (collapsedUrl.startsWith('http://') || collapsedUrl.startsWith('https://')) {
      const assetIndex = collapsedUrl.indexOf('/ai5g/doc/assets/');
      if (assetIndex >= 0) {
        return `![${alt}](#{domainURL}${collapsedUrl.substring(assetIndex)})`;
      }
      return match;
    }
    if (collapsedUrl.startsWith('/ai5g/doc/assets/')) {
      return `![${alt}](#{domainURL}${collapsedUrl})`;
    }
    return match;
  });
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
