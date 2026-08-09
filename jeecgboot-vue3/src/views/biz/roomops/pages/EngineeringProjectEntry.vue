<template>
  <div class="roomops-page">
    <div class="toolbar">
      <div class="page-title">工程录入</div>
      <a-space>
        <a-button type="primary" :loading="submitLoading" @click="submit">保存工程</a-button>
        <a-button @click="reset">清空重填</a-button>
      </a-space>
    </div>

    <div class="entry-card">
      <EngineeringProjectForm ref="projectFormRef" :key="formKey" :record="record" />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { message } from 'ant-design-vue';
  import EngineeringProjectForm from '../components/EngineeringProjectForm.vue';
  import { createEngineeringProject } from '../api/roomops.api';

  const projectFormRef = ref<any>(null);
  const formKey = ref(0);
  const submitLoading = ref(false);
  const record = ref<any>({});

  function reset() {
    record.value = {};
    formKey.value += 1;
  }

  async function submit() {
    const form = projectFormRef.value;
    if (!form) return;
    const payload = form.getPayload();
    if (!payload.projectName) {
      message.error('请填写工程名称');
      return;
    }
    submitLoading.value = true;
    try {
      const created: any = await createEngineeringProject(payload);
      const projectId = created?.projectId || payload.projectId;
      if (form.getPendingFiles().length) {
        await form.saveAttachments(projectId);
      }
      message.success(`工程已保存：${projectId}`);
      reset();
    } catch (e: any) {
      message.error(e?.message || '保存失败，请检查后台日志');
    } finally {
      submitLoading.value = false;
    }
  }
</script>

<style scoped>
  .roomops-page {
    padding: 16px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
  }

  .entry-card {
    padding: 20px 28px;
    background: #fff;
  }
</style>
