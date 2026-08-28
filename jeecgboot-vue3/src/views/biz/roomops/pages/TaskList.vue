<template>
  <div class="roomops-page">
    <div class="toolbar">
      <div class="page-title">{{ pageTitle }}</div>
      <a-space>
        <template v-if="viewMode === 'active'">
          <a-button type="primary" @click="openAdd">派单</a-button>
          <a-button @click="pushAll">重新同步全部活动任务</a-button>
        </template>
        <a-button @click="load">刷新</a-button>
      </a-space>
    </div>

    <div class="filter-form">
      <div v-if="!props.businessType" class="filter-item">
        <span class="filter-label">业务</span>
        <a-select v-model:value="filters.businessType" :options="businessTypeOptions" allow-clear class="filter-select-sm" @change="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">工程编号</span>
        <a-select
          v-model:value="filters.projectId"
          :options="engineeringProjectOptions"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="请选择工程"
          class="filter-select-md"
          @change="reloadFirstPage"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">专业</span>
        <a-select v-model:value="filters.domainCode" :options="domainOptions" allow-clear class="filter-select-sm" @change="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">状态</span>
        <a-select v-model:value="filters.status" :options="statusOptions" allow-clear class="filter-select-sm" @change="reloadFirstPage" />
      </div>
      <div class="filter-item">
        <span class="filter-label">执行人</span>
        <a-select
          v-model:value="filters.assigneeName"
          :options="dingtalkUserOptions"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="请选择执行人"
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
      :scroll="{ x: 1500 }"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'taskId'">
          <JEllipsis :value="record.taskId" :length="16" />
        </template>
        <template v-else-if="column.key === 'businessTypeName'">
          {{ businessTypeLabel(record.businessType) }}
        </template>
        <template v-else-if="column.key === 'projectId'">
          <JEllipsis :value="record.projectId || '-'" :length="14" />
        </template>
        <template v-else-if="column.key === 'roomId'">
          <JEllipsis :value="record.roomId || '-'" :length="10" />
        </template>
        <template v-else-if="column.key === 'taskContentCell'">
          <JEllipsis :value="taskSummary(record)" :length="20" />
        </template>
        <template v-else-if="column.key === 'assignerName'">
          {{ record.assignerName || '-' }}
        </template>
        <template v-else-if="column.key === 'assigneeName'">
          <template v-if="record.assigneeName">{{ record.assigneeName }}</template>
          <template v-else-if="record.status === 'AVAILABLE'">
            待接单<template v-if="record.candidateNames">（{{ record.candidateNames }}）</template><template v-else>（所有人可认领）</template>
          </template>
          <template v-else>-</template>
        </template>
        <template v-else-if="column.key === 'statusName'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          <a-tag v-if="record.warning" color="red">预警</a-tag>
        </template>
        <template v-else-if="column.key === 'deadlineAt'">
          {{ formatTime(record.deadlineAt) }}
        </template>
        <template v-else-if="column.key === 'submittedAt'">
          {{ formatTime(record.submittedAt) }}
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
                  <a-menu-item v-if="record.recordId" key="record" @click="openRecordDetail(record.recordId)">查看提交</a-menu-item>
                  <a-menu-item v-if="canConfirm(record)" key="confirm" @click="openConfirm(record)">确认完成</a-menu-item>
                  <a-menu-item v-if="canReject(record)" key="reject" @click="openReject(record)">驳回重发</a-menu-item>
                  <a-menu-item v-if="canPush(record)" key="push" @click="pushOne(record)">重新同步到小程序</a-menu-item>
                  <a-menu-item v-if="!isArchived(record)" key="edit" @click="openEdit(record)">编辑</a-menu-item>
                  <a-menu-item v-if="!isArchived(record)" key="archive" @click="toggleArchive(record, true)">归档</a-menu-item>
                  <a-menu-item v-if="isArchived(record)" key="restore" @click="toggleArchive(record, false)">恢复</a-menu-item>
                  <a-menu-item key="delete" @click="remove(record)">删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="formOpen"
      :title="formTitle"
      width="820px"
      :confirm-loading="submitLoading"
      :body-style="{ padding: '20px 28px 4px' }"
      @ok="submit"
      @cancel="formOpen = false"
    >
      <a-form :model="formModel" layout="vertical" class="roomops-form">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="业务类型">
              <a-select v-model:value="formModel.businessType" :options="businessTypeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="任务编号">
              <a-input v-model:value="formModel.taskId" placeholder="不填自动生成，如 IR-CORE-ROOM001-XXXX" />
            </a-form-item>
          </a-col>
          <a-col v-if="formModel.businessType === 'engineering'" :span="12">
            <a-form-item label="关联工程">
              <a-select
                v-model:value="formModel.projectId"
                :options="engineeringProjectOptions"
                show-search
                option-filter-prop="label"
                placeholder="请选择工程"
                @change="fillProjectInfo"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="机房（可多选）">
              <a-select
                v-model:value="formModel.roomIds"
                mode="multiple"
                :options="machineRoomOptions"
                show-search
                option-filter-prop="label"
                placeholder="可多选，将按机房生成多个任务"
              />
            </a-form-item>
          </a-col>
          <a-col v-if="isUpdate" :span="12">
            <a-form-item label="执行人">
              <a-select
                v-model:value="formModel.assigneeUserid"
                :options="dingtalkUserOptions"
                allow-clear
                show-search
                option-filter-prop="label"
                placeholder="不选则进入待接任务"
                @change="fillAssigneeName"
              />
            </a-form-item>
          </a-col>
          <a-col v-if="!isUpdate" :span="12">
            <a-form-item label="可认领人员（可多选）">
              <a-select
                v-model:value="formModel.candidateUserids"
                mode="multiple"
                :options="dingtalkUserOptions"
                allow-clear
                show-search
                option-filter-prop="label"
                placeholder="仅所选人员可看到并认领，不选则所有人可认领"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="专业">
              <a-select v-model:value="formModel.domainCode" :options="domainOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="优先级">
              <a-select v-model:value="formModel.priority" :options="priorityOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="截止时间">
              <a-date-picker
                v-model:value="formModel.deadlineAt"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="任务标题">
              <a-input v-model:value="formModel.taskTitle" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="任务内容">
              <a-textarea v-model:value="formModel.taskContent" :rows="4" placeholder="巡检内容、故障现象、施工要求等" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="detailOpen"
      title="任务详情"
      width="860px"
      :footer="null"
      :body-style="{ padding: '16px 24px' }"
      @cancel="detailOpen = false"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="detailTask">
          <a-descriptions :column="2" size="small" bordered class="detail-desc">
            <a-descriptions-item label="任务编号" :span="2">{{ detailTask.taskId }}</a-descriptions-item>
            <a-descriptions-item label="业务类型">{{ businessTypeLabel(detailTask.businessType) }}</a-descriptions-item>
            <a-descriptions-item v-if="detailTask.projectId" label="工程编号">{{ detailTask.projectId }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(detailTask.status)">{{ statusLabel(detailTask.status) }}</a-tag>
              <a-tag v-if="detailTask.warning" color="red">预警</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="机房">{{ detailTask.roomId }} {{ detailTask.roomName || '' }}</a-descriptions-item>
            <a-descriptions-item label="执行人">{{ detailTask.assigneeName || '待接单' }}</a-descriptions-item>
            <a-descriptions-item label="候选执行人">{{ detailTask.candidateNames || '所有人可认领' }}</a-descriptions-item>
            <a-descriptions-item label="派单人">{{ detailTask.assignerName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="当前轮次">{{ detailTask.roundCount || 1 }}</a-descriptions-item>
            <a-descriptions-item label="截止时间">{{ detailTask.deadlineAt || '-' }}</a-descriptions-item>
            <a-descriptions-item label="提交时间">{{ detailTask.submittedAt || '-' }}</a-descriptions-item>
            <a-descriptions-item label="确认时间">{{ detailTask.confirmedAt || '-' }}</a-descriptions-item>
            <a-descriptions-item v-if="detailTask.archivedAt" label="归档状态" :span="2">
              已归档（{{ detailTask.archivedAt }}，{{ detailTask.archivedBy || '-' }}）
            </a-descriptions-item>
            <a-descriptions-item v-if="detailTask.recordId" label="提交记录" :span="2">
              <a-space>
                <span>{{ detailTask.recordId }}</span>
                <a-button type="link" size="small" @click="openRecordDetail(detailTask.recordId)">查看提交</a-button>
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item v-if="detailTask.rejectRemark" label="驳回备注" :span="2">
              {{ detailTask.rejectRemark }}
            </a-descriptions-item>
            <a-descriptions-item v-if="detailTask.confirmRemark" label="确认备注" :span="2">
              {{ detailTask.confirmRemark }}
            </a-descriptions-item>
            <a-descriptions-item label="任务内容" :span="2">
              <div class="content-text">{{ detailTask.taskContent || '无' }}</div>
            </a-descriptions-item>
          </a-descriptions>
          <div class="round-title">提交记录（{{ detailSubmissions.length }}）</div>
          <a-list v-if="detailSubmissions.length" bordered size="small" :data-source="detailSubmissions">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #title>
                    第 {{ item.submissionNo || 1 }} 次 · {{ submissionTypeLabel(item.submissionType) }}
                    <a-tag :color="reviewStatusColor(item.reviewStatus)">{{ reviewStatusLabel(item.reviewStatus) }}</a-tag>
                  </template>
                  <template #description>
                    {{ item.inspectorName || '-' }} · {{ formatTime(item.submittedAt) }} · {{ item.photoCount || 0 }} 张照片
                  </template>
                </a-list-item-meta>
                <a-button type="link" @click="openRecordDetail(item.recordId)">查看本次内容</a-button>
              </a-list-item>
            </template>
          </a-list>
          <a-empty v-else description="暂无提交记录" />
          <div class="round-title">流转记录</div>
          <a-timeline v-if="detailTask.rounds?.length" class="round-timeline">
            <a-timeline-item v-for="round in detailTask.rounds" :key="round.id">
              <div class="round-line">
                <span class="round-action">{{ actionLabel(round.action) }}</span>
                <span class="round-operator">{{ round.operatorName || '系统' }}</span>
                <span class="round-time">{{ round.actionTime || round.createTime || '' }}</span>
              </div>
              <div v-if="round.remark" class="round-remark">{{ round.remark }}</div>
              <a-button v-if="round.recordId" type="link" size="small" @click="openRecordDetail(round.recordId)">查看本次提交</a-button>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无流转记录" />
        </template>
      </a-spin>
    </a-modal>

    <a-modal
      v-model:open="confirmOpen"
      title="确认任务完成"
      width="520px"
      :confirm-loading="submitLoading"
      @ok="submitConfirm"
      @cancel="confirmOpen = false"
    >
      <div class="modal-tip">任务编号：{{ selectedTask?.taskId }}</div>
      <a-textarea v-model:value="confirmRemark" :rows="3" placeholder="确认备注（选填）" />
    </a-modal>

    <a-modal
      v-model:open="rejectOpen"
      title="驳回并重新下发"
      width="560px"
      :confirm-loading="submitLoading"
      @ok="submitReject"
      @cancel="rejectOpen = false"
    >
      <div class="modal-tip">任务编号：{{ selectedTask?.taskId }}，任务 ID 保持不变。</div>
      <a-form layout="vertical" class="roomops-form">
        <a-form-item label="驳回备注">
          <a-textarea v-model:value="rejectRemark" :rows="3" placeholder="说明需要整改或补充的内容" />
        </a-form-item>
        <a-form-item label="重新指定执行人">
          <a-select
            v-model:value="rejectReassignUserid"
            :options="dingtalkUserOptions"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="不选保留原执行人"
            @change="fillReassignName"
          />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="rejectClearAssignee">改为待接任务，不再指定执行人</a-checkbox>
        </a-form-item>
      </a-form>
    </a-modal>

    <RecordDetailModal v-model:open="recordDetailOpen" :record-id="recordDetailId" title="提交的业务记录" />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { Modal, message } from 'ant-design-vue';
  import JEllipsis from '/@/components/Form/src/jeecg/components/JEllipsis.vue';
  import RecordDetailModal from '../components/RecordDetailModal.vue';
  import {
    archiveTask,
    confirmTask,
    createTask,
    deleteTask,
    listTasks,
    listTaskRecords,
    pushTask,
    queryTask,
    rejectTask,
    updateTask,
  } from '../api/roomops.api';
  import { defHttp } from '/@/utils/http/axios';

  const props = defineProps<{
    viewMode?: 'active' | 'archived' | 'warning' | 'mine';
    businessType?: string;
    pageTitle?: string;
  }>();

  const viewMode = computed(() => props.viewMode || 'active');
  const pageTitle = computed(
    () =>
      props.pageTitle ||
      ({
        active: '任务分派',
        archived: '已归档任务',
        warning: '任务预警',
        mine: '我的任务',
      }[viewMode.value] || '任务分派')
  );

  const rows = ref<any[]>([]);
  const loading = ref(false);
  const submitLoading = ref(false);
  const formOpen = ref(false);
  const isUpdate = ref(false);
  const detailOpen = ref(false);
  const detailLoading = ref(false);
  const confirmOpen = ref(false);
  const rejectOpen = ref(false);
  const detailTask = ref<any>(null);
  const detailSubmissions = ref<any[]>([]);
  const selectedTask = ref<any>(null);
  const confirmRemark = ref('');
  const rejectRemark = ref('');
  const rejectReassignUserid = ref('');
  const rejectReassignName = ref('');
  const rejectClearAssignee = ref(false);
  const recordDetailOpen = ref(false);
  const recordDetailId = ref('');
  const formModel = reactive<Record<string, any>>({});
  const machineRoomOptions = ref<any[]>([]);
  const dingtalkUserOptions = ref<any[]>([]);
  const engineeringProjectOptions = ref<any[]>([]);
  const filters = reactive({
    businessType: props.businessType || '',
    projectId: '',
    domainCode: 'core_network',
    status: '',
    assigneeName: '',
    roomId: '',
  });
  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ['5', '10', '20', '50'],
    showTotal: (total: number) => `共 ${total} 条`,
  });

  const businessTypeOptions = [
    { label: '巡检', value: 'inspection' },
    { label: '故障', value: 'fault' },
    { label: '工程', value: 'engineering' },
  ];
  const domainOptions = [
    { label: '核心网 CORE', value: 'core_network' },
    { label: '动力', value: 'power' },
    { label: '承载网', value: 'transport' },
  ];
  const statusOptions = [
    { label: '待接单', value: 'AVAILABLE' },
    { label: '已派单', value: 'ASSIGNED' },
    { label: '已提交', value: 'SUBMITTED' },
    { label: '驳回重发', value: 'REOPENED' },
    { label: '已完成', value: 'DONE' },
  ];
  const priorityOptions = [
    { label: '低', value: 'low' },
    { label: '普通', value: 'normal' },
    { label: '高', value: 'high' },
    { label: '紧急', value: 'urgent' },
  ];

  const tableColumns = [
    { title: '任务编号', dataIndex: 'taskId', key: 'taskId', width: 180 },
    { title: '业务', dataIndex: 'businessType', key: 'businessTypeName', width: 70 },
    { title: '工程编号', dataIndex: 'projectId', key: 'projectId', width: 130 },
    { title: '机房', dataIndex: 'roomId', key: 'roomId', width: 110 },
    { title: '任务内容', key: 'taskContentCell', width: 220 },
    { title: '派单人', key: 'assignerName', width: 80 },
    { title: '执行人', key: 'assigneeName', width: 90 },
    { title: '状态', key: 'statusName', width: 100 },
    { title: '轮次', dataIndex: 'roundCount', key: 'roundCount', width: 60 },
    { title: '截止时间', dataIndex: 'deadlineAt', key: 'deadlineAt', width: 120 },
    { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 120 },
    { title: '操作', key: 'action', fixed: 'right', width: 170 },
  ];

  const formTitle = computed(() => (isUpdate.value ? '编辑任务' : '派发新任务'));

  function isArchived(record: any) {
    return Number(record.archived) === 1;
  }

  function taskSummary(record: any) {
    const parts = [record.taskTitle, record.taskContent].filter((item) => item);
    return parts.join(' - ') || '无任务内容';
  }

  function canConfirm(record: any) {
    return record.status === 'SUBMITTED' && !isArchived(record);
  }

  function canReject(record: any) {
    return record.status === 'SUBMITTED' && !isArchived(record);
  }

  function canPush(record: any) {
    return !isArchived(record) && ['AVAILABLE', 'ASSIGNED', 'REOPENED'].includes(record.status);
  }

  function businessTypeLabel(value?: string) {
    const map: Record<string, string> = { inspection: '巡检', fault: '故障', engineering: '工程' };
    return map[value || ''] || value || '-';
  }

  function statusLabel(value?: string) {
    const map: Record<string, string> = {
      AVAILABLE: '待接单',
      ASSIGNED: '已派单',
      SUBMITTED: '已提交',
      REOPENED: '驳回重发',
      DONE: '已完成',
    };
    return map[value || ''] || value || '-';
  }

  function statusColor(value?: string) {
    const map: Record<string, string> = {
      AVAILABLE: 'orange',
      ASSIGNED: 'blue',
      SUBMITTED: 'cyan',
      REOPENED: 'red',
      DONE: 'green',
    };
    return map[value || ''] || 'default';
  }

  function actionLabel(value?: string) {
    const map: Record<string, string> = {
      CREATE: '创建任务',
      UPDATE: '更新任务',
      REASSIGN: '调整执行人',
      CLAIM: '执行人接单',
      SUBMIT: '提交记录',
      REJECT: '驳回重发',
      CONFIRM: '确认闭环',
      PROGRESS: '提交工程进度',
      ARCHIVE: '归档任务',
      UNARCHIVE: '恢复任务',
    };
    return map[value || ''] || value || '-';
  }

  function submissionTypeLabel(value?: string) {
    return value === 'PROGRESS' ? '进度提交' : '最终提交';
  }

  function reviewStatusLabel(value?: string) {
    const map: Record<string, string> = { PROGRESS: '进度记录', SUBMITTED: '待确认', REJECTED: '已驳回', ACCEPTED: '已通过' };
    return map[value || ''] || value || '-';
  }

  function reviewStatusColor(value?: string) {
    const map: Record<string, string> = { PROGRESS: 'blue', SUBMITTED: 'cyan', REJECTED: 'red', ACCEPTED: 'green' };
    return map[value || ''] || 'default';
  }

  function formatTime(value?: string) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
  }

  async function load() {
    loading.value = true;
    try {
      const params: Record<string, any> = { pageNo: pagination.current, pageSize: pagination.pageSize };
      if (viewMode.value === 'archived') {
        params.archived = 1;
      } else {
        params.archived = 0;
        if (viewMode.value === 'warning') params.warning = true;
        if (viewMode.value === 'mine') params.mine = true;
      }
      Object.entries(filters).forEach(([key, value]) => {
        if (value) params[key] = value;
      });
      if (props.businessType) {
        params.businessType = props.businessType;
      }
      const data: any = await listTasks(params);
      rows.value = data?.records || [];
      pagination.total = data?.total || 0;
    } catch (e: any) {
      message.error(e?.message || '任务列表加载失败');
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
        roomName: room.roomName || '',
        domainCode: room.domainCode || 'core_network',
        domainShortCode: room.domainShortCode || 'CORE',
        domainName: room.domainName || '核心网',
        regionCode: room.regionCode || 'TY',
        regionName: room.regionName || '太原',
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
      dingtalkUserOptions.value = (data?.records || []).map((user) => ({
        label: user.name ? `${user.name}${user.deptName ? `（${user.deptName}）` : ''}` : user.dingtalkUserid,
        value: user.dingtalkUserid || '',
        name: user.name || '',
        userid: user.dingtalkUserid || '',
      }));
    } catch (e: any) {
      message.warning(e?.message || '执行人列表加载失败');
      dingtalkUserOptions.value = [];
    }
  }

  async function loadEngineeringProjects() {
    try {
      const data: any = await defHttp.get({
        url: '/roomops/engineering/project/list',
        params: { pageNo: 1, pageSize: 200, archived: 0 },
      });
      engineeringProjectOptions.value = (data?.records || []).map((project) => ({
        label: `${project.projectId || ''} / ${project.projectName || ''}`,
        value: project.projectId,
        projectName: project.projectName || '',
        roomId: project.roomId || '',
        roomName: project.roomName || '',
        domainCode: project.domainCode || 'core_network',
        domainShortCode: project.domainShortCode || 'CORE',
        domainName: project.domainName || '核心网',
        regionCode: project.regionCode || 'TY',
        regionName: project.regionName || '太原',
      }));
    } catch {
      engineeringProjectOptions.value = [];
    }
  }

  function reloadFirstPage() {
    pagination.current = 1;
    load();
  }

  function resetFilters() {
    filters.businessType = props.businessType || '';
    filters.projectId = '';
    filters.domainCode = 'core_network';
    filters.status = '';
    filters.assigneeName = '';
    filters.roomId = '';
    reloadFirstPage();
  }

  function handleTableChange(page: any) {
    pagination.current = page.current;
    pagination.pageSize = page.pageSize;
    load();
  }

  function resetForm(record?: any) {
    Object.keys(formModel).forEach((key) => delete formModel[key]);
    formModel.businessType = record?.businessType || props.businessType || 'inspection';
    formModel.taskId = record?.taskId || '';
    formModel.projectId = record?.projectId || '';
    formModel.taskTitle = record?.taskTitle || '';
    formModel.taskContent = record?.taskContent || '';
    formModel.roomId = record?.roomId || '';
    formModel.roomIds = record?.roomId ? [record.roomId] : [];
    formModel.roomName = record?.roomName || '';
    formModel.candidateUserids = record?.candidateUserids ? String(record.candidateUserids).split(',').filter(Boolean) : [];
    formModel.assigneeUserid = record?.assigneeUserid || '';
    formModel.assigneeName = record?.assigneeName || '';
    formModel.domainCode = record?.domainCode || 'core_network';
    formModel.domainShortCode = record?.domainShortCode || 'CORE';
    formModel.domainName = record?.domainName || '核心网';
    formModel.regionCode = record?.regionCode || 'TY';
    formModel.regionName = record?.regionName || '太原';
    formModel.priority = record?.priority || 'normal';
    formModel.deadlineAt = record?.deadlineAt || '';
    if (record?.id) formModel.id = record.id;
    if (!formModel.assigneeUserid && formModel.assigneeName) {
      dingtalkUserOptions.value.unshift({ label: formModel.assigneeName, value: `name:${formModel.assigneeName}`, name: formModel.assigneeName, userid: '' });
      formModel.assigneeUserid = `name:${formModel.assigneeName}`;
    }
  }

  function openAdd() {
    isUpdate.value = false;
    resetForm();
    formOpen.value = true;
  }

  function openEdit(record: any) {
    isUpdate.value = true;
    resetForm(record);
    formOpen.value = true;
  }

  function fillProjectInfo(projectId: string) {
    const project = engineeringProjectOptions.value.find((item) => item.value === projectId);
    if (!project) return;
    formModel.roomId = project.roomId;
    formModel.roomIds = project.roomId ? [project.roomId] : [];
    formModel.roomName = project.roomName;
    formModel.domainCode = project.domainCode;
    formModel.domainShortCode = project.domainShortCode;
    formModel.domainName = project.domainName;
    formModel.regionCode = project.regionCode;
    formModel.regionName = project.regionName;
  }

  function fillAssigneeName(userid: string) {
    if (userid?.startsWith('name:')) {
      formModel.assigneeName = userid.substring(5);
      formModel.assigneeUserid = '';
      return;
    }
    const user = dingtalkUserOptions.value.find((item) => item.value === userid);
    formModel.assigneeName = user?.name || '';
    formModel.assigneeUserid = user?.userid || userid || '';
  }

  function fillReassignName(userid: string) {
    if (userid?.startsWith('name:')) {
      rejectReassignName.value = userid.substring(5);
      return;
    }
    const user = dingtalkUserOptions.value.find((item) => item.value === userid);
    rejectReassignName.value = user?.name || '';
  }

  async function submit() {
    submitLoading.value = true;
    try {
      if (isUpdate.value) {
        const payload = { ...formModel };
        delete payload.roomIds;
        payload.candidateUserids = Array.isArray(payload.candidateUserids)
          ? payload.candidateUserids.join(',')
          : payload.candidateUserids || '';
        if (payload.assigneeUserid?.startsWith('name:')) {
          payload.assigneeName = payload.assigneeUserid.substring(5);
          payload.assigneeUserid = '';
        }
        await updateTask(payload);
        message.success('任务已更新');
      } else {
        const roomIds = (formModel.roomIds || []).filter((v: string) => v);
        if (roomIds.length === 0) {
          message.warning('请至少选择一个机房');
          return;
        }
        const candidateUserids = (formModel.candidateUserids || []).filter((v: string) => v);
        const payload: Record<string, any> = {};
        Object.keys(formModel).forEach((key) => {
          if (key !== 'roomIds' && key !== 'candidateUserids') payload[key] = formModel[key];
        });
        payload.roomIds = roomIds;
        payload.candidateUserids = candidateUserids;
        const created: any = await createTask(payload);
        const count = Array.isArray(created) ? created.length : 1;
        message.success(`派单成功：已生成 ${count} 个任务`);
      }
      formOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '保存失败，请检查后台日志');
    } finally {
      submitLoading.value = false;
    }
  }

  async function remove(record: any) {
    Modal.confirm({
      title: '确定删除这条任务？',
      onOk: async () => {
        await deleteTask(record.id);
        message.success('删除成功');
        await load();
      },
    });
  }

  async function toggleArchive(record: any, archived: boolean) {
    try {
      await archiveTask({ taskId: record.taskId, archived });
      message.success(archived ? '任务已归档' : '任务已恢复');
      await load();
    } catch (e: any) {
      message.error(e?.message || (archived ? '归档失败' : '恢复失败'));
    }
  }

  function openRecordDetail(recordId: string) {
    recordDetailId.value = recordId;
    recordDetailOpen.value = true;
  }

  async function openDetail(record: any) {
    selectedTask.value = record;
    detailOpen.value = true;
    detailLoading.value = true;
    detailTask.value = null;
    detailSubmissions.value = [];
    try {
      const [data, submissions]: any[] = await Promise.all([
        queryTask({ id: record.id }),
        listTaskRecords(record.taskId),
      ]);
      detailTask.value = data;
      detailSubmissions.value = submissions || [];
    } catch (e: any) {
      message.error(e?.message || '任务详情加载失败');
      detailTask.value = record;
    } finally {
      detailLoading.value = false;
    }
  }

  function openConfirm(record: any) {
    selectedTask.value = record;
    confirmRemark.value = '';
    confirmOpen.value = true;
  }

  async function submitConfirm() {
    submitLoading.value = true;
    try {
      await confirmTask({ taskId: selectedTask.value.taskId, remark: confirmRemark.value });
      message.success('已确认闭环');
      confirmOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '确认失败');
    } finally {
      submitLoading.value = false;
    }
  }

  function openReject(record: any) {
    selectedTask.value = record;
    rejectRemark.value = '';
    rejectReassignUserid.value = '';
    rejectReassignName.value = '';
    rejectClearAssignee.value = false;
    rejectOpen.value = true;
  }

  async function submitReject() {
    submitLoading.value = true;
    try {
      await rejectTask({
        taskId: selectedTask.value.taskId,
        remark: rejectRemark.value,
        reassignUserid: rejectReassignUserid.value?.startsWith('name:') ? '' : rejectReassignUserid.value,
        reassignName: rejectReassignName.value,
        clearAssignee: rejectClearAssignee.value,
      });
      message.success('已驳回并重新下发');
      rejectOpen.value = false;
      await load();
    } catch (e: any) {
      message.error(e?.message || '驳回失败');
    } finally {
      submitLoading.value = false;
    }
  }

  async function pushOne(record: any) {
    try {
      await pushTask({ taskId: record.taskId });
      message.success('任务已重新同步到小程序前置服务');
    } catch (e: any) {
      message.error(e?.message || '下发失败');
    }
  }

  async function pushAll() {
    try {
      await pushTask({});
      message.success('全部活动任务已重新同步到小程序前置服务');
    } catch (e: any) {
      message.error(e?.message || '下发失败');
    }
  }

  onMounted(() => {
    loadMachineRooms();
    loadDingtalkUsers();
    loadEngineeringProjects();
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
    width: 150px;
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

  .roomops-form :deep(.ant-form-item) {
    margin-bottom: 18px;
  }

  .detail-desc {
    margin-bottom: 18px;
  }

  .content-text {
    white-space: pre-wrap;
    line-height: 1.6;
  }

  .round-title {
    margin-bottom: 10px;
    font-size: 15px;
    font-weight: 600;
  }

  .round-timeline {
    max-height: 46vh;
    overflow: auto;
    padding-top: 4px;
  }

  .round-line {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .round-action {
    font-weight: 600;
  }

  .round-operator,
  .round-time {
    color: #888;
  }

  .round-remark {
    margin-top: 4px;
    color: #555;
  }

  .modal-tip {
    margin-bottom: 12px;
    color: #555;
  }
</style>
