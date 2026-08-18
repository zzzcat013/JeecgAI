<template>
  <div class="roomops-home">
    <a-card :bordered="false" class="welcome-card">
      <div class="welcome-inner">
        <div class="welcome-title">机房运维工作台</div>
        <div class="welcome-desc">
          面向机房运维人员的一站式工作台：任务分派与接单、巡检 / 故障 / 工程记录、照片与定位留痕、
          月度计划、问题整改闭环和月报归档。
        </div>
        <div class="welcome-actions">
          <a-button type="primary" ghost @click="go('/roomops/task')">进入任务分派</a-button>
          <a-button ghost @click="go('/roomops/governance')">进入治理中心</a-button>
        </div>
      </div>
    </a-card>

    <a-row :gutter="16" class="stat-row">
      <a-col v-for="item in statCards" :key="item.label" :xs="12" :md="6">
        <a-card :bordered="false" class="stat-card" :style="{ background: item.bg }">
          <div class="stat-inner">
            <div class="stat-left">
              <CountTo :start-val="0" :end-val="item.value" :duration="600" class="stat-value" />
              <div class="stat-label">{{ item.label }}</div>
            </div>
            <Icon :icon="item.icon" :size="36" color="#ffffff" />
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="快捷入口" :bordered="false" class="block-card">
      <div class="nav-grid">
        <div v-for="item in quickNav" :key="item.path" class="nav-item" @click="go(item.path)">
          <Icon :icon="item.icon" :size="26" :color="item.color" />
          <span class="nav-title">{{ item.title }}</span>
        </div>
      </div>
    </a-card>

    <a-card title="功能操作介绍" :bordered="false" class="block-card">
      <a-collapse v-model:active-key="activeKeys">
        <a-collapse-panel v-for="section in guides" :key="section.key" :header="section.title">
          <a-steps :current="section.steps.length" direction="vertical" size="small">
            <a-step v-for="step in section.steps" :key="step.title" :title="step.title" :description="step.desc" />
          </a-steps>
        </a-collapse-panel>
      </a-collapse>
    </a-card>
  </div>
</template>

