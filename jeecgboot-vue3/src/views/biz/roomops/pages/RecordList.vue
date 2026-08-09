<template>
  <div class="roomops-page">
    <div class="toolbar">
      <div class="page-title">业务记录</div>
      <a-space>
        <a-button type="primary" @click="openAdd">新增</a-button>
        <a-button @click="load">刷新</a-button>
      </a-space>
    </div>

    <div class="filter-form">
      <div class="filter-item">
        <span class="filter-label">业务</span>
        <a-select v-model:value="filters.businessType" :options="filterBusinessTypeOptions" allow-clear class="filter-select-sm" @change="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">专业</span>
        <a-select v-model:value="filters.domainCode" :options="domainOptions" allow-clear class="filter-select-sm" @change="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">人员</span>
        <a-select
          v-model:value="filters.inspectorName"
          :options="dingtalkUserOptions"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="请选择执行人员"
          class="filter-select-md"
          @change="reloadFirstPage"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">机房</span>
        <a-select
          v-model:value="filters.roomId"
          :options="machineRoomOptions"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="请选择机房"
          class="filter-select-lg"
          @change="reloadFirstPage"
        />
      </div>
      <div class="filter-actions">
        <a-space>
          <a-button type="primary" @click="reloadFirstPage">查询</a-button>
          <a-button @click="resetFilters">重置</a-button>
        </a-space>
      </div>
    </div>

    <a-table
      :columns="tableColumns"
      :data-source="rows"
      :loading="loading"
      row-key="id"
      size="small"
      :pagination="pagination"
      :scroll="{ x: tableScrollX }"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'recordId'">
          <JEllipsis :value="record.recordId" :length="18" />
        </template>
        <template v-else-if="column.key === 'roomId'">
          <JEllipsis :value="record.roomId || '-'" :length="14" />
        </template>
        <template v-else-if="column.key === 'roomName'">
          <JEllipsis :value="record.roomName || '-'" :length="16" />
        </template>
        <template v-else-if="column.key === 'businessTypeName'">
          {{ businessTypeLabel(record.businessType) }}
        </template>
        <template v-else-if="column.key === 'summary'">
          <JEllipsis :value="summaryText(record)" :length="22" />
        </template>
        <template v-else-if="businessFieldKey(column.key)">
          <JEllipsis :value="record[column.dataIndex] || '-'" :length="column.ellipsisLength || 16" />
        </template>
        <template v-else-if="column.key === 'photoCount'">
          <a-button type="link" class="count-link" :disabled="!record.photoCount" @click="openPhotos(record)">
            {{ record.photoCount || 0 }} 张
          </a-button>
        </template>
        <template v-else-if="column.key === 'uploadModeName'">
          {{ uploadModeLabel(record.uploadMode) }}
        </template>
        <template v-else-if="column.key === 'submittedAt'">
          {{ formatTime(record.submittedAt) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <a-button type="link" @click="openFormDetail(record)">详情</a-button>
            <a-button type="link" @click="openDetail(record)">查看</a-button>
            <a-button v-if="canEdit" type="link" @click="openEdit(record)">编辑</a-button>
            <a-dropdown :trigger="['click']">
              <a-button type="link" class="more-btn">
                更多
                <Icon icon="mdi:chevron-down"></Icon>
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="delete" @click="remove(record)">删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </template>
    </a-table>

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
              <a-select
                v-else-if="field.type === 'select'"
                v-model:value="formModel[field.name]"
                :options="field.options"
                style="width: 100%"
                :disabled="detailMode"
                @change="field.name === 'businessType' && onBusinessTypeChange()"
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

    <a-modal
      v-model:open="photosOpen"
      :title="photosTitle"
      width="980px"
      :footer="null"
      :body-style="{ padding: '16px 20px' }"
      @cancel="closePhotos"
    >
      <a-spin :spinning="photosLoading">
        <div v-if="photoCards.length" class="photo-grid">
          <div v-for="photo in photoCards" :key="photo.id" class="photo-card">
            <div class="thumb-wrap">
              <img :src="photo.url" class="thumb" @click="openLargePhoto(photo)" />
            </div>
            <div class="photo-meta">
              <div>{{ photo.storedFilename || photo.originalFilename }}</div>
              <div>序号：{{ photo.photoIndex || '-' }} / {{ photo.photoTotal || '-' }}</div>
              <div>拍摄：{{ photo.photoCapturedAt || '-' }}</div>
              <div>水印：{{ photo.watermarked ? '是' : '否' }}</div>
              <div v-if="photo.photoRemark">备注：{{ photo.photoRemark }}</div>
            </div>
          </div>
        </div>
        <a-empty v-else-if="!photosLoading" description="暂无照片" />
      </a-spin>
    </a-modal>

    <a-modal v-model:open="largePhotoOpen" title="照片预览" width="920px" :footer="null" @cancel="largePhotoUrl = ''">
      <div class="large-photo-wrap">
        <img v-if="largePhotoUrl" :src="largePhotoUrl" class="large-photo" />
      </div>
    </a-modal>

    <RecordDetailModal v-model:open="detailOpen" :record-id="detailRecordId" title="业务记录详情" />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { defHttp } from '/@/utils/http/axios';
  import { Modal, message } from 'ant-design-vue';
  import JEllipsis from '/@/components/Form/src/jeecg/components/JEllipsis.vue';
  import { usePermission } from '/@/hooks/web/usePermission';
  import RecordDetailModal from '../components/RecordDetailModal.vue';
  import { businessTypeColumn, businessTypeField, businessTypeLabel, businessTypeOptions } from '../config/businessType.config';

  const filterBusinessTypeOptions = [{ label: '全部业务', value: '' }, ...businessTypeOptions];
  const domainOptions = [{ label: '核心网', value: 'core_network' }];
  const machineRoomOptions = ref<any[]>([]);
  const dingtalkUserOptions = ref<any[]>([]);
  const rows = ref<any[]>([]);
  const loading = ref(false);
  const submitLoading = ref(false);
  const modalOpen = ref(false);
  const isUpdate = ref(false);
  const detailMode = ref(false);
  const photosOpen = ref(false);
  const photosLoading = ref(false);
  const selectedRecord = ref<any>(null);
  const photoCards = ref<any[]>([]);
  const largePhotoOpen = ref(false);
  const largePhotoUrl = ref('');
  const detailOpen = ref(false);
  const detailRecordId = ref('');
  const formModel = reactive<Record<string, any>>({});
  const filters = reactive({ businessType: '', domainCode: 'core_network', inspectorName: '', roomId: '' });
  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ['5', '10', '20', '50'],
    showTotal: (total: number) => `共 ${total} 条`,
  });

  const baseColumns = [
    { title: '记录编号', dataIndex: 'recordId', key: 'recordId', width: 160, ellipsisLength: 12 },
    businessTypeColumn(),
    { title: '地市', dataIndex: 'regionName', key: 'regionName', width: 70 },
    { title: '机房编号', dataIndex: 'roomId', key: 'roomId', width: 120, ellipsisLength: 10 },
    { title: '机房名称', dataIndex: 'roomName', key: 'roomName', width: 140, ellipsisLength: 12 },
    { title: '执行人员', dataIndex: 'inspectorName', key: 'inspectorName', width: 100 },
    { title: '照片', dataIndex: 'photoCount', key: 'photoCount', width: 80 },
  ];
  const commonTailColumns = [
    { title: '业务摘要', key: 'summary', width: 280 },
    { title: '上传方式', dataIndex: 'uploadMode', key: 'uploadModeName', width: 90 },
    { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 130 },
    { title: '操作', key: 'action', fixed: 'right', width: 220 },
  ];
  const businessColumns: Record<string, any[]> = {
    inspection: [
      { title: '环境状态', dataIndex: 'environmentStatus', key: 'environmentStatus', width: 120 },
      { title: '设备状态', dataIndex: 'deviceStatus', key: 'deviceStatus', width: 120 },
      { title: '异常描述', dataIndex: 'exceptionDesc', key: 'exceptionDesc', width: 150, ellipsisLength: 10 },
    ],
    fault: [
      { title: '故障工单号', dataIndex: 'faultOrderNo', key: 'faultOrderNo', width: 130, ellipsisLength: 10 },
      { title: '处理情况', dataIndex: 'handlingResult', key: 'handlingResult', width: 180, ellipsisLength: 12 },
    ],
    engineering: [
      { title: '施工内容', dataIndex: 'constructionContent', key: 'constructionContent', width: 180, ellipsisLength: 12 },
      { title: '现场问题', dataIndex: 'siteProblems', key: 'siteProblems', width: 160, ellipsisLength: 10 },
      { title: '遗留问题', dataIndex: 'remainingIssues', key: 'remainingIssues', width: 150, ellipsisLength: 9 },
      { title: '备注', dataIndex: 'remarkNote', key: 'remarkNote', width: 140, ellipsisLength: 9 },
    ],
  };
  const tableColumns = computed(() => [
    ...baseColumns,
    ...(filters.businessType ? businessColumns[filters.businessType] || [] : []),
    ...commonTailColumns,
  ]);
  const tableScrollX = computed(() => Math.max(1200, tableColumns.value.reduce((sum, col) => sum + (Number(col.width) || 140), 0)));
  const modalTitle = computed(() => {
    if (detailMode.value) return '业务记录详情';
    return `${isUpdate.value ? '编辑' : '新增'}业务记录`;
  });
  const photosTitle = computed(() => `${selectedRecord.value?.recordId || ''} 照片`);
  const { hasPermission } = usePermission();
  const canEdit = computed(() => hasPermission('roomops:record:edit'));

  const fields = computed(() => {
    const commonFields = [
      { name: 'recordId', label: '记录编号' },
      businessTypeField(),
      { name: 'domainCode', label: '专业编码', defaultValue: 'core_network' },
      { name: 'domainShortCode', label: '专业简写', defaultValue: 'CORE' },
      { name: 'domainName', label: '专业名称', defaultValue: '核心网' },
      { name: 'regionCode', label: '地市编码', defaultValue: 'TY' },
      { name: 'regionName', label: '地市名称', defaultValue: '太原' },
      { name: 'roomId', label: '机房编号', defaultValue: 'TY01ROOM1500' },
      { name: 'roomName', label: '机房名称', defaultValue: '一枢纽15楼机房' },
      { name: 'inspectorName', label: '执行人员' },
      { name: 'uploadMode', label: '上传方式', defaultValue: 'direct' },
      { name: 'submittedAt', label: '提交时间', type: 'datetime' },
    ];
    const typeFields: Record<string, any[]> = {
      inspection: [
        { name: 'environmentStatus', label: '环境状态', defaultValue: '正常' },
        { name: 'deviceStatus', label: '设备状态', defaultValue: '正常' },
        { name: 'exceptionDesc', label: '异常描述', type: 'textarea', span: 24 },
      ],
      fault: [
        { name: 'faultOrderNo', label: '故障工单号' },
        { name: 'handlingResult', label: '处理情况', type: 'textarea', span: 24 },
      ],
      engineering: [
        { name: 'constructionContent', label: '施工内容', type: 'textarea', span: 24 },
        { name: 'siteProblems', label: '现场发现的问题', type: 'textarea', span: 24 },
        { name: 'remainingIssues', label: '遗留问题', type: 'textarea', span: 24 },
        { name: 'remarkNote', label: '备注说明', type: 'textarea', span: 24 },
      ],
    };
    const businessType = formModel.businessType || 'inspection';
    return [...commonFields, ...(typeFields[businessType] || typeFields.inspection)];
  });

  async function load() {
    loading.value = true;
    try {
      const params: Record<string, any> = { pageNo: pagination.current, pageSize: pagination.pageSize };
      Object.entries(filters).forEach(([key, value]) => {
        if (value) params[key] = value;
      });
      const data: any = await defHttp.get({ url: '/roomops/record/list', params });
      rows.value = data?.records || [];
      pagination.total = data?.total || 0;
    } catch (e: any) {
      message.error(e?.message || '加载失败');
      rows.value = [];
    } finally {
      loading.value = false;
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
      }));
    } catch (e: any) {
      message.warning(e?.message || '机房列表加载失败');
      machineRoomOptions.value = [];
    }
  }

  async function loadDingtalkUsers() {
    try {
      const data: any = await defHttp.get({
        url: '/roomops/dingtalkUser/list',
        params: { pageNo: 1, pageSize: 200, active: 1 },
      });
      const userMap = new Map<string, any>();
      (data?.records || []).forEach((user) => {
        const name = user.name || '';
        if (!name || userMap.has(name)) return;
        userMap.set(name, {
          label: name,
          value: name,
        });
      });
      dingtalkUserOptions.value = Array.from(userMap.values());
    } catch (e: any) {
      message.warning(e?.message || '执行人员列表加载失败');
      dingtalkUserOptions.value = [];
    }
  }

  function reloadFirstPage() {
    pagination.current = 1;
    load();
  }

  function resetFilters() {
    filters.businessType = '';
    filters.domainCode = 'core_network';
    filters.inspectorName = '';
    filters.roomId = '';
    reloadFirstPage();
  }

  function handleTableChange(page: any) {
    pagination.current = page.current;
    pagination.pageSize = page.pageSize;
    load();
  }

  function summaryItems(record: any) {
    if (record.businessType === 'fault') {
      return [
        { label: '工单', value: record.faultOrderNo },
        { label: '处理', value: record.handlingResult },
      ];
    }
    if (record.businessType === 'engineering') {
      return [
        { label: '施工', value: record.constructionContent },
        { label: '问题', value: record.siteProblems },
        { label: '遗留', value: record.remainingIssues },
      ];
    }
    return [
      { label: '环境', value: record.environmentStatus },
      { label: '设备', value: record.deviceStatus },
      { label: '异常', value: record.exceptionDesc },
    ];
  }

  function summaryText(record: any) {
    return summaryItems(record)
      .map((item) => `${item.label}:${item.value || '-'}`)
      .join(' / ');
  }

  function uploadModeLabel(value?: string) {
    if (value === 'offline_retry') return '补传';
    if (value === 'online' || value === 'direct') return '直接上传';
    if (value === 'offline_pending') return '待上传';
    return value || '-';
  }

  function businessFieldKey(key?: string) {
    return ['exceptionDesc', 'faultOrderNo', 'handlingResult', 'constructionContent', 'siteProblems', 'remainingIssues', 'remarkNote'].includes(key || '');
  }

  function formatTime(value?: string) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
  }

  function openDetail(record: Record<string, any>) {
    detailRecordId.value = record.recordId;
    detailOpen.value = true;
  }

  function resetForm(record?: Record<string, any>) {
    Object.keys(formModel).forEach((key) => delete formModel[key]);
    if (record?.businessType) {
      formModel.businessType = record.businessType;
    }
    fields.value.forEach((field) => {
      formModel[field.name] = record?.[field.name] ?? field.defaultValue ?? '';
    });
    if (record?.id) formModel.id = record.id;
  }

  function onBusinessTypeChange() {
    const keepKeys = new Set(fields.value.map((field) => field.name));
    Object.keys(formModel).forEach((key) => {
      if (key !== 'id' && !keepKeys.has(key)) {
        delete formModel[key];
      }
    });
  }

  function openAdd() {
    isUpdate.value = false;
    detailMode.value = false;
    resetForm();
    modalOpen.value = true;
  }

  function openEdit(record: Record<string, any>) {
    isUpdate.value = true;
    detailMode.value = false;
    resetForm(record);
    modalOpen.value = true;
  }

  function openFormDetail(record: Record<string, any>) {
    isUpdate.value = true;
    detailMode.value = true;
    resetForm(record);
    modalOpen.value = true;
  }

  async function submit() {
    const url = isUpdate.value ? '/roomops/record/edit' : '/roomops/record/add';
    submitLoading.value = true;
    try {
      if (isUpdate.value) {
        await defHttp.put({ url, data: { ...formModel } });
      } else {
        await defHttp.post({ url, data: { ...formModel } });
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

  async function remove(record: Record<string, any>) {
    Modal.confirm({
      title: '确定删除这条记录？',
      onOk: async () => {
        await defHttp.delete({ url: '/roomops/record/delete', data: { id: record.id } }, { joinParamsToUrl: true });
        message.success('删除成功');
        await load();
      },
    });
  }

  async function openPhotos(record: Record<string, any>) {
    selectedRecord.value = record;
    photosOpen.value = true;
    photosLoading.value = true;
    revokePhotoUrls();
    try {
      const data: any = await defHttp.get({
        url: '/roomops/photo/list',
        params: { recordId: record.recordId, pageNo: 1, pageSize: 50 },
      });
      const photos = data?.records || [];
      photoCards.value = await Promise.all(photos.map(loadPhotoBlob));
    } catch (e: any) {
      message.error(e?.message || '照片加载失败');
      photoCards.value = [];
    } finally {
      photosLoading.value = false;
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

  function revokePhotoUrls() {
    photoCards.value.forEach((photo) => {
      if (photo.url) URL.revokeObjectURL(photo.url);
    });
    photoCards.value = [];
  }

  function closePhotos() {
    photosOpen.value = false;
    revokePhotoUrls();
  }

  onMounted(() => {
    loadMachineRooms();
    loadDingtalkUsers();
    load();
  });
</script>

<style scoped>
  .roomops-page {
    padding: 16px;
  }

  .roomops-page :deep(.ant-table-tbody > tr > td) {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
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

  .filter-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px 12px;
    align-items: center;
    margin-bottom: 14px;
    padding: 12px 16px;
    background: #fff;
  }

  .filter-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 32px;
  }

  .filter-label {
    flex: none;
    color: #333;
    font-weight: 500;
    line-height: 32px;
    white-space: nowrap;
  }

  .filter-select-sm {
    width: 130px;
  }

  .filter-select-md {
    width: 120px;
  }

  .filter-select-lg {
    width: 240px;
  }

  .filter-actions {
    margin-left: 4px;
    height: 32px;
  }

  @media (max-width: 1280px) {
    .filter-form {
      align-items: flex-start;
    }
  }

  .summary-cell {
    max-width: 260px;
    line-height: 1.55;
  }

  .summary-line {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .summary-line span {
    color: #888;
  }

  .count-link {
    padding: 0;
  }

  .more-btn {
    padding-left: 6px;
    padding-right: 6px;
  }

  .roomops-form :deep(.ant-form-item) {
    margin-bottom: 18px;
  }

  .photo-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
    gap: 14px;
    max-height: 70vh;
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
    background: #f5f5f5;
  }

  .thumb {
    width: 100%;
    height: 100%;
    object-fit: cover;
    cursor: zoom-in;
  }

  .photo-meta {
    padding: 8px 10px;
    color: #555;
    font-size: 12px;
    line-height: 1.6;
    word-break: break-all;
  }

  .large-photo-wrap {
    display: flex;
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
