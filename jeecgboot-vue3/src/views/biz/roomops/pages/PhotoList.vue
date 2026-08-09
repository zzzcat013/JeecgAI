<template>
  <div class="roomops-page">
    <div class="toolbar">
      <div class="page-title">照片明细</div>
      <a-space>
        <a-button type="primary" @click="openAdd">新增</a-button>
        <a-button @click="load">刷新</a-button>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div v-if="photos.length" class="photo-grid">
        <div v-for="photo in photos" :key="photo.id" class="photo-card">
          <div class="thumb-wrap" @click="openLargePhoto(photo)">
            <img v-if="photo.url" :src="photo.url" class="thumb" />
            <div v-else class="thumb-empty">图片不可用</div>
          </div>
          <div class="photo-meta">
            <a-tooltip :title="fileName(photo)">
              <div class="meta-title" @click="openLargePhoto(photo)">{{ fileName(photo) }}</div>
            </a-tooltip>
            <div class="meta-grid">
              <div class="meta-item">
                <span>记录编号</span>
                <em>{{ photo.recordId || '-' }}</em>
              </div>
              <div class="meta-item">
                <span>序号</span>
                <em>{{ photo.photoIndex || '-' }} / {{ photo.photoTotal || '-' }}</em>
              </div>
              <div class="meta-item">
                <span>拍摄时间</span>
                <em>{{ formatTime(photo.photoCapturedAt) || '-' }}</em>
              </div>
              <div class="meta-item">
                <span>文件大小</span>
                <em>{{ formatSize(photo.fileSize) }}</em>
              </div>
              <div class="meta-item">
                <span>经度</span>
                <em>{{ photo.photoLongitude ?? '-' }}</em>
              </div>
              <div class="meta-item">
                <span>纬度</span>
                <em>{{ photo.photoLatitude ?? '-' }}</em>
              </div>
              <div class="meta-item">
                <span>定位精度</span>
                <em>{{ photo.photoAccuracy != null ? `${photo.photoAccuracy} m` : '-' }}</em>
              </div>
              <div class="meta-item">
                <span>水印</span>
                <em>{{ photo.watermarked ? '是' : '否' }}</em>
              </div>
            </div>
            <div v-if="photo.originalFilename && photo.originalFilename !== photo.storedFilename" class="meta-original">
              原始文件：{{ photo.originalFilename }}
            </div>
            <div v-if="photo.photoRemark" class="meta-remark" :title="photo.photoRemark">
              备注：{{ photo.photoRemark }}
            </div>
          </div>
          <div class="photo-actions">
            <a-space size="small">
              <a-button type="link" size="small" @click="openDetail(photo)">详情</a-button>
              <a-button v-if="canEdit" type="link" size="small" @click="openEdit(photo)">编辑</a-button>
              <a-button type="link" size="small" danger @click="remove(photo)">删除</a-button>
            </a-space>
          </div>
        </div>
      </div>
      <a-empty v-else-if="!loading" description="暂无照片" />
    </a-spin>

    <div v-if="pagination.total" class="pagination-bar">
      <a-pagination
        :current="pagination.current"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        show-size-changer
        :page-size-options="['12', '24', '48']"
        :show-total="showTotal"
        @change="handlePageChange"
        @show-size-change="handleSizeChange"
      />
    </div>

    <a-modal
      v-model:open="modalOpen"
      :title="modalTitle"
      width="760px"
      :confirm-loading="submitLoading"
      :footer="detailMode ? null : undefined"
      :body-style="{ padding: '20px 28px 4px' }"
      @ok="submit"
      @cancel="modalOpen = false"
    >
      <a-form :model="formModel" layout="vertical" class="roomops-form">
        <a-row :gutter="16">
          <a-col v-for="field in fields" :key="field.name" :span="field.span || 12">
            <a-form-item :label="field.label">
              <a-textarea v-if="field.type === 'textarea'" v-model:value="formModel[field.name]" :rows="3" :disabled="detailMode" />
              <a-input-number v-else-if="field.type === 'number'" v-model:value="formModel[field.name]" style="width: 100%" :disabled="detailMode" />
              <a-select
                v-else-if="field.type === 'select'"
                v-model:value="formModel[field.name]"
                :options="field.options"
                style="width: 100%"
                :disabled="detailMode"
              />
              <a-date-picker
                v-else-if="field.type === 'datetime'"
                v-model:value="formModel[field.name]"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                :disabled="detailMode"
              />
              <a-input v-else v-model:value="formModel[field.name]" :disabled="detailMode" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="largePhotoOpen" title="照片预览" width="920px" :footer="null" @cancel="largePhotoOpen = false">
      <div class="large-photo-wrap">
        <img v-if="largePhotoUrl" :src="largePhotoUrl" class="large-photo" />
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import { defHttp } from '/@/utils/http/axios';
  import { Modal, message } from 'ant-design-vue';
  import { usePermission } from '/@/hooks/web/usePermission';

  const photos = ref<any[]>([]);
  const loading = ref(false);
  const submitLoading = ref(false);
  const modalOpen = ref(false);
  const isUpdate = ref(false);
  const detailMode = ref(false);
  const largePhotoOpen = ref(false);
  const largePhotoUrl = ref('');
  const formModel = reactive<Record<string, any>>({});
  const pagination = reactive({
    current: 1,
    pageSize: 12,
    total: 0,
  });

  const fields = [
    { name: 'recordId', label: '记录编号', defaultValue: 'IR-CORE-TY-TY01ROOM1500-' },
    { name: 'photoIndex', label: '照片序号', type: 'number' },
    { name: 'photoTotal', label: '照片总数', type: 'number' },
    { name: 'originalFilename', label: '原始文件名' },
    { name: 'storedFilename', label: '存储文件名' },
    { name: 'storagePath', label: '存储路径', span: 24 },
    { name: 'contentType', label: '文件类型' },
    { name: 'fileSize', label: '文件大小', type: 'number' },
    { name: 'photoCapturedAt', label: '拍摄时间', type: 'datetime' },
    { name: 'photoLatitude', label: '照片纬度' },
    { name: 'photoLongitude', label: '照片经度' },
    { name: 'photoAccuracy', label: '照片定位精度' },
    { name: 'watermarked', label: '是否加水印', type: 'number', defaultValue: 0 },
    { name: 'photoRemark', label: '照片备注', type: 'textarea', span: 24 },
  ];

  const modalTitle = computed(() => {
    if (detailMode.value) return '照片详情';
    return `${isUpdate.value ? '编辑' : '新增'}照片明细`;
  });
  const { hasPermission } = usePermission();
  const canEdit = computed(() => hasPermission('roomops:photo:edit'));

  function showTotal(total: number) {
    return `共 ${total} 条`;
  }

  async function load() {
    loading.value = true;
    revokePhotoUrls();
    try {
      const data: any = await defHttp.get({
        url: '/roomops/photo/list',
        params: { pageNo: pagination.current, pageSize: pagination.pageSize },
      });
      pagination.total = data?.total || 0;
      const records = data?.records || [];
      photos.value = await Promise.all(records.map(loadPhotoBlob));
    } catch (e: any) {
      message.error(e?.message || '加载失败');
      photos.value = [];
    } finally {
      loading.value = false;
    }
  }

  async function loadPhotoBlob(photo: any) {
    try {
      const resp: any = await defHttp.get(
        { url: `/roomops/photo/preview/${photo.id}`, responseType: 'blob', timeout: 30000 },
        { isReturnNativeResponse: true, isTransformResponse: false }
      );
      return { ...photo, url: URL.createObjectURL(resp.data) };
    } catch {
      return { ...photo, url: '' };
    }
  }

  function handlePageChange(page: number) {
    pagination.current = page;
    load();
  }

  function handleSizeChange(_current: number, size: number) {
    pagination.current = 1;
    pagination.pageSize = size;
    load();
  }

  function fileName(photo: any) {
    return photo?.storedFilename || photo?.originalFilename || '图片';
  }

  function formatTime(value?: string) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 19);
  }

  function formatSize(size?: number) {
    if (size == null) return '-';
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / 1024 / 1024).toFixed(2)} MB`;
  }

  function openLargePhoto(photo: any) {
    if (!photo.url) return;
    largePhotoUrl.value = photo.url;
    largePhotoOpen.value = true;
  }

  function openAdd() {
    isUpdate.value = false;
    detailMode.value = false;
    resetForm();
    modalOpen.value = true;
  }

  function openEdit(record: any) {
    isUpdate.value = true;
    detailMode.value = false;
    resetForm(record);
    modalOpen.value = true;
  }

  function openDetail(record: any) {
    isUpdate.value = true;
    detailMode.value = true;
    resetForm(record);
    modalOpen.value = true;
  }

  function resetForm(record?: any) {
    Object.keys(formModel).forEach((key) => delete formModel[key]);
    fields.forEach((field) => {
      formModel[field.name] = record?.[field.name] ?? field.defaultValue ?? '';
    });
    if (record?.id) {
      formModel.id = record.id;
    }
  }

  async function submit() {
    submitLoading.value = true;
    try {
      if (isUpdate.value) {
        await defHttp.put({ url: '/roomops/photo/edit', data: { ...formModel } });
      } else {
        await defHttp.post({ url: '/roomops/photo/add', data: { ...formModel } });
      }
      message.success(isUpdate.value ? '修改成功' : '新增成功');
      modalOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '保存失败，请检查后台日志');
    } finally {
      submitLoading.value = false;
    }
  }

  function remove(record: any) {
    Modal.confirm({
      title: '确定删除这张照片？',
      onOk: async () => {
        await defHttp.delete({ url: '/roomops/photo/delete', data: { id: record.id } }, { joinParamsToUrl: true });
        message.success('删除成功');
        await load();
      },
    });
  }

  function revokePhotoUrls() {
    photos.value.forEach((photo) => {
      if (photo.url) URL.revokeObjectURL(photo.url);
    });
    photos.value = [];
  }

  onMounted(load);
  onBeforeUnmount(revokePhotoUrls);
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

  .photo-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 14px;
  }

  .photo-card {
    border: 1px solid #eee;
    border-radius: 6px;
    background: #fff;
    overflow: hidden;
  }

  .thumb-wrap {
    height: 170px;
    background: #f5f5f5;
    cursor: zoom-in;
  }

  .thumb {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  .thumb-empty {
    display: flex;
    height: 100%;
    align-items: center;
    justify-content: center;
    color: #999;
    font-size: 12px;
  }

  .photo-meta {
    padding: 10px 12px;
    color: #555;
    font-size: 12px;
  }

  .meta-title {
    margin-bottom: 8px;
    color: #333;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .meta-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 5px 12px;
  }

  .meta-item {
    min-width: 0;
    display: flex;
    justify-content: space-between;
    gap: 6px;
    line-height: 1.5;
  }

  .meta-item span {
    flex: none;
    color: #999;
  }

  .meta-item em {
    min-width: 0;
    color: #333;
    font-style: normal;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .meta-original,
  .meta-remark {
    margin-top: 6px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .meta-remark {
    color: #666;
  }

  .photo-actions {
    display: flex;
    justify-content: flex-end;
    padding: 2px 8px;
    border-top: 1px solid #f0f0f0;
  }

  .pagination-bar {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .roomops-form :deep(.ant-form-item) {
    margin-bottom: 18px;
  }

  .large-photo-wrap {
    display: flex;
    min-height: 240px;
    max-height: 72vh;
    align-items: center;
    justify-content: center;
    overflow: auto;
    background: #f5f5f5;
  }

  .large-photo {
    max-width: 100%;
    max-height: 70vh;
    object-fit: contain;
  }
</style>