<script lang="ts" name="machine-room-home" setup>
  import { onMounted, reactive, ref } from 'vue';
  import { CountTo } from '/@/components/CountTo/index';
  import { Icon } from '/@/components/Icon';
  import { useGo } from '/@/hooks/web/usePage';
  import { defHttp } from '/@/utils/http/axios';

  const go = useGo();
  const activeKeys = ref<string[]>(['inspection']);

  const statCards = reactive([
    { label: '待接单任务', value: 0, icon: 'ant-design:inbox-outlined', bg: 'linear-gradient(135deg,#3b82f6,#1d4ed8)' },
    { label: '待确认提交', value: 0, icon: 'ant-design:audit-outlined', bg: 'linear-gradient(135deg,#f59e0b,#b45309)' },
    { label: '业务记录总数', value: 0, icon: 'ant-design:file-text-outlined', bg: 'linear-gradient(135deg,#10b981,#047857)' },
    { label: '未闭环问题', value: 0, icon: 'ant-design:tool-outlined', bg: 'linear-gradient(135deg,#ef4444,#b91c1c)' },
  ]);

  const quickNav = [
    { title: '任务分派', path: '/roomops/task', icon: 'ant-design:send-outlined', color: '#2563eb' },
    { title: '我的任务', path: '/roomops/task/mine', icon: 'ant-design:user-outlined', color: '#7c3aed' },
    { title: '任务预警', path: '/roomops/task/warning', icon: 'ant-design:warning-outlined', color: '#dc2626' },
    { title: '业务记录', path: '/roomops/record', icon: 'ant-design:file-text-outlined', color: '#059669' },
    { title: '照片明细', path: '/roomops/photo', icon: 'ant-design:picture-outlined', color: '#0891b2' },
    { title: '机房列表', path: '/roomops/machine-room', icon: 'ant-design:bank-outlined', color: '#d97706' },
    { title: '工程列表', path: '/roomops/engineering/list', icon: 'ant-design:tool-outlined', color: '#4f46e5' },
    { title: '巡检治理中心', path: '/roomops/governance', icon: 'ant-design:dashboard-outlined', color: '#16a34a' },
  ];

  const guides = [
    {
      key: 'inspection',
      title: '巡检任务全流程',
      steps: [
        { title: '创建与下发', desc: '在“任务分派”新建巡检任务，选择机房、执行人、截止时间；保存后自动同步到小程序前置服务，若同步失败可在列表中点击“重新同步到小程序”。' },
        { title: '小程序接单', desc: '运维人员在钉钉小程序的任务列表中接单；未指定执行人的任务显示“待接单”，接单后自动绑定执行人。' },
        { title: '现场执行', desc: '扫码绑定机房并校验二维码与机房关系，提交定位与现场照片，逐项检查环境、设备、温湿度等。' },
        { title: '提交与多次提交', desc: '提交巡检结果后任务进入“待确认”；支持多次提交，每次提交内容完整保留，可回看历史版本。' },
        { title: '审核闭环', desc: '管理端“确认完成”后任务闭环；驳回后任务重新下发，小程序重新提交新版本继续流转。' },
      ],
    },
    {
      key: 'fault',
      title: '故障处理流程',
      steps: [
        { title: '故障任务分派', desc: '选择故障类型、机房、紧急程度并指定处理人，创建后自动下发到小程序。' },
        { title: '现场核实', desc: '在故障现场扫码、定位、拍照，填写故障现象；环境或设备状态异常会自动生成整改问题。' },
        { title: '处理与提交', desc: '处理后提交结果，支持多次提交保留过程记录。' },
        { title: '确认闭环', desc: '管理端确认完成即闭环；驳回后重新下发继续处理。' },
      ],
    },
    {
      key: 'engineering',
      title: '工程施工流程',
      steps: [
        { title: '工程录入', desc: '录入工程项目基础信息与现场问题，一个任务可进行多次提交，过程版本全部保留。' },
        { title: '进度提交', desc: '施工过程中选择“进度提交”，任务状态保持继续处理，可多次提交进度。' },
        { title: '最终提交', desc: '施工完成后选择“最终提交”，任务进入“待确认”等待审核。' },
        { title: '审核确认 / 驳回', desc: '管理端确认完成闭环；驳回后小程序重新提交新版本，历史提交可随时回看。' },
      ],
    },
    {
      key: 'governance',
      title: '巡检治理中心',
      steps: [
        { title: '巡检模板', desc: '定义检查项（环境、设备、温湿度等）与业务类型，供月度计划选用。' },
        { title: '月度计划', desc: '选择月份、模板、巡检机房、执行人与截止日保存计划，一键“生成任务”并自动下发。' },
        { title: '问题整改闭环', desc: '巡检记录中的异常自动生成问题单；按“处理中 → 已整改 → 闭环”流转，可驳回重开。' },
        { title: '月度统计归档', desc: '按月查看任务完成率、逾期率、问题闭环率，并导出 CSV 月报归档。' },
      ],
    },
    {
      key: 'config',
      title: '数据配置与同步',
      steps: [
        { title: '机房列表', desc: '维护机房基础信息、二维码标识与经纬度坐标，服务端据此校验扫码与定位。' },
        { title: '钉钉用户', desc: '同步钉钉通讯录中的运维人员，并维护默认专业与地市。' },
        { title: '同步日志', desc: '查看 VPS 前置服务与 Jeecg 之间的任务、记录同步情况，排查同步失败问题。' },
      ],
    },
  ];

  async function loadStats() {
    try {
      const [available, submitted, records, openIssues, processingIssues]: any[] = await Promise.all([
        defHttp.get({ url: '/roomops/task/list', params: { status: 'AVAILABLE', pageNo: 1, pageSize: 1 } }),
        defHttp.get({ url: '/roomops/task/list', params: { status: 'SUBMITTED', pageNo: 1, pageSize: 1 } }),
        defHttp.get({ url: '/roomops/record/list', params: { pageNo: 1, pageSize: 1 } }),
        defHttp.get({ url: '/roomops/governance/issues', params: { status: 'OPEN' } }),
        defHttp.get({ url: '/roomops/governance/issues', params: { status: 'PROCESSING' } }),
      ]);
      statCards[0].value = available?.total || 0;
      statCards[1].value = submitted?.total || 0;
      statCards[2].value = records?.total || 0;
      statCards[3].value =
        (Array.isArray(openIssues) ? openIssues.length : 0) +
        (Array.isArray(processingIssues) ? processingIssues.length : 0);
    } catch (error) {
      console.warn('机房运维首页统计加载失败', error);
    }
  }

  onMounted(() => {
    loadStats();
  });
</script>

<style lang="less" scoped>
  .roomops-home {
    padding: 16px;
  }

  .welcome-card {
    border-radius: 8px;
    background: linear-gradient(120deg, #1e3a8a 0%, #1d4ed8 60%, #2563eb 100%);
    color: #fff;
  }

  .welcome-inner {
    padding: 8px 8px 16px;
  }

  .welcome-title {
    font-size: 24px;
    font-weight: 600;
    color: #fff;
  }

  .welcome-desc {
    margin-top: 8px;
    color: rgba(255, 255, 255, 0.85);
    line-height: 1.8;
  }

  .welcome-actions {
    margin-top: 16px;
    display: flex;
    gap: 12px;
  }

  .stat-row {
    margin-top: 16px;
  }

  .stat-card {
    border-radius: 8px;
    margin-bottom: 16px;
    color: #fff;
  }

  .stat-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .stat-value {
    font-size: 28px;
    font-weight: 600;
    color: #fff;
  }

  .stat-label {
    margin-top: 4px;
    color: rgba(255, 255, 255, 0.85);
  }

  .block-card {
    border-radius: 8px;
    margin-top: 16px;
  }

  .nav-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
  }

  .nav-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 16px 8px;
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
  }

  .nav-item:hover {
    border-color: #2563eb;
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.15);
    transform: translateY(-2px);
  }

  .nav-title {
    color: #333;
  }

  @media (max-width: 768px) {
    .nav-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }
</style>
