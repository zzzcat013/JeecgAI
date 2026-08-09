<template>
  <div class="roomops-page">
    <div class="toolbar">
      <div class="page-title">{{ title }}</div>
      <a-space>
        <slot name="toolbar-extra" :reload="load"></slot>
        <a-button type="primary" @click="openAdd">新增</a-button>
        <a-button @click="load">刷新</a-button>
      </a-space>
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
        <template v-if="column.key === 'action'">
          <a-space size="small">
            <a-button type="link" @click="openDetail(record)">详情</a-button>
            <a-button v-if="imagePreview" type="link" @click="openImagePreview(record)">查看</a-button>
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
        <template v-else-if="isPreviewColumn(column)">
          <a-button type="link" class="filename-link" @click="openImagePreview(record)">
            <JEllipsis :value="record?.[column.dataIndex] || record?.storedFilename || record?.originalFilename || '查看图片'" :length="18" />
          </a-button>
        </template>
        <template v-else-if="column.ellipsisLength">
          <JEllipsis :value="record?.[column.dataIndex]" :length="column.ellipsisLength" />
        </template>
        <template v-else-if="column.valueMap">
          {{ column.valueMap[record?.[column.dataIndex]] ?? record?.[column.dataIndex] ?? '' }}
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

    <a-modal
      v-model:open="imagePreviewOpen"
      title="图片预览"
      width="860px"
      :footer="null"
      :body-style="{ padding: '12px' }"
      @cancel="closeImagePreview"
    >
      <div class="image-preview-wrap">
        <a-spin :spinning="imagePreviewLoading">
          <img v-if="imagePreviewUrl" :src="imagePreviewUrl" class="image-preview" />
          <a-empty v-else-if="!imagePreviewLoading" description="图片加载失败" />
        </a-spin>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { defHttp } from '/@/utils/http/axios';
  import { Modal, message } from 'ant-design-vue';
  import JEllipsis from '/@/components/Form/src/jeecg/components/JEllipsis.vue';
  import { usePermission } from '/@/hooks/web/usePermission';

  interface FieldConfig {
    name: string;
    label: string;
    type?: 'text' | 'textarea' | 'number' | 'datetime' | 'select';
    span?: number;
    defaultValue?: string | number;
    options?: { label: string; value: string | number }[];
  }

  const props = defineProps<{
    title: string;
    apiBase: string;
    columns: any[];
    fields: FieldConfig[];
    imagePreview?: boolean;
    editAuth?: string;
  }>();

  const rows = ref<any[]>([]);
  const loading = ref(false);
  const submitLoading = ref(false);
  const modalOpen = ref(false);
  const detailMode = ref(false);
  const imagePreviewOpen = ref(false);
  const imagePreviewLoading = ref(false);
  const imagePreviewUrl = ref('');
  const isUpdate = ref(false);
  const formModel = reactive<Record<string, any>>({});
  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ['5', '10', '20', '50'],
    showTotal: (total: number) => `共 ${total} 条`,
  });

  const tableColumns = computed(() => [
    ...props.columns,
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 210,
    },
  ]);
  const tableScrollX = computed(() =>
    Math.max(1000, tableColumns.value.reduce((sum, col) => sum + (Number(col.width) || 120), 0))
  );
  const modalTitle = computed(() => {
    if (detailMode.value) return `${props.title}详情`;
    return `${isUpdate.value ? '编辑' : '新增'}${props.title}`;
  });
  const imagePreview = computed(() => props.imagePreview === true);
  const { hasPermission } = usePermission();
  const canEdit = computed(() => (props.editAuth ? hasPermission(props.editAuth) : true));

  function resetForm(record?: Record<string, any>) {
    Object.keys(formModel).forEach((key) => delete formModel[key]);
    props.fields.forEach((field) => {
      formModel[field.name] = record?.[field.name] ?? field.defaultValue ?? '';
    });
    if (record?.id) {
      formModel.id = record.id;
    }
  }

  async function load() {
    loading.value = true;
    try {
      const data: any = await defHttp.get({
        url: `${props.apiBase}/list`,
        params: { pageNo: pagination.current, pageSize: pagination.pageSize },
      });
      rows.value = data?.records || [];
      pagination.total = data?.total || 0;
    } catch (e: any) {
      message.error(e?.message || '加载失败');
      rows.value = [];
    } finally {
      loading.value = false;
    }
  }

  function handleTableChange(page: any) {
    pagination.current = page.current;
    pagination.pageSize = page.pageSize;
    load();
  }

  function isPreviewColumn(column: any) {
    return imagePreview.value && (column?.dataIndex === 'storedFilename' || column?.key === 'storedFilename');
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

  function openDetail(record: Record<string, any>) {
    isUpdate.value = true;
    detailMode.value = true;
    resetForm(record);
    modalOpen.value = true;
  }

  async function openImagePreview(record: Record<string, any>) {
    if (!record?.id) {
      message.warning('缺少照片记录ID');
      return;
    }
    imagePreviewOpen.value = true;
    imagePreviewLoading.value = true;
    revokeImagePreviewUrl();
    try {
      const resp: any = await defHttp.get(
        { url: `${props.apiBase}/preview/${record.id}`, responseType: 'blob', timeout: 30000 },
        { isReturnNativeResponse: true, isTransformResponse: false }
      );
      imagePreviewUrl.value = URL.createObjectURL(resp.data);
    } catch (e: any) {
      message.error(e?.message || '图片加载失败');
      imagePreviewUrl.value = '';
    } finally {
      imagePreviewLoading.value = false;
    }
  }

  function revokeImagePreviewUrl() {
    if (imagePreviewUrl.value) {
      URL.revokeObjectURL(imagePreviewUrl.value);
      imagePreviewUrl.value = '';
    }
  }

  function closeImagePreview() {
    imagePreviewOpen.value = false;
    revokeImagePreviewUrl();
  }

  async function submit() {
    const url = isUpdate.value ? `${props.apiBase}/edit` : `${props.apiBase}/add`;
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
        await defHttp.delete({ url: `${props.apiBase}/delete`, data: { id: record.id } }, { joinParamsToUrl: true });
        message.success('删除成功');
        await load();
      },
    });
  }

  defineExpose({ load });

  onMounted(load);
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
    margin-bottom: 16px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
  }

  .roomops-form {
    padding-top: 2px;
  }

  .roomops-form :deep(.ant-form-item) {
    margin-bottom: 18px;
  }

  .filename-link {
    height: auto;
    max-width: 260px;
    padding: 0;
    white-space: normal;
    text-align: left;
    word-break: break-all;
  }

  .more-btn {
    padding-left: 6px;
    padding-right: 6px;
  }

  .roomops-form :deep(.ant-form-item-label) {
    padding-bottom: 6px;
  }

  .image-preview-wrap {
    display: flex;
    min-height: 240px;
    max-height: 72vh;
    align-items: center;
    justify-content: center;
    overflow: auto;
    background: #f5f5f5;
  }

  .image-preview {
    max-width: 100%;
    max-height: 70vh;
    object-fit: contain;
  }
</style>
