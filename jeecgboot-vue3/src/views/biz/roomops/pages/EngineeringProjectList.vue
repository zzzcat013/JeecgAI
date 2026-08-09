<template>
  <div class="roomops-page">
    <div class="toolbar">
      <div class="page-title">{{ pageTitle }}</div>
      <a-space>
        <a-button v-if="!isArchivedView" type="primary" @click="openAdd">新增工程</a-button>
        <a-button @click="load">刷新</a-button>
      </a-space>
    </div>

    <div class="filter-form">
      <div class="filter-item">
        <span class="filter-label">工程编号</span>
        <a-input v-model:value="filters.projectId" allow-clear class="filter-select-md" placeholder="模糊查询" @pressEnter="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">工程名称</span>
        <a-input v-model:value="filters.projectName" allow-clear class="filter-select-md" placeholder="模糊查询" @pressEnter="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">类别</span>
        <a-input v-model:value="filters.category" allow-clear class="filter-select-sm" placeholder="模糊查询" @pressEnter="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">状态</span>
        <a-select v-model:value="filters.status" :options="statusOptions" allow-clear class="filter-select-sm" @change="reloadFirstPage" />
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
      :scroll="{ x: 1600 }"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'projectId'">
          <JEllipsis :value="record.projectId || '-'" :length="18" />
        </template>
        <template v-else-if="column.key === 'projectName'">
          <JEllipsis :value="record.projectName || '-'" :length="16" />
        </template>
        <template v-else-if="column.key === 'category'">
          <JEllipsis :value="record.category || '-'" :length="12" />
        </template>
        <template v-else-if="column.key === 'statusName'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'roomId'">
          <JEllipsis :value="record.roomId || '-'" :length="12" />
        </template>
        <template v-else-if="column.key === 'attachmentCount'">
          <a-button type="link" class="count-link" :disabled="!record.attachmentCount" @click="openAttachments(record)">
            {{ record.attachmentCount || 0 }} 个
          </a-button>
        </template>
        <template v-else-if="column.key === 'taskCount'">
          <a-button type="link" class="count-link" :disabled="!record.taskCount" @click="openTasks(record)">
            {{ record.taskCount || 0 }} 条
          </a-button>
        </template>
        <template v-else-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <a-button type="link" @click="openDetail(record)">详情</a-button>
            <a-dropdown :trigger="['click']">
              <a-button type="link" class="more-btn">
                更多
                <Icon icon="mdi:chevron-down"></Icon>
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item v-if="canEdit && !isArchivedRecord(record)" key="edit" @click="openEdit(record)">编辑</a-menu-item>
                  <a-menu-item v-if="canEdit && !isArchivedRecord(record)" key="status" @click="openStatus(record)">状态更新</a-menu-item>
                  <a-menu-item v-if="!isArchivedRecord(record)" key="task" @click="openDispatch(record)">下发任务</a-menu-item>
                  <a-menu-item v-if="canEdit && !isArchivedRecord(record)" key="archive" @click="toggleArchive(record, true)">归档</a-menu-item>
                  <a-menu-item v-if="canEdit && isArchivedRecord(record)" key="restore" @click="toggleArchive(record, false)">恢复</a-menu-item>
                  <a-menu-item v-if="canEdit" key="delete" @click="remove(record)">删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="projectFormOpen"
      :title="formTitle"
      width="920px"
      :confirm-loading="submitLoading"
      :footer="detailMode ? null : undefined"
      :body-style="{ padding: '20px 28px 8px' }"
      @ok="submitProject"
      @cancel="projectFormOpen = false"
    >
      <EngineeringProjectForm ref="projectFormRef" :key="formKey" :record="selectedProject" :readonly="detailMode" />
    </a-modal>

    <a-modal
      v-model:open="statusOpen"
      title="工程状态更新"
      width="440px"
      :confirm-loading="submitLoading"
      @ok="submitStatus"
      @cancel="statusOpen = false"
    >
      <div class="modal-tip">工程编号：{{ selectedProject?.projectId }}</div>
      <a-select v-model:value="statusValue" :options="statusOptions" style="width: 100%" />
    </a-modal>

    <a-modal
      v-model:open="dispatchOpen"
      title="下发工程任务"
      width="720px"
      :confirm-loading="submitLoading"
      @ok="submitDispatch"
      @cancel="dispatchOpen = false"
    >
      <a-form :model="dispatchForm" layout="vertical" class="roomops-form">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="工程编号">
              <a-input :value="selectedProject?.projectId" disabled />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="机房">
              <a-input :value="selectedProject?.roomId" disabled />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="任务编号">
              <a-input v-model:value="dispatchForm.taskId" placeholder="不填自动生成 ER-..." />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="执行人">
              <a-select
                v-model:value="dispatchForm.assigneeUserid"
                :options="dingtalkUserOptions"
                allow-clear
                show-search
                option-filter-prop="label"
                placeholder="不选则进入待接任务"
                @change="fillAssigneeName"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="优先级">
              <a-select v-model:value="dispatchForm.priority" :options="priorityOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="截止时间">
              <a-date-picker
                v-model:value="dispatchForm.deadlineAt"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="任务标题">
              <a-input v-model:value="dispatchForm.taskTitle" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="任务内容">
              <a-textarea v-model:value="dispatchForm.taskContent" :rows="4" placeholder="施工随工、检查要求等" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="attachmentsOpen"
      :title="attachmentsTitle"
      width="980px"
      :footer="null"
      :body-style="{ padding: '16px 24px' }"
      @cancel="attachmentsOpen = false"
    >
      <a-spin :spinning="attachmentsLoading">
        <a-table
          v-if="attachments.length"
          :columns="attachmentColumns"
          :data-source="attachments"
          row-key="id"
          size="small"
          :pagination="false"
          :scroll="{ x: 900 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'docTypeName'">
              {{ docTypeLabel(record.docType) }}
            </template>
            <template v-else-if="column.key === 'originalFilename'">
              <JEllipsis :value="record.originalFilename || '-'" :length="20" />
            </template>
            <template v-else-if="column.key === 'fileSize'">
              {{ formatSize(record.fileSize) }}
            </template>
            <template v-else-if="column.key === 'uploadedAt'">
              {{ formatTime(record.uploadedAt) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a-button type="link" @click="previewFile(record)">预览</a-button>
                <a-button type="link" @click="downloadAttachment(record)">下载</a-button>
                <a-button v-if="canEdit" type="link" danger @click="removeAttachment(record)">删除</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
        <a-empty v-else-if="!attachmentsLoading" description="暂无附件" />
      </a-spin>
    </a-modal>

    <a-modal
      v-model:open="tasksOpen"
      :title="tasksTitle"
      width="980px"
      :footer="null"
      :body-style="{ padding: '16px 24px' }"
      @cancel="tasksOpen = false"
    >
      <a-spin :spinning="tasksLoading">
        <a-table
          v-if="projectTasks.length"
          :columns="taskColumns"
          :data-source="projectTasks"
          row-key="id"
          size="small"
          :pagination="false"
          :scroll="{ x: 900 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'taskId'">
              <JEllipsis :value="record.taskId || '-'" :length="18" />
            </template>
            <template v-else-if="column.key === 'taskContentCell'">
              <JEllipsis :value="taskSummary(record)" :length="24" />
            </template>
            <template v-else-if="column.key === 'assigneeName'">
              {{ record.assigneeName || (record.status === 'AVAILABLE' ? '待接单' : '-') }}
            </template>
            <template v-else-if="column.key === 'statusName'">
              <a-tag :color="taskStatusColor(record.status)">{{ taskStatusLabel(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'submittedAt'">
              {{ formatTime(record.submittedAt) }}
            </template>
          </template>
        </a-table>
        <a-empty v-else-if="!tasksLoading" description="暂无工程任务" />
      </a-spin>
    </a-modal>

    <AttachmentPreviewModal v-model:open="previewOpen" :attachment="previewAttachment" />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { Modal, message } from 'ant-design-vue';
  import JEllipsis from '/@/components/Form/src/jeecg/components/JEllipsis.vue';
  import { usePermission } from '/@/hooks/web/usePermission';
  import EngineeringProjectForm from '../components/EngineeringProjectForm.vue';
  import AttachmentPreviewModal from '../components/AttachmentPreviewModal.vue';
  import {
    archiveEngineeringProject,
    createEngineeringProject,
    createTask,
    deleteEngineeringAttachment,
    deleteEngineeringProject,
    listEngineeringAttachments,
    listEngineeringProjects,
    listEngineeringTasks,
    queryEngineeringProject,
    updateEngineeringProject,
    updateEngineeringStatus,
  } from '../api/roomops.api';
  import { defHttp } from '/@/utils/http/axios';

  const props = defineProps<{
    viewMode?: 'active' | 'archived';
    pageTitle?: string;
  }>();

  const isArchivedView = computed(() => props.viewMode === 'archived');
  const pageTitle = computed(() => props.pageTitle || (isArchivedView.value ? '已归档工程' : '工程列表'));
  const { hasPermission } = usePermission();
  const canEdit = computed(() => hasPermission('roomops:engineering:edit'));

  const rows = ref<any[]>([]);
  const loading = ref(false);
  const submitLoading = ref(false);
  const projectFormOpen = ref(false);
  const detailMode = ref(false);
  const selectedProject = ref<any>(null);
  const formKey = ref(0);
  const projectFormRef = ref<any>(null);
  const statusOpen = ref(false);
  const statusValue = ref('NOT_STARTED');
  const dispatchOpen = ref(false);
  const attachmentsOpen = ref(false);
  const attachmentsLoading = ref(false);
  const attachments = ref<any[]>([]);
  const previewOpen = ref(false);
  const previewAttachment = ref<any>(null);
  const tasksOpen = ref(false);
  const tasksLoading = ref(false);
  const projectTasks = ref<any[]>([]);
  const machineRoomOptions = ref<any[]>([]);
  const dingtalkUserOptions = ref<any[]>([]);
  const dispatchForm = reactive<Record<string, any>>({});
  const filters = reactive({ projectId: '', projectName: '', category: '', status: '', roomId: '' });
  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ['5', '10', '20', '50'],
    showTotal: (total: number) => `共 ${total} 条`,
  });

  const statusOptions = [
    { label: '未开工', value: 'NOT_STARTED' },
    { label: '开工', value: 'STARTED' },
    { label: '实施中', value: 'IN_PROGRESS' },
    { label: '完工', value: 'COMPLETED' },
    { label: '验收完成', value: 'ACCEPTED' },
  ];
  const priorityOptions = [
    { label: '低', value: 'low' },
    { label: '普通', value: 'normal' },
    { label: '高', value: 'high' },
    { label: '紧急', value: 'urgent' },
  ];

  const tableColumns = [
    { title: '工程编号', dataIndex: 'projectId', key: 'projectId', width: 190 },
    { title: '工程名称', dataIndex: 'projectName', key: 'projectName', width: 180 },
    { title: '类别', dataIndex: 'category', key: 'category', width: 120 },
    { title: '状态', dataIndex: 'status', key: 'statusName', width: 90 },
    { title: '专业', dataIndex: 'domainName', key: 'domainName', width: 80 },
    { title: '地市', dataIndex: 'regionName', key: 'regionName', width: 70 },
    { title: '机房', dataIndex: 'roomId', key: 'roomId', width: 120 },
    { title: '负责人', dataIndex: 'startReportPerson', key: 'startReportPerson', width: 80 },
    { title: '附件', key: 'attachmentCount', width: 70 },
    { title: '任务', key: 'taskCount', width: 70 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 130 },
    { title: '操作', key: 'action', fixed: 'right', width: 170 },
  ];

  const attachmentColumns = [
    { title: '附件类型', dataIndex: 'docType', key: 'docTypeName', width: 110 },
    { title: '文件名', dataIndex: 'originalFilename', key: 'originalFilename', width: 260 },
    { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 90 },
    { title: '上传人', dataIndex: 'uploaderName', key: 'uploaderName', width: 90 },
    { title: '上传时间', dataIndex: 'uploadedAt', key: 'uploadedAt', width: 140 },
    { title: '操作', key: 'action', fixed: 'right', width: 180 },
  ];

  const taskColumns = [
    { title: '任务编号', dataIndex: 'taskId', key: 'taskId', width: 190 },
    { title: '任务内容', key: 'taskContentCell', width: 260 },
    { title: '执行人', key: 'assigneeName', width: 100 },
    { title: '状态', key: 'statusName', width: 90 },
    { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 140 },
  ];

  const formTitle = computed(() => {
    if (detailMode.value) return '工程详情';
    return selectedProject.value?.id ? '编辑工程' : '新增工程';
  });
  const attachmentsTitle = computed(() => `附件列表 - ${selectedProject.value?.projectId || ''}`);
  const tasksTitle = computed(() => `工程任务 - ${selectedProject.value?.projectId || ''}`);

  function statusLabel(value?: string) {
    const map: Record<string, string> = {
      NOT_STARTED: '未开工',
      STARTED: '开工',
      IN_PROGRESS: '实施中',
      COMPLETED: '完工',
      ACCEPTED: '验收完成',
    };
    return map[value || ''] || value || '-';
  }

  function statusColor(value?: string) {
    const map: Record<string, string> = {
      NOT_STARTED: 'default',
      STARTED: 'blue',
      IN_PROGRESS: 'processing',
      COMPLETED: 'cyan',
      ACCEPTED: 'green',
    };
    return map[value || ''] || 'default';
  }

  function taskStatusLabel(value?: string) {
    const map: Record<string, string> = {
      AVAILABLE: '待接单',
      ASSIGNED: '已派单',
      SUBMITTED: '已提交',
      REOPENED: '驳回重发',
      DONE: '已完成',
    };
    return map[value || ''] || value || '-';
  }

  function taskStatusColor(value?: string) {
    const map: Record<string, string> = {
      AVAILABLE: 'orange',
      ASSIGNED: 'blue',
      SUBMITTED: 'cyan',
      REOPENED: 'red',
      DONE: 'green',
    };
    return map[value || ''] || 'default';
  }

  function docTypeLabel(value?: string) {
    const map: Record<string, string> = {
      START_REPORT: '开工报告',
      PLAN: '施工方案',
      TECHNICAL: '技术交底',
      SAFETY: '安全交底',
      OTHER: '其他附件',
    };
    return map[value || ''] || value || '-';
  }

  function formatTime(value?: string) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
  }

  function formatSize(size?: number) {
    if (size == null) return '-';
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / 1024 / 1024).toFixed(2)} MB`;
  }

  function taskSummary(record: any) {
    const parts = [record.taskTitle, record.taskContent].filter((item) => item);
    return parts.join(' - ') || '无任务内容';
  }

  function isArchivedRecord(record: any) {
    return Number(record.archived) === 1;
  }

  async function load() {
    loading.value = true;
    try {
      const params: Record<string, any> = { pageNo: pagination.current, pageSize: pagination.pageSize };
      if (isArchivedView.value) params.archived = 1;
      else params.archived = 0;
      Object.entries(filters).forEach(([key, value]) => {
        if (value) params[key] = value;
      });
      const data: any = await listEngineeringProjects(params);
      rows.value = data?.records || [];
      pagination.total = data?.total || 0;
    } catch (e: any) {
      message.error(e?.message || '工程列表加载失败');
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
    } catch {
      machineRoomOptions.value = [];
    }
  }

  async function loadDingtalkUsers() {
    try {
      const data: any = await defHttp.get({
        url: '/roomops/dingtalkUser/list',
        params: { pageNo: 1, pageSize: 200, active: 1 },
      });
      dingtalkUserOptions.value = (data?.records || []).map((user) => ({
        label: user.name ? `${user.name}${user.deptName ? `（${user.deptName}）` : ''}` : user.dingtalkUserid,
        value: user.dingtalkUserid || '',
        name: user.name || '',
      }));
    } catch {
      dingtalkUserOptions.value = [];
    }
  }

  function reloadFirstPage() {
    pagination.current = 1;
    load();
  }

  function resetFilters() {
    filters.projectId = '';
    filters.projectName = '';
    filters.category = '';
    filters.status = '';
    filters.roomId = '';
    reloadFirstPage();
  }

  function handleTableChange(page: any) {
    pagination.current = page.current;
    pagination.pageSize = page.pageSize;
    load();
  }

  function openAdd() {
    detailMode.value = false;
    selectedProject.value = {};
    formKey.value += 1;
    projectFormOpen.value = true;
  }

  async function openEdit(record: any) {
    detailMode.value = false;
    try {
      const data: any = await queryEngineeringProject({ id: record.id });
      selectedProject.value = data;
      formKey.value += 1;
      projectFormOpen.value = true;
    } catch (e: any) {
      message.error(e?.message || '工程详情加载失败');
    }
  }

  async function openDetail(record: any) {
    detailMode.value = true;
    try {
      const data: any = await queryEngineeringProject({ id: record.id });
      selectedProject.value = data;
      formKey.value += 1;
      projectFormOpen.value = true;
    } catch (e: any) {
      message.error(e?.message || '工程详情加载失败');
    }
  }

  async function submitProject() {
    const form = projectFormRef.value;
    if (!form) return;
    const payload = form.getPayload();
    if (!payload.projectName) {
      message.error('请填写工程名称');
      return;
    }
    submitLoading.value = true;
    try {
      let projectId = payload.projectId;
      if (payload.id) {
        await updateEngineeringProject(payload);
      } else {
        const created: any = await createEngineeringProject(payload);
        projectId = created?.projectId || payload.projectId;
      }
      if (form.getPendingFiles().length) {
        await form.saveAttachments(projectId);
      }
      message.success(payload.id ? '工程已更新' : '工程已保存');
      projectFormOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '保存失败，请检查后台日志');
    } finally {
      submitLoading.value = false;
    }
  }

  function openStatus(record: any) {
    selectedProject.value = record;
    statusValue.value = record.status || 'NOT_STARTED';
    statusOpen.value = true;
  }

  async function submitStatus() {
    submitLoading.value = true;
    try {
      await updateEngineeringStatus({ projectId: selectedProject.value.projectId, status: statusValue.value });
      message.success('工程状态已更新');
      statusOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '状态更新失败');
    } finally {
      submitLoading.value = false;
    }
  }

  function openDispatch(record: any) {
    selectedProject.value = record;
    Object.keys(dispatchForm).forEach((key) => delete dispatchForm[key]);
    dispatchForm.taskId = '';
    dispatchForm.taskTitle = '';
    dispatchForm.taskContent = '';
    dispatchForm.assigneeUserid = '';
    dispatchForm.assigneeName = '';
    dispatchForm.priority = 'normal';
    dispatchForm.deadlineAt = '';
    dispatchOpen.value = true;
  }

  function fillAssigneeName(userid: string) {
    const user = dingtalkUserOptions.value.find((item) => item.value === userid);
    dispatchForm.assigneeName = user?.name || '';
    dispatchForm.assigneeUserid = userid || '';
  }

  async function submitDispatch() {
    if (!dispatchForm.taskTitle && !dispatchForm.taskContent) {
      message.error('请填写任务标题或任务内容');
      return;
    }
    submitLoading.value = true;
    try {
      const project = selectedProject.value;
      const created: any = await createTask({
        businessType: 'engineering',
        projectId: project.projectId,
        taskId: dispatchForm.taskId,
        taskTitle: dispatchForm.taskTitle,
        taskContent: dispatchForm.taskContent,
        roomId: project.roomId,
        roomName: project.roomName,
        domainCode: project.domainCode || 'core_network',
        domainShortCode: project.domainShortCode || 'CORE',
        domainName: project.domainName || '核心网',
        regionCode: project.regionCode || 'TY',
        regionName: project.regionName || '太原',
        assigneeUserid: dispatchForm.assigneeUserid,
        assigneeName: dispatchForm.assigneeName,
        priority: dispatchForm.priority,
        deadlineAt: dispatchForm.deadlineAt,
      });
      message.success(`工程任务已下发：${created?.taskId || ''}`);
      dispatchOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '任务下发失败');
    } finally {
      submitLoading.value = false;
    }
  }

  async function toggleArchive(record: any, archived: boolean) {
    try {
      await archiveEngineeringProject({ projectId: record.projectId, archived });
      message.success(archived ? '工程已归档' : '工程已恢复');
      await load();
    } catch (e: any) {
      message.error(e?.message || (archived ? '归档失败' : '恢复失败'));
    }
  }

  function remove(record: any) {
    Modal.confirm({
      title: '确定删除这条工程记录？',
      onOk: async () => {
        await deleteEngineeringProject(record.id);
        message.success('删除成功');
        await load();
      },
    });
  }

  async function openAttachments(record: any) {
    selectedProject.value = record;
    attachmentsOpen.value = true;
    attachmentsLoading.value = true;
    attachments.value = [];
    try {
      const data: any = await listEngineeringAttachments({ projectId: record.projectId });
      attachments.value = data || [];
    } catch (e: any) {
      message.error(e?.message || '附件列表加载失败');
    } finally {
      attachmentsLoading.value = false;
    }
  }

  async function removeAttachment(att: any) {
    try {
      await deleteEngineeringAttachment(att.id);
      attachments.value = attachments.value.filter((item) => item.id !== att.id);
      message.success('附件已删除');
      await load();
    } catch (e: any) {
      message.error(e?.message || '附件删除失败');
    }
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

  async function openTasks(record: any) {
    selectedProject.value = record;
    tasksOpen.value = true;
    tasksLoading.value = true;
    projectTasks.value = [];
    try {
      const data: any = await listEngineeringTasks(record.projectId);
      projectTasks.value = data || [];
    } catch (e: any) {
      message.error(e?.message || '工程任务加载失败');
    } finally {
      tasksLoading.value = false;
    }
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
    width: 120px;
  }

  .filter-select-md {
    width: 180px;
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

  .more-btn {
    padding-left: 6px;
    padding-right: 6px;
  }

  .count-link {
    padding: 0;
  }

  .modal-tip {
    margin-bottom: 12px;
    color: #555;
  }

</style>
