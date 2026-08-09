<template>
  <a-modal
    :open="open"
    :title="title"
    width="1200px"
    :body-style="{ padding: 0, height: '80vh' }"
    :footer="null"
    @cancel="close"
  >
    <template #title>
      <div class="preview-title">
        <span class="preview-title-text">{{ title }}</span>
        <a-space>
          <a-button v-if="previewLink || textContent" type="link" @click="download">下载</a-button>
        </a-space>
      </div>
    </template>
    <div class="attachment-preview">
      <a-spin :spinning="loading">
        <div v-if="isImage && previewLink" class="preview-center">
          <img :src="previewLink" class="preview-image" />
        </div>
        <div v-else-if="isPdf && previewLink" class="preview-fill">
          <iframe :src="pdfViewerLink" class="preview-iframe"></iframe>
        </div>
        <div v-else-if="isDocx && !isPdf" class="preview-fill">
          <div ref="docxEl" class="docx-body"></div>
        </div>
        <div v-else-if="isText && !isPdf" class="preview-fill">
          <pre class="preview-text">{{ textContent }}</pre>
        </div>
        <div v-else-if="!loading" class="preview-center">
          <a-result status="info" title="该格式不支持在线预览" sub-title="可下载后使用本地应用打开">
            <template #extra>
              <a-button type="primary" @click="download">下载文件</a-button>
            </template>
          </a-result>
        </div>
        <a-empty v-else-if="!loading" description="预览加载失败" />
      </a-spin>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, nextTick, ref, watch, onUnmounted } from 'vue';
  import { message } from 'ant-design-vue';
  import { defHttp } from '/@/utils/http/axios';
  import { renderAsync } from 'docx-preview';

  const props = defineProps<{
    open: boolean;
    attachment: any;
  }>();

  const emit = defineEmits(['update:open']);

  const loading = ref(false);
  const previewLink = ref('');
  const textContent = ref('');
  const currentType = ref('');
  const responseContentType = ref('');
  const docxEl = ref<HTMLElement | null>(null);
  let objectUrl: string | null = null;

  const title = computed(() => props.attachment?.originalFilename || '附件预览');
  const pdfViewerLink = computed(() => (previewLink.value ? `${previewLink.value}#page=1&zoom=page-width` : ''));
  const isImage = computed(() => ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(currentType.value));
  const isPdf = computed(() => currentType.value === 'pdf' || responseContentType.value.includes('pdf'));
  const isDocx = computed(
    () =>
      currentType.value === 'docx' ||
      responseContentType.value.includes('wordprocessingml') ||
      responseContentType.value.includes('officedocument.wordprocessingml')
  );
  const isText = computed(() => ['txt', 'log', 'csv'].includes(currentType.value));

  function close() {
    emit('update:open', false);
  }

  function reset() {
    loading.value = false;
    previewLink.value = '';
    textContent.value = '';
    currentType.value = '';
    responseContentType.value = '';
    revokeObjectUrl();
  }

  function revokeObjectUrl() {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
      objectUrl = null;
    }
  }

  function fileExt(name?: string) {
    if (!name) return '';
    const dot = name.lastIndexOf('.');
    return dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
  }

  async function waitForDocxContainer() {
    await nextTick();
    let retry = 0;
    while (!docxEl.value && retry < 40) {
      await new Promise((resolve) => setTimeout(resolve, 50));
      await nextTick();
      retry++;
    }
    return !!docxEl.value;
  }

  async function loadPreview() {
    const att = props.attachment;
    if (!att?.id) return;
    loading.value = true;
    textContent.value = '';
    previewLink.value = '';
    responseContentType.value = '';
    currentType.value = fileExt(att.originalFilename || att.storedFilename || '');
    try {
      const resp: any = await defHttp.get(
        { url: `/roomops/engineering/attachment/preview/${att.id}`, responseType: 'blob', timeout: 120000 },
        { isReturnNativeResponse: true, isTransformResponse: false }
      );
      responseContentType.value = String(resp?.headers?.['content-type'] || resp?.headers?.['Content-Type'] || '').toLowerCase();
      const blob: Blob = resp?.data as Blob;
      if (isText.value && !isPdf.value) {
        textContent.value = await blob.text();
      } else if (isDocx.value && !isPdf.value) {
        const mounted = await waitForDocxContainer();
        if (!mounted) {
          throw new Error('文档预览容器尚未就绪');
        }
        docxEl.value!.innerHTML = '';
        try {
          await renderAsync(blob, docxEl.value!, undefined, {
            inWrapper: true,
            ignoreWidth: false,
            ignoreHeight: false,
            className: 'docx-preview',
            breakPages: true,
          });
        } catch (renderError) {
          console.warn('docx-preview render failed', renderError);
          throw renderError;
        }
      } else {
        revokeObjectUrl();
        objectUrl = URL.createObjectURL(blob);
        previewLink.value = objectUrl;
      }
    } catch (e: any) {
      message.error(e?.message || '预览加载失败');
    } finally {
      loading.value = false;
    }
  }

  async function download() {
    if (!props.attachment?.id) return;
    try {
      const resp: any = await defHttp.get(
        { url: `/roomops/engineering/attachment/download/${props.attachment.id}`, responseType: 'blob', timeout: 120000 },
        { isReturnNativeResponse: true, isTransformResponse: false }
      );
      const url = URL.createObjectURL(resp.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = props.attachment?.originalFilename || 'attachment';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error(e?.message || '下载失败');
    }
  }

  watch(
    () => [props.open, props.attachment?.id],
    () => {
      if (props.open) {
        loadPreview();
      } else {
        reset();
      }
    },
    { immediate: true }
  );

  onUnmounted(reset);
</script>

<style scoped>
  .preview-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-right: 24px;
  }

  .preview-title-text {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .attachment-preview {
    width: 100%;
    height: 100%;
    background: #f5f5f5;
  }

  .attachment-preview :deep(.ant-spin-nested-loading) {
    width: 100%;
    height: 100%;
  }

  .attachment-preview :deep(.ant-spin-container) {
    width: 100%;
    height: 100%;
  }

  .preview-center {
    display: flex;
    height: 80vh;
    align-items: center;
    justify-content: center;
    overflow: auto;
    padding: 16px;
  }

  .preview-image {
    max-width: 100%;
    max-height: 78vh;
    object-fit: contain;
  }

  .preview-fill {
    height: 80vh;
    overflow: auto;
  }

  .preview-iframe {
    width: 100%;
    height: 100%;
    border: 0;
  }

  .docx-body {
    height: 100%;
    overflow: auto;
    padding: 24px 32px;
    background: #fff;
  }

  .preview-text {
    height: 100%;
    margin: 0;
    padding: 16px 20px;
    overflow: auto;
    white-space: pre-wrap;
    background: #fff;
  }
</style>
