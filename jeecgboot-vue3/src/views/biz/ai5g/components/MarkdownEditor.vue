<template>
  <a-modal
    v-model:open="visible"
    :title="title"
    :footer="null"
    :width="1200"
    @cancel="handleClose"
  >
    <!-- 操作工具栏（放在内容区，避免 title 插槽兼容问题） -->
    <div class="editor-toolbar">
      <a-space>
        <a-button v-if="!editing" type="primary" @click="startEdit">编辑</a-button>
        <a-button v-if="editing" type="primary" @click="saveEdit">保存</a-button>
        <a-button v-if="editing" @click="cancelEdit">取消</a-button>
      </a-space>
    </div>

    <!-- 预览模式 -->
    <div v-if="!editing" class="markdown-body" v-html="rendered" style="max-height:70vh;overflow:auto;padding:16px"></div>

    <!-- 编辑模式 -->
    <a-textarea v-else v-model:value="content" style="width:100%;height:70vh;resize:none;font-family:monospace" />
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { message } from 'ant-design-vue';
import MarkdownIt from 'markdown-it';
import { defHttp } from '/@/utils/http/axios';

const props = defineProps<{
  id: string;
  title: string;
}>();

const emit = defineEmits<{
  (e: 'saved', content: string): void;
  (e: 'close'): void;
}>();

const visible = ref(true);
const content = ref('');
const editing = ref(false);
const md = new MarkdownIt({ linkify: true, breaks: true });

const rendered = computed(() => md.render(content.value));

// 初始加载
(async () => {
  try {
    const resp: any = await defHttp.get({ url: `/ai5g/doc/preview-md/${props.id}`, responseType: 'blob' }, { isReturnNativeResponse: true, isTransformResponse: false });
    const txt = await (resp.data as Blob).text();
    content.value = txt;
  } catch (e: any) {
    message.error(e?.message || '加载失败');
    visible.value = false;
  }
})();

function startEdit() {
  editing.value = true;
}

async function saveEdit() {
  try {
    message.loading({ content: '保存中...', key: 'saveMd' });
    await defHttp.post({ url: '/ai5g/doc/save-md', params: { id: props.id, content: content.value } }, { isTransformResponse: false });
    message.success({ content: '保存成功', key: 'saveMd' });
    editing.value = false;
    emit('saved', content.value);
  } catch (e: any) {
    message.error({ content: e?.message || '保存失败', key: 'saveMd' });
  }
}

function cancelEdit() {
  editing.value = false;
}

function handleClose() {
  emit('close');
}
</script>

<style scoped>
.markdown-body {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}
.editor-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
}
</style>
