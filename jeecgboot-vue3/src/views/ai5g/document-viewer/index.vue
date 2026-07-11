<template>
  <PageWrapper contentFullHeight>
    <div class="doc-viewer" :style="{ '--left-panel-width': `${leftPanelWidth}px` }">
      <section class="side-panel">
        <div class="panel-title">
          <span>文档分类</span>
          <a-button type="text" size="small" @click="reloadAll">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </div>
        <a-input-search v-model:value="categoryKeyword" placeholder="筛选分类" allow-clear class="category-search" />
        <a-spin :spinning="categoryLoading">
          <a-tree
            block-node
            show-line
            :tree-data="filteredTreeData"
            :selected-keys="selectedCategoryKeys"
            :expanded-keys="expandedKeys"
            @expand="handleExpand"
            @select="handleCategorySelect"
          />
          </a-spin>
      </section>

      <div
        class="splitter-handle"
        title="拖动调整宽度"
        @mousedown.prevent="startResize"
        @dblclick="resetPanelWidth"
      >
        <span></span>
      </div>

      <section class="doc-list-panel">
        <div class="list-toolbar">
          <div>
            <div class="section-title">{{ selectedCategoryTitle }}</div>
            <div class="section-subtitle">共 {{ pagination.total }} 个文档</div>
          </div>
          <a-space>
            <a-input-search
              v-model:value="searchTitle"
              placeholder="搜索文档标题"
              allow-clear
              style="width: 220px"
              @search="handleSearch"
            />
            <a-input-number v-model:value="searchYear" placeholder="年份" :min="1900" :max="2100" style="width: 110px" @pressEnter="handleSearch" />
            <a-button type="primary" @click="handleSearch">
              <template #icon><SearchOutlined /></template>
              查询
            </a-button>
          </a-space>
        </div>

        <a-spin :spinning="docLoading">
          <div class="doc-list">
            <div
              v-for="item in docs"
              :key="item.id"
              :class="['doc-row', { active: selectedDoc?.id === item.id }]"
              @click="selectDoc(item)"
            >
              <div class="doc-row-main">
                <FileTextOutlined class="doc-icon" />
                <div class="doc-text">
                  <div class="doc-title">{{ item.displayName || item.originalName || item.actualFileName || '未命名文档' }}</div>
                  <div class="doc-meta">
                    <span>{{ item.fileType || 'file' }}</span>
                    <span>v{{ item.version || 1 }}</span>
                    <span>{{ formatDate(item.uploadTime) }}</span>
                  </div>
                </div>
              </div>
              <div class="doc-actions">
                <a-tag :color="statusColor(item.processStatus)">{{ statusText(item.processStatus) }}</a-tag>
                <a-button type="link" size="small" @click.stop="openDocFrame(item)">
                  <template #icon><EyeOutlined /></template>
                  查看
                </a-button>
              </div>
            </div>
            <a-empty v-if="!docLoading && docs.length === 0" description="当前分类暂无文档" />
          </div>
        </a-spin>

        <div class="pager">
          <a-pagination
            size="small"
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            show-size-changer
            @change="handlePageChange"
            @showSizeChange="handlePageChange"
          />
        </div>
      </section>
    </div>

    <a-modal
      v-model:open="viewerVisible"
      width="86vw"
      :footer="null"
      :destroy-on-close="true"
      wrap-class-name="doc-view-frame-modal"
    >
      <template #title>
        <div class="frame-title">
          <span>{{ selectedDoc?.displayName || selectedDoc?.originalName || '文档查看' }}</span>
          <a-tag v-if="selectedDoc?.mdConverted" color="green">Markdown</a-tag>
          <a-tag v-else color="default">未转换</a-tag>
        </div>
      </template>

      <div v-if="selectedDoc" class="frame-body">
        <div class="frame-header">
          <div>
            <div class="preview-title">{{ selectedDoc.displayName || selectedDoc.originalName || '文档详情' }}</div>
            <div class="preview-path">{{ categoryName(selectedDoc.categoryPath) }}</div>
          </div>
          <a-space>
            <a-button :disabled="!selectedDoc.mdConverted" @click="loadMarkdown(selectedDoc)">
              <template #icon><ReloadOutlined /></template>
              重新加载
            </a-button>
            <a-button type="primary" :loading="downloadLoading" @click="downloadCurrentDoc">
              <template #icon><DownloadOutlined /></template>
              下载
            </a-button>
          </a-space>
        </div>

        <a-descriptions size="small" :column="3" bordered class="doc-info">
          <a-descriptions-item label="文件名">{{ selectedDoc.originalName || selectedDoc.actualFileName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ selectedDoc.fileType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="年份">{{ selectedDoc.fileYear || '-' }}</a-descriptions-item>
          <a-descriptions-item label="版本">v{{ selectedDoc.version || 1 }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(selectedDoc.processStatus)">{{ statusText(selectedDoc.processStatus) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="上传时间">{{ formatDate(selectedDoc.uploadTime) }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="3">{{ selectedDoc.remark || '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="content-head">
          <span>文档内容</span>
        </div>
        <a-spin :spinning="previewLoading">
          <div v-if="selectedDoc.mdConverted && markdownContent" class="markdown-box">
            <MarkdownViewer :value="markdownContent" />
          </div>
          <a-empty v-else-if="!previewLoading" description="该文档暂无可查看的 Markdown 内容" />
        </a-spin>
      </div>
    </a-modal>
  </PageWrapper>
</template>

<script lang="ts" setup>
  import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { DownloadOutlined, EyeOutlined, FileTextOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue';
  import { PageWrapper } from '/@/components/Page';
  import { MarkdownViewer } from '/@/components/Markdown';
  import { defHttp } from '/@/utils/http/axios';
  import { DocFileItem, DocTypeItem, getDoc, getMarkdownPreview, listDocTypes, pageDocs } from './documentViewer.api';

  interface CategoryNode {
    key: string;
    title: string;
    code: string;
    level: number;
    children?: CategoryNode[];
  }

  const categoryLoading = ref(false);
  const docLoading = ref(false);
  const previewLoading = ref(false);
  const categoryKeyword = ref('');
  const searchTitle = ref('');
  const searchYear = ref<number | undefined>();
  const docTypes = ref<DocTypeItem[]>([]);
  const treeData = ref<CategoryNode[]>([]);
  const expandedKeys = ref<string[]>(['all']);
  const selectedCategoryKeys = ref<string[]>(['all']);
  const selectedCategory = ref<CategoryNode | null>(null);
  const docs = ref<DocFileItem[]>([]);
  const selectedDoc = ref<DocFileItem | null>(null);
  const markdownContent = ref('');
  const viewerVisible = ref(false);
  const downloadLoading = ref(false);
  const leftPanelWidth = ref(280);
  const resizing = ref(false);
  const resizeMoveHandler = ref<((event: MouseEvent) => void) | null>(null);
  const resizeUpHandler = ref<((event: MouseEvent) => void) | null>(null);
  const minLeftWidth = 220;
  const maxLeftWidth = 420;
  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const selectedCategoryTitle = computed(() => selectedCategory.value?.title || '全部文档');

  const filteredTreeData = computed(() => {
    const keyword = categoryKeyword.value.trim().toLowerCase();
    if (!keyword) return treeData.value;
    const filterNode = (node: CategoryNode): CategoryNode | null => {
      const matched = node.title.toLowerCase().includes(keyword) || node.code.toLowerCase().includes(keyword);
      const children = (node.children || []).map(filterNode).filter(Boolean) as CategoryNode[];
      if (matched || children.length) {
        return { ...node, children };
      }
      return null;
    };
    return treeData.value.map(filterNode).filter(Boolean) as CategoryNode[];
  });

  onMounted(() => {
    reloadAll();
  });

  onBeforeUnmount(() => {
    stopResize();
  });

  async function reloadAll() {
    await loadCategories();
    await loadDocs();
  }

  async function loadCategories() {
    categoryLoading.value = true;
    try {
      const list = await listDocTypes();
      docTypes.value = (list || []).filter((item) => item.status !== 0);
      treeData.value = [{ key: 'all', title: '全部文档', code: '', level: 0, children: buildTree(docTypes.value) }];
      expandedKeys.value = ['all', ...docTypes.value.filter((item) => item.level < 3).map((item) => item.code)];
    } finally {
      categoryLoading.value = false;
    }
  }

  function buildTree(items: DocTypeItem[]) {
    const map = new Map<string, CategoryNode>();
    items.forEach((item) => {
      map.set(item.code, {
        key: item.code,
        title: item.name,
        code: item.code,
        level: item.level,
        children: [],
      });
    });
    const roots: CategoryNode[] = [];
    items.forEach((item) => {
      const node = map.get(item.code);
      if (!node) return;
      if (item.parentCode && map.has(item.parentCode)) {
        map.get(item.parentCode)?.children?.push(node);
      } else {
        roots.push(node);
      }
    });
    return roots;
  }

  async function loadDocs() {
    docLoading.value = true;
    try {
      const params: any = {
        pageNo: pagination.current,
        pageSize: pagination.pageSize,
      };
      const code = selectedCategory.value?.code || '';
      if (code.length === 2) params.typeCode1 = code;
      if (code.length === 4) params.typeCode2 = code;
      if (code.length === 6) params.typeCode3 = code;
      if (searchTitle.value.trim()) params.title = searchTitle.value.trim();
      if (searchYear.value) params.fileYear = searchYear.value;

      const page = await pageDocs(params);
      docs.value = page?.records || [];
      pagination.total = page?.total || 0;
      if (docs.value.length) {
        selectDoc(docs.value[0]);
      } else {
        selectedDoc.value = null;
        markdownContent.value = '';
      }
    } finally {
      docLoading.value = false;
    }
  }

  function selectDoc(item: DocFileItem) {
    selectedDoc.value = item;
    markdownContent.value = '';
  }

  async function openDocFrame(item: DocFileItem) {
    selectedDoc.value = item;
    markdownContent.value = '';
    viewerVisible.value = true;
    try {
      const detail = await getDoc(item.id);
      selectedDoc.value = detail || item;
      if (selectedDoc.value?.mdConverted) {
        await loadMarkdown(selectedDoc.value);
      }
    } catch (error) {
      message.error('文档详情加载失败');
    }
  }

  async function loadMarkdown(item: DocFileItem) {
    if (!item?.id || !item.mdConverted) return;
    previewLoading.value = true;
    try {
      markdownContent.value = await getMarkdownPreview(item.id);
    } catch (error) {
      markdownContent.value = '';
      message.error('Markdown 内容加载失败');
    } finally {
      previewLoading.value = false;
    }
  }

  async function downloadCurrentDoc() {
    if (!selectedDoc.value?.id) return;
    downloadLoading.value = true;
    try {
      const resp: any = await defHttp.get(
        { url: `/ai5g/doc/download/${selectedDoc.value.id}`, responseType: 'blob', timeout: 120000 },
        { isReturnNativeResponse: true, isTransformResponse: false },
      );
      const blob: Blob = resp?.data as Blob;
      const filename = selectedDoc.value.displayName || selectedDoc.value.originalName || selectedDoc.value.actualFileName || 'document';
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
    } catch (error: any) {
      message.error(error?.message || '下载失败');
    } finally {
      downloadLoading.value = false;
    }
  }

  function startResize(event: MouseEvent) {
    resizing.value = true;
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';
    const startX = event.clientX;
    const startWidth = leftPanelWidth.value;

    const handleMove = (moveEvent: MouseEvent) => {
      if (!resizing.value) return;
      const nextWidth = startWidth + (moveEvent.clientX - startX);
      leftPanelWidth.value = Math.max(minLeftWidth, Math.min(maxLeftWidth, nextWidth));
    };

    const handleUp = () => {
      stopResize();
    };

    resizeMoveHandler.value = handleMove;
    resizeUpHandler.value = handleUp;
    document.addEventListener('mousemove', handleMove);
    document.addEventListener('mouseup', handleUp);
  }

  function stopResize() {
    if (resizeMoveHandler.value) {
      document.removeEventListener('mousemove', resizeMoveHandler.value);
      resizeMoveHandler.value = null;
    }
    if (resizeUpHandler.value) {
      document.removeEventListener('mouseup', resizeUpHandler.value);
      resizeUpHandler.value = null;
    }
    resizing.value = false;
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
  }

  function resetPanelWidth() {
    leftPanelWidth.value = 280;
  }

  function handleExpand(keys: (string | number)[]) {
    expandedKeys.value = keys.map(String);
  }

  function handleCategorySelect(keys: (string | number)[]) {
    const key = String(keys[0] || 'all');
    selectedCategoryKeys.value = [key];
    selectedCategory.value = key === 'all' ? null : findCategoryNode(key, treeData.value);
    pagination.current = 1;
    loadDocs();
  }

  function findCategoryNode(key: string, nodes: CategoryNode[]): CategoryNode | null {
    for (const node of nodes) {
      if (node.key === key) return node;
      const matched = findCategoryNode(key, node.children || []);
      if (matched) return matched;
    }
    return null;
  }

  function handleSearch() {
    pagination.current = 1;
    loadDocs();
  }

  function handlePageChange(page: number, pageSize: number) {
    pagination.current = page;
    pagination.pageSize = pageSize;
    loadDocs();
  }

  function statusText(status?: string) {
    const map: Record<string, string> = {
      success: '已转换',
      uploaded: '已上传',
      processing: '处理中',
      failed: '失败',
    };
    return status ? map[status] || status : '-';
  }

  function statusColor(status?: string) {
    const map: Record<string, string> = {
      success: 'green',
      uploaded: 'blue',
      processing: 'orange',
      failed: 'red',
    };
    return status ? map[status] || 'default' : 'default';
  }

  function formatDate(value?: string) {
    if (!value) return '-';
    return value.replace('T', ' ').slice(0, 19);
  }

  function categoryName(path?: string) {
    if (!path) return '未分类';
    const codes = path.split('/').filter(Boolean);
    return codes
      .map((code) => {
        const item = docTypes.value.find((type) => type.code === code);
        return item?.name || code;
      })
      .join(' / ');
  }

</script>

<style lang="less" scoped>
  .doc-viewer {
    display: grid;
    grid-template-columns: var(--left-panel-width, 280px) 8px minmax(0, 1fr);
    gap: 0;
    height: calc(100vh - 112px);
    min-height: 620px;
    padding: 12px;
    background: #f4f7fb;
  }

  .side-panel,
  .doc-list-panel {
    min-height: 0;
    background: #fff;
    border: 1px solid #e6ebf2;
    border-radius: 8px;
  }

  .side-panel {
    display: flex;
    flex-direction: column;
    padding: 14px;
    overflow: auto;
    border-right: 0;
    border-radius: 8px 0 0 8px;
  }

  .panel-title,
  .list-toolbar,
  .frame-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .panel-title {
    height: 32px;
    font-size: 16px;
    font-weight: 600;
    color: #1f2a44;
  }

  .category-search {
    margin: 12px 0;
  }

  .doc-list-panel {
    display: flex;
    flex-direction: column;
    padding: 14px;
    min-width: 0;
    border-left: 0;
    border-radius: 0 8px 8px 0;
  }

  .splitter-handle {
    position: relative;
    display: flex;
    align-items: stretch;
    justify-content: center;
    width: 8px;
    cursor: col-resize;
    background: linear-gradient(180deg, transparent 0%, #f0f4fa 16%, #f0f4fa 84%, transparent 100%);
  }

  .splitter-handle::before {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    left: 50%;
    width: 1px;
    transform: translateX(-0.5px);
    background: #d9e2ee;
  }

  .splitter-handle span {
    position: relative;
    z-index: 1;
    width: 2px;
    height: 42px;
    margin-top: 120px;
    border-radius: 999px;
    background: #8fa7c4;
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.9);
  }

  .list-toolbar {
    flex-wrap: wrap;
    padding-bottom: 12px;
    border-bottom: 1px solid #edf1f7;
  }

  .section-title {
    font-size: 17px;
    font-weight: 650;
    color: #172033;
  }

  .section-subtitle,
  .preview-path,
  .doc-meta {
    margin-top: 3px;
    font-size: 12px;
    color: #758195;
  }

  .doc-list {
    flex: 1;
    min-height: 0;
    padding-top: 10px;
    overflow: auto;
  }

  .doc-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    min-height: 70px;
    padding: 12px;
    margin-bottom: 8px;
    cursor: pointer;
    border: 1px solid #eef2f7;
    border-radius: 8px;
    transition: border-color 0.16s ease, background 0.16s ease;
  }

  .doc-row:hover,
  .doc-row.active {
    background: #f7fbff;
    border-color: #70a7ff;
  }

  .doc-row-main {
    display: flex;
    align-items: center;
    flex: 1;
    min-width: 0;
  }

  .doc-icon {
    flex: 0 0 auto;
    margin-right: 10px;
    font-size: 22px;
    color: #2f6fdd;
  }

  .doc-text {
    min-width: 0;
  }

  .doc-title {
    overflow: hidden;
    font-size: 14px;
    font-weight: 600;
    color: #1f2a44;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .doc-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .doc-actions {
    display: flex;
    align-items: center;
    flex: 0 0 auto;
    gap: 8px;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    padding-top: 10px;
    border-top: 1px solid #edf1f7;
  }

  .preview-title {
    font-size: 19px;
    font-weight: 700;
    color: #172033;
  }

  .frame-title {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-right: 28px;
  }

  .frame-body {
    height: calc(100vh - 180px);
    min-height: 520px;
    overflow: auto;
    padding: 6px 8px 10px;
  }

  .frame-body :deep(.ant-descriptions) {
    margin-top: 4px;
  }

  .doc-info {
    margin-top: 14px;
  }

  .content-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 16px;
    padding-bottom: 10px;
    font-size: 16px;
    font-weight: 650;
    color: #1f2a44;
    border-bottom: 1px solid #edf1f7;
  }

  .markdown-box {
    padding: 18px 16px 24px;
    overflow: auto;
    background: #fff;
    border: 1px solid #edf1f7;
    border-radius: 8px;
  }

  .markdown-box :deep(img) {
    max-width: 100%;
    height: auto;
  }

  .markdown-box :deep(p) {
    margin-bottom: 0.75em;
  }

  @media (max-width: 1280px) {
    .doc-viewer {
      --left-panel-width: 240px;
    }

    .splitter-handle span {
      margin-top: 100px;
    }
  }
</style>
