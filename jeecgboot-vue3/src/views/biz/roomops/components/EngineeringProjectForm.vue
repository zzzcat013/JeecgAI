<template>
  <div class="engineering-form">
    <a-form :model="formModel" layout="vertical" class="roomops-form">
      <div class="form-section-title">基本信息</div>
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="工程编号">
            <a-input v-model:value="formModel.projectId" :disabled="readonly" placeholder="不填自动生成，如 EG-CORE-TY-ROOM001-XXXX" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="工程名称">
            <a-input v-model:value="formModel.projectName" :disabled="readonly" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="工程类别">
            <a-input v-model:value="formModel.category" :disabled="readonly" placeholder="如核心网改造、设备安装" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="归属">
            <a-input v-model:value="formModel.ownership" :disabled="readonly" placeholder="建设单位/部门" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="状态">
            <a-select v-model:value="formModel.status" :options="statusOptions" :disabled="readonly" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="专业">
            <a-select v-model:value="formModel.domainCode" :options="domainOptions" :disabled="readonly" @change="fillDomain" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="地市">
            <a-select v-model:value="formModel.regionCode" :options="regionOptions" :disabled="readonly" @change="fillRegion" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="机房">
            <a-select
              v-model:value="formModel.roomId"
              :options="machineRoomOptions"
              allow-clear
              show-search
              option-filter-prop="label"
              :disabled="readonly"
              @change="fillRoom"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <div class="form-section-title">开工报告</div>
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="开工日期">
            <a-date-picker v-model:value="formModel.startReportDate" value-format="YYYY-MM-DD" style="width: 100%" :disabled="readonly" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="开工单位">
            <a-input v-model:value="formModel.startReportCompany" :disabled="readonly" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="负责人">
            <a-input v-model:value="formModel.startReportPerson" :disabled="readonly" />
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="开工报告内容">
            <a-textarea v-model:value="formModel.startReportContent" :rows="3" :disabled="readonly" />
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="工程说明">
            <a-textarea v-model:value="formModel.description" :rows="3" :disabled="readonly" />
          </a-form-item>
        </a-col>
      </a-row>

      <div class="form-section-title">工程资料附件</div>
      <div v-for="section in uploadSections" :key="section.docType" class="upload-section">
        <div class="upload-section-head">
          <span>{{ section.title }}</span>
          <a-upload v-if="!readonly" :show-upload-list="false" :before-upload="(file) => beforeUpload(file, section.docType)">
            <a-button size="small">上传文件</a-button>
          </a-upload>
        </div>
        <div v-if="attachmentsByType(section.docType).length" class="file-list">
          <div v-for="att in attachmentsByType(section.docType)" :key="att.id" class="file-line">
            <span class="file-name" :title="att.originalFilename">{{ att.originalFilename }}</span>
            <span class="file-size">{{ formatSize(att.fileSize) }}</span>
            <a-space size="small">
              <a-button type="link" size="small" @click="previewFile(att)">预览</a-button>
              <a-button type="link" size="small" @click="downloadAttachment(att)">下载</a-button>
              <a-button v-if="!readonly" type="link" size="small" danger @click="deleteAttachment(att)">删除</a-button>
            </a-space>
          </div>
        </div>
        <a-empty v-else :image="simpleImage" description="暂无附件" class="file-empty" />
      </div>

      <div v-if="pendingFiles.length" class="pending-box">
        <div class="pending-title">保存工程后上传（{{ pendingFiles.length }} 个文件）</div>
        <div v-for="(item, index) in pendingFiles" :key="index" class="file-line">
          <span class="file-name">{{ item.file.name }}</span>
          <span>{{ uploadSectionLabel(item.docType) }}</span>
        </div>
      </div>
    </a-form>

    <AttachmentPreviewModal v-model:open="previewOpen" :attachment="previewAttachment" />
  </div>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
  import { defHttp } from '/@/utils/http/axios';
  import { message } from 'ant-design-vue';
  import { Empty } from 'ant-design-vue';
  import AttachmentPreviewModal from './AttachmentPreviewModal.vue';

  const props = defineProps<{
    record?: any;
    readonly?: boolean;
  }>();

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const formModel = reactive<Record<string, any>>({});
  const attachments = ref<any[]>([]);
  const pendingFiles = ref<any[]>([]);
  const uploading = ref(false);
  const previewOpen = ref(false);
  const previewAttachment = ref<any>(null);
  const machineRoomOptions = ref<any[]>([]);

  const statusOptions = [
    { label: '未开工', value: 'NOT_STARTED' },
    { label: '开工', value: 'STARTED' },
    { label: '实施中', value: 'IN_PROGRESS' },
    { label: '完工', value: 'COMPLETED' },
    { label: '验收完成', value: 'ACCEPTED' },
  ];
  const domainOptions = [
    { label: '核心网 CORE', value: 'core_network' },
    { label: '动力', value: 'power' },
    { label: '承载网', value: 'transport' },
  ];
  const regionOptions = [{ label: '太原 TY', value: 'TY' }];
  const uploadSections = [
    { docType: 'START_REPORT', title: '开工报告附件' },
    { docType: 'PLAN', title: '施工方案' },
    { docType: 'TECHNICAL', title: '技术交底' },
    { docType: 'SAFETY', title: '安全交底' },
    { docType: 'OTHER', title: '其他附件' },
  ];

  function reset() {
    Object.keys(formModel).forEach((key) => delete formModel[key]);
    formModel.projectId = props.record?.projectId || '';
    formModel.projectName = props.record?.projectName || '';
    formModel.category = props.record?.category || '';
    formModel.ownership = props.record?.ownership || '';
    formModel.domainCode = props.record?.domainCode || 'core_network';
    formModel.domainShortCode = props.record?.domainShortCode || 'CORE';
    formModel.domainName = props.record?.domainName || '核心网';
    formModel.regionCode = props.record?.regionCode || 'TY';
    formModel.regionName = props.record?.regionName || '太原';
    formModel.roomId = props.record?.roomId || '';
    formModel.roomName = props.record?.roomName || '';
    formModel.status = props.record?.status || 'NOT_STARTED';
    formModel.startReportDate = props.record?.startReportDate || '';
    formModel.startReportCompany = props.record?.startReportCompany || '';
    formModel.startReportPerson = props.record?.startReportPerson || '';
    formModel.startReportContent = props.record?.startReportContent || '';
    formModel.description = props.record?.description || '';
    if (props.record?.id) formModel.id = props.record.id;
    pendingFiles.value = [];
    if (formModel.projectId) {
      loadAttachments(formModel.projectId);
    } else {
      attachments.value = [];
    }
  }

  async function loadMachineRooms() {
    try {
      const data: any = await defHttp.get({
        url: '/roomops/machineRoom/list',
        params: { pageNo: 1, pageSize: 200, status: 1 },
      });
      machineRoomOptions.value = (data?.records || []).map((room) => ({
        label: `${room.roomId || ''} / ${room.roomName || ''}`,
        value: room.roomId,
        roomName: room.roomName || '',
        domainCode: room.domainCode || 'core_network',
        domainShortCode: room.domainShortCode || 'CORE',
        domainName: room.domainName || '核心网',
        regionCode: room.regionCode || 'TY',
        regionName: room.regionName || '太原',
      }));
    } catch {
      machineRoomOptions.value = [];
    }
  }

  async function loadAttachments(projectId: string) {
    try {
      const data: any = await defHttp.get({
        url: '/roomops/engineering/attachment/list',
        params: { projectId },
      });
      attachments.value = data || [];
    } catch {
      attachments.value = [];
    }
  }

  function attachmentsByType(docType: string) {
    return attachments.value.filter((item) => item.docType === docType);
  }

  function uploadSectionLabel(docType: string) {
    return uploadSections.find((item) => item.docType === docType)?.title || docType;
  }

  function beforeUpload(file: File, docType: string) {
    const pendingDup = pendingFiles.value.find(
      (item) => item.docType === docType && item.file.name === file.name && item.file.size === file.size
    );
    if (pendingDup) {
      message.warning('该文件已选择，请勿重复上传');
      return false;
    }
    if (formModel.projectId) {
      const exists = attachments.value.find(
        (item) => item.docType === docType && item.originalFilename === file.name && Number(item.fileSize) === file.size
      );
      if (exists) {
        message.warning('该文件已存在，请勿重复上传');
        return false;
      }
      uploadFile(file, docType, formModel.projectId);
    } else {
      pendingFiles.value.push({ file, docType });
      message.info('工程保存后会自动上传该文件');
    }
    return false;
  }

  async function uploadFile(file: File, docType: string, projectId: string) {
    uploading.value = true;
    try {
      const resp: any = await defHttp.uploadFile(
        { url: '/roomops/engineering/attachment/upload' },
        { name: 'file', file, filename: file.name, data: { projectId, docType } },
        { isReturnResponse: true }
      );
      const data = resp?.result;
      if (resp?.success === false) {
        throw new Error(resp?.message || '文件上传失败');
      }
      if (data?.duplicate) {
        message.warning(resp?.message || '文件已存在，未重复上传');
        return;
      }
      if (!data?.id) {
        throw new Error(resp?.message || '文件上传失败');
      }
      if (!attachments.value.some((item) => item.id === data.id)) {
        attachments.value.push(data);
      }
      message.success('文件上传成功');
    } catch (e: any) {
      message.error(e?.message || '文件上传失败');
    } finally {
      uploading.value = false;
    }
  }

  async function saveAttachments(projectId: string) {
    for (const item of pendingFiles.value) {
      await uploadFile(item.file, item.docType, projectId);
    }
    pendingFiles.value = [];
    await loadAttachments(projectId);
  }

  function getPayload() {
    return { ...formModel };
  }

  function getPendingFiles() {
    return pendingFiles.value;
  }

  function getAttachments() {
    return attachments.value;
  }

  async function previewFile(att: any) {
    previewAttachment.value = att;
    previewOpen.value = true;
  }

  async function downloadAttachment(att: any) {
    try {
      const resp: any = await defHttp.get(
        { url: `/roomops/engineering/attachment/download/${att.id}`, responseType: 'blob', timeout: 30000 },
        { isReturnNativeResponse: true, isTransformResponse: false }
      );
      const url = URL.createObjectURL(resp.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = att.originalFilename || 'attachment';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error(e?.message || '下载失败');
    }
  }

  async function deleteAttachment(att: any) {
    try {
      await defHttp.delete({ url: '/roomops/engineering/attachment/delete', data: { id: att.id } }, { joinParamsToUrl: true });
      attachments.value = attachments.value.filter((item) => item.id !== att.id);
      message.success('删除成功');
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  }

  function fillDomain(value: string) {
    const item = domainOptions.find((opt) => opt.value === value);
    formModel.domainShortCode = item?.value === 'power' ? 'POWER' : item?.value === 'transport' ? 'TRANSPORT' : 'CORE';
    formModel.domainName = item?.label?.replace(/ .*/, '') || '核心网';
  }

  function fillRegion(value: string) {
    const item = regionOptions.find((opt) => opt.value === value);
    formModel.regionName = item?.label?.replace(/ .*/, '') || '太原';
  }

  function fillRoom(roomId: string) {
    const room = machineRoomOptions.value.find((item) => item.value === roomId);
    if (!room) return;
    formModel.roomName = room.roomName;
    formModel.domainCode = room.domainCode;
    formModel.domainShortCode = room.domainShortCode;
    formModel.domainName = room.domainName;
    formModel.regionCode = room.regionCode;
    formModel.regionName = room.regionName;
  }

  function formatSize(size?: number) {
    if (size == null) return '-';
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / 1024 / 1024).toFixed(2)} MB`;
  }

  function revokePreviewUrl() {
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value);
      previewUrl.value = '';
    }
  }

  watch(
    () => props.record?.id,
    () => reset(),
    { immediate: true }
  );

  onMounted(loadMachineRooms);

  defineExpose({ getPayload, getPendingFiles, getAttachments, saveAttachments, loadAttachments });
</script>

<style scoped>
  .engineering-form {
    padding-top: 2px;
  }

  .form-section-title {
    margin: 12px 0 10px;
    padding-left: 8px;
    border-left: 3px solid #1677ff;
    color: #333;
    font-size: 14px;
    font-weight: 600;
  }

  .form-section-title:first-child {
    margin-top: 0;
  }

  .upload-section {
    margin-bottom: 16px;
    padding: 10px 12px;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
  }

  .upload-section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    color: #333;
    font-weight: 500;
  }

  .file-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .file-line {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    padding: 4px 0;
    border-bottom: 1px dashed #f0f0f0;
  }

  .file-name {
    min-width: 0;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-size {
    flex: none;
    color: #999;
  }

  .file-empty {
    padding: 4px 0;
  }

  .pending-box {
    margin-top: 4px;
    padding: 10px 12px;
    border: 1px dashed #faad14;
    border-radius: 6px;
    background: #fffbe6;
  }

  .pending-title {
    margin-bottom: 6px;
    color: #ad6800;
    font-weight: 500;
  }

</style>
