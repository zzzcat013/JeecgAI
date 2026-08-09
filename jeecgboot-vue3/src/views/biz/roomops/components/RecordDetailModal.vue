<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    width="920px"
    :footer="null"
    :body-style="{ padding: '16px 24px' }"
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <template v-if="record">
        <a-descriptions :column="2" size="small" bordered class="detail-desc">
          <a-descriptions-item label="记录编号" :span="2">{{ record.recordId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="业务类型">{{ businessTypeLabel(record.businessType) }}</a-descriptions-item>
          <a-descriptions-item label="专业/地市">
            {{ record.domainName || '-' }} / {{ record.regionName || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="机房">{{ record.roomId || '-' }} {{ record.roomName || '' }}</a-descriptions-item>
          <a-descriptions-item label="执行人员">{{ record.inspectorName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ record.submittedAt || '-' }}</a-descriptions-item>
          <a-descriptions-item label="上传方式">{{ uploadModeLabel(record.uploadMode) }}</a-descriptions-item>
          <a-descriptions-item v-if="record.latitude != null || record.longitude != null" label="记录定位" :span="2">
            {{ record.latitude || 0 }}, {{ record.longitude || 0 }}
            <span v-if="record.accuracy != null">（精度 {{ record.accuracy }}m）</span>
          </a-descriptions-item>
          <template v-if="record.businessType === 'fault'">
            <a-descriptions-item label="故障工单号">{{ record.faultOrderNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="处理情况">{{ record.handlingResult || '-' }}</a-descriptions-item>
          </template>
          <template v-else-if="record.businessType === 'engineering'">
            <a-descriptions-item label="施工内容" :span="2">{{ record.constructionContent || '-' }}</a-descriptions-item>
            <a-descriptions-item label="现场发现的问题">{{ record.siteProblems || '-' }}</a-descriptions-item>
            <a-descriptions-item label="遗留问题">{{ record.remainingIssues || '-' }}</a-descriptions-item>
            <a-descriptions-item label="备注说明" :span="2">{{ record.remarkNote || '-' }}</a-descriptions-item>
          </template>
          <template v-else>
            <a-descriptions-item label="环境状态">{{ record.environmentStatus || '-' }}</a-descriptions-item>
            <a-descriptions-item label="设备状态">{{ record.deviceStatus || '-' }}</a-descriptions-item>
            <a-descriptions-item label="异常描述" :span="2">{{ record.exceptionDesc || '-' }}</a-descriptions-item>
          </template>
        </a-descriptions>

        <div class="photo-title">照片（{{ photoCards.length }} 张）</div>
        <div v-if="photoCards.length" class="photo-grid">
          <div v-for="photo in photoCards" :key="photo.id" class="photo-card">
            <div class="thumb-wrap">
              <img v-if="photo.url" :src="photo.url" class="thumb" @click="openLargePhoto(photo)" />
              <div v-else class="thumb-empty">图片不可用</div>
            </div>
            <div class="photo-meta">
              <div class="photo-line">
                <span>第 {{ photo.photoIndex || '-' }} 张</span>
                <span v-if="photo.photoCapturedAt">{{ formatTime(photo.photoCapturedAt) }}</span>
              </div>
              <div v-if="photo.photoRemark" class="photo-remark" :title="photo.photoRemark">{{ photo.photoRemark }}</div>
              <div v-if="photo.photoLatitude != null || photo.photoLongitude != null" class="photo-line">
                {{ photo.photoLatitude || 0 }}, {{ photo.photoLongitude || 0 }}
              </div>
            </div>
          </div>
        </div>
        <a-empty v-else-if="!loading" description="该记录暂无照片" />
      </template>
      <a-empty v-else-if="!loading" description="未找到业务记录" />
    </a-spin>

    <a-modal
      :open="largePhotoOpen"
      title="照片预览"
      width="860px"
      :footer="null"
      :body-style="{ padding: '12px' }"
      @cancel="largePhotoOpen = false"
    >
      <div class="large-photo-wrap">
        <img v-if="largePhotoUrl" :src="largePhotoUrl" class="large-photo" />
      </div>
    </a-modal>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { defHttp } from '/@/utils/http/axios';
  import { message } from 'ant-design-vue';
  import { businessTypeLabel } from '../config/businessType.config';

  const props = defineProps<{
    open: boolean;
    recordId?: string;
    title?: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:open', value: boolean): void;
  }>();

  const loading = ref(false);
  const record = ref<any>(null);
  const photoCards = ref<any[]>([]);
  const largePhotoOpen = ref(false);
  const largePhotoUrl = ref('');
  const modalTitle = computed(() => props.title || `业务记录详情`);

  watch(
    () => [props.open, props.recordId] as const,
    ([open, recordId]) => {
      if (!open) {
        closeLargePhoto();
        return;
      }
      if (recordId) {
        loadRecord(recordId);
      }
    },
    { immediate: true }
  );

  function close() {
    emit('update:open', false);
  }

  async function loadRecord(recordId: string) {
    loading.value = true;
    record.value = null;
    revokePhotoUrls();
    try {
      const data: any = await defHttp.get({ url: '/roomops/record/queryByRecordId', params: { recordId } });
      record.value = data;
      await loadPhotos(data.recordId);
    } catch (e: any) {
      message.error(e?.message || '业务记录加载失败');
    } finally {
      loading.value = false;
    }
  }

  async function loadPhotos(recordId: string) {
    try {
      const data: any = await defHttp.get({
        url: '/roomops/photo/list',
        params: { recordId, pageNo: 1, pageSize: 50 },
      });
      const photos = data?.records || [];
      photoCards.value = await Promise.all(photos.map(loadPhotoBlob));
    } catch {
      photoCards.value = [];
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

  function openLargePhoto(photo: any) {
    if (!photo.url) return;
    largePhotoUrl.value = photo.url;
    largePhotoOpen.value = true;
  }

  function closeLargePhoto() {
    largePhotoOpen.value = false;
    revokePhotoUrls();
  }

  function revokePhotoUrls() {
    photoCards.value.forEach((photo) => {
      if (photo.url) URL.revokeObjectURL(photo.url);
    });
    photoCards.value = [];
  }

  function uploadModeLabel(value?: string) {
    if (value === 'offline_retry') return '补传';
    if (value === 'online' || value === 'direct') return '直接上传';
    if (value === 'offline_pending') return '待上传';
    return value || '-';
  }

  function formatTime(value?: string) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 16);
  }
</script>

<style scoped>
  .detail-desc {
    margin-bottom: 18px;
  }

  .photo-title {
    margin-bottom: 10px;
    font-size: 15px;
    font-weight: 600;
  }

  .photo-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 14px;
    max-height: 56vh;
    overflow: auto;
  }

  .photo-card {
    border: 1px solid #eee;
    border-radius: 6px;
    background: #fff;
    overflow: hidden;
  }

  .thumb-wrap {
    height: 150px;
    background: #f7f7f7;
    cursor: pointer;
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
    padding: 8px 10px;
    font-size: 12px;
    color: #555;
  }

  .photo-line {
    display: flex;
    gap: 8px;
    justify-content: space-between;
    white-space: nowrap;
    overflow: hidden;
  }

  .photo-remark {
    margin-top: 4px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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
