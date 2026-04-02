<template>
  <BasicModal v-bind="$attrs" @register="registerModal" title="本地目录打包上传" @ok="handleSubmit" width="600px">
    <a-form :model="formState" :rules="rules" layout="vertical">
      <a-form-item label="本地目录路径" name="localPath">
        <a-input v-model:value="formState.localPath" placeholder="请输入服务器可访问的本地目录路径（包含 .md 和 images 目录）" />
        <template #help>
          <div style="margin-top: 8px; font-size: 12px; color: #8c8c8c">
            <p>提示：该功能将自动扫描目录下的所有 Markdown 文件及其引用的图片，按照 JEECG 规范打包并导入知识库。</p>
            <p>目录结构建议：</p>
            <pre style="background: #f5f5f5; padding: 8px; border-radius: 4px; font-family: monospace;">
.
├── doc1.md
├── doc2.md
└── images/
    ├── img1.png
    └── img2.png
            </pre>
          </div>
        </template>
      </a-form-item>
    </a-form>
  </BasicModal>
</template>

<script lang="ts" setup>
  import { ref, reactive } from 'vue';
  import { BasicModal, useModalInner } from '@/components/Modal';
  import { knowledgeImportLocal } from '../api/AiKnowledgeBase.api';
  import { useMessage } from '@/hooks/web/useMessage';

  const emit = defineEmits(['success', 'register']);
  const { createMessage } = useMessage();
  const knowledgeId = ref('');
  const originDocId = ref('');
  const formState = reactive({
    localPath: '',
  });

  const rules = {
    localPath: [{ required: true, message: '请输入本地目录路径' }],
  };

  const [registerModal, { setModalProps, closeModal }] = useModalInner(async (data) => {
    knowledgeId.value = data.knowledgeId;
    originDocId.value = data.originDocId || '';
    formState.localPath = data.localPath || '';
    setModalProps({ confirmLoading: false });
  });

  async function handleSubmit() {
    try {
      setModalProps({ confirmLoading: true });
      const res = await knowledgeImportLocal({
        knowId: knowledgeId.value,
        localPath: formState.localPath,
        originDocId: originDocId.value,
      });
      if (res.success || res.code === 200) {
        createMessage.success(res.message || '导入任务已提交');
        emit('success');
        closeModal();
      } else {
        createMessage.error(res.message || '导入失败');
      }
    } catch (e) {
      console.error(e);
    } finally {
      setModalProps({ confirmLoading: false });
    }
  }
</script>
