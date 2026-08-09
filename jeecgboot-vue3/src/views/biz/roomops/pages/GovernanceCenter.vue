<template>
  <div class="governance-page">
    <a-page-header title="巡检治理中心" sub-title="模板、月度计划、整改闭环和月报" />
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="report" tab="月度统计">
        <a-space class="toolbar">
          <a-month-picker v-model:value="month" value-format="YYYY-MM" />
          <a-button type="primary" @click="loadSummary">查询</a-button>
          <a-button @click="exportMonthly">导出归档</a-button>
        </a-space>
        <a-row :gutter="16">
          <a-col v-for="item in summaryCards" :key="item.label" :span="6">
            <a-card><a-statistic :title="item.label" :value="item.value" :suffix="item.suffix" /></a-card>
          </a-col>
        </a-row>
      </a-tab-pane>

      <a-tab-pane key="template" tab="巡检模板">
        <a-form layout="inline" :model="templateForm" class="toolbar">
          <a-form-item label="模板名称"><a-input v-model:value="templateForm.templateName" /></a-form-item>
          <a-form-item label="业务类型"><a-select v-model:value="templateForm.businessType" :options="businessOptions" style="width: 130px" /></a-form-item>
          <a-form-item><a-button type="primary" @click="saveTemplate">新增模板</a-button></a-form-item>
        </a-form>
        <a-textarea v-model:value="templateForm.checkItemsJson" :rows="5" class="json-editor" />
        <a-table :columns="templateColumns" :data-source="templates" row-key="id" :pagination="false" />
      </a-tab-pane>

      <a-tab-pane key="plan" tab="月度计划">
        <a-form layout="vertical" :model="planForm">
          <a-row :gutter="16">
            <a-col :span="6"><a-form-item label="计划名称"><a-input v-model:value="planForm.planName" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="月份"><a-month-picker v-model:value="planForm.planMonth" value-format="YYYY-MM" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="模板"><a-select v-model:value="planForm.templateId" :options="templateOptions" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="执行人"><a-select v-model:value="planForm.assigneeUserid" show-search :options="userOptions" @change="handleAssigneeChange" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="截止日"><a-input-number v-model:value="planForm.deadlineDay" :min="1" :max="31" /></a-form-item></a-col>
            <a-col :span="20"><a-form-item label="巡检机房"><a-select v-model:value="selectedRoomIds" mode="multiple" show-search :options="roomOptions" /></a-form-item></a-col>
            <a-col :span="4"><a-button type="primary" @click="savePlan">保存计划</a-button></a-col>
          </a-row>
        </a-form>
        <a-table :columns="planColumns" :data-source="plans" row-key="id" :pagination="false">
          <template #bodyCell="{ column, record }"><a-button v-if="column.key === 'action'" type="link" @click="generatePlan(record.id)">生成任务</a-button></template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="issue" tab="问题整改">
        <a-space class="toolbar"><a-select v-model:value="issueStatus" allow-clear placeholder="状态" :options="issueOptions" style="width: 150px" /><a-button @click="loadIssues">查询</a-button></a-space>
        <a-table :columns="issueColumns" :data-source="issues" row-key="id" :pagination="false">
          <template #bodyCell="{ column, record }">
            <a-space v-if="column.key === 'action'"><a-button size="small" @click="updateIssue(record, 'PROCESSING')">处理中</a-button><a-button size="small" @click="updateIssue(record, 'RESOLVED')">已整改</a-button><a-button size="small" type="primary" @click="updateIssue(record, 'CLOSED')">闭环</a-button></a-space>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import dayjs from 'dayjs';
  import { defHttp } from '/@/utils/http/axios';

  const activeTab = ref('report');
  const month = ref(dayjs().format('YYYY-MM'));
  const summary = reactive<Record<string, any>>({});
  const templates = ref<any[]>([]);
  const plans = ref<any[]>([]);
  const issues = ref<any[]>([]);
  const rooms = ref<any[]>([]);
  const users = ref<any[]>([]);
  const selectedRoomIds = ref<string[]>([]);
  const issueStatus = ref<string>();
  const businessOptions = [{ label: '巡检', value: 'inspection' }, { label: '故障', value: 'fault' }, { label: '工程', value: 'engineering' }];
  const issueOptions = ['OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED', 'REOPENED'].map((value) => ({ label: value, value }));
  const templateForm = reactive({ templateName: '', businessType: 'inspection', checkItemsJson: '[{"code":"environment","name":"环境状态","required":true},{"code":"device","name":"设备状态","required":true}]' });
  const planForm = reactive({ planName: '', planMonth: dayjs().format('YYYY-MM'), templateId: '', roomIdsJson: '[]', assigneeUserid: '', assigneeName: '', deadlineDay: 28 });
  const summaryCards = computed(() => [
    { label: '任务总数', value: summary.totalTasks || 0 }, { label: '完成率', value: summary.completionRate || 0, suffix: '%' },
    { label: '逾期率', value: summary.overdueRate || 0, suffix: '%' }, { label: '问题闭环率', value: summary.issueCloseRate || 0, suffix: '%' },
  ]);
  const templateOptions = computed(() => templates.value.map((item) => ({ label: item.template_name, value: item.id })));
  const roomOptions = computed(() => rooms.value.map((item) => ({ label: `${item.roomName}（${item.roomId}）`, value: item.roomId })));
  const userOptions = computed(() => users.value.map((item) => ({ label: item.name, value: item.dingtalkUserid })));
  const templateColumns = [{ title: '编码', dataIndex: 'template_code' }, { title: '名称', dataIndex: 'template_name' }, { title: '业务类型', dataIndex: 'business_type' }, { title: '状态', dataIndex: 'status' }];
  const planColumns = [{ title: '计划', dataIndex: 'plan_name' }, { title: '月份', dataIndex: 'plan_month' }, { title: '执行人', dataIndex: 'assignee_name' }, { title: '状态', dataIndex: 'status' }, { title: '任务数', dataIndex: 'generated_count' }, { title: '操作', key: 'action' }];
  const issueColumns = [{ title: '问题编号', dataIndex: 'issue_id' }, { title: '机房', dataIndex: 'room_name' }, { title: '问题描述', dataIndex: 'description' }, { title: '状态', dataIndex: 'status' }, { title: '整改结果', dataIndex: 'rectification_result' }, { title: '操作', key: 'action', width: 240 }];

  async function loadSummary() { Object.assign(summary, await defHttp.get({ url: '/roomops/governance/monthly', params: { month: month.value } })); }
  async function loadTemplates() { templates.value = await defHttp.get({ url: '/roomops/governance/templates' }); }
  async function loadPlans() { plans.value = await defHttp.get({ url: '/roomops/governance/plans', params: { month: planForm.planMonth } }); }
  async function loadIssues() { issues.value = await defHttp.get({ url: '/roomops/governance/issues', params: { status: issueStatus.value } }); }
  async function loadSelectors() {
    const [roomPage, userPage]: any[] = await Promise.all([
      defHttp.get({ url: '/roomops/machineRoom/list', params: { pageNo: 1, pageSize: 1000, status: '1' } }),
      defHttp.get({ url: '/roomops/dingtalkUser/list', params: { pageNo: 1, pageSize: 1000, active: '1' } }),
    ]);
    rooms.value = roomPage?.records || [];
    users.value = userPage?.records || [];
  }
  async function saveTemplate() { await defHttp.post({ url: '/roomops/governance/template/save', data: templateForm }); message.success('模板已保存'); await loadTemplates(); }
  async function savePlan() { await defHttp.post({ url: '/roomops/governance/plan/save', data: { ...planForm, roomIdsJson: JSON.stringify(selectedRoomIds.value) } }); message.success('计划已保存'); await loadPlans(); }
  function handleAssigneeChange(userid: string) { planForm.assigneeName = users.value.find((item) => item.dingtalkUserid === userid)?.name || ''; }
  async function generatePlan(id: string) { await defHttp.post({ url: '/roomops/governance/plan/generate', data: { id } }); message.success('任务已生成并下发'); await loadPlans(); }
  async function updateIssue(record: any, status: string) { await defHttp.post({ url: '/roomops/governance/issue/update', data: { issueId: record.issue_id, status } }); message.success('问题状态已更新'); await loadIssues(); }
  async function exportMonthly() {
    const response: any = await defHttp.get(
      { url: '/roomops/governance/monthly/export', params: { month: month.value }, responseType: 'blob' },
      { isReturnNativeResponse: true, isTransformResponse: false }
    );
    const blob = response.data;
    const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `机房巡检月报-${month.value}.csv`; link.click(); URL.revokeObjectURL(link.href);
  }
  onMounted(async () => { await Promise.all([loadSummary(), loadTemplates(), loadPlans(), loadIssues(), loadSelectors()]); });
</script>

<style scoped>.governance-page{padding:16px}.toolbar{margin-bottom:16px}.json-editor{margin-bottom:16px}</style>
