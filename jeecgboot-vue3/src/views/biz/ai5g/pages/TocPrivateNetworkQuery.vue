<template>
  <div class="toc-query">
    <div class="hero">
      <div><div class="eyebrow">5G PRIVATE NETWORK</div><h1>随行专网数据查询</h1><p>统一查询项目申请、DNN、网络资源、地址池、路由及网管反馈资料</p></div>
      <a-button type="primary" :loading="loading" @click="reload">刷新数据</a-button>
    </div>
    <a-row :gutter="16" class="stats">
      <a-col :xs="12" :lg="6" v-for="item in statCards" :key="item.label">
        <a-card :bordered="false"><a-statistic :title="item.label" :value="item.value" :suffix="item.suffix" /></a-card>
      </a-col>
    </a-row>
    <a-card :bordered="false" class="main-card">
      <div class="filters">
        <a-select v-model:value="filters.projectCode" allow-clear placeholder="全部项目" style="width:260px" :options="projectOptions" />
        <a-input-search v-model:value="filters.keyword" allow-clear placeholder="搜索项目、DNN、IP、网段或文档内容" style="max-width:440px" @search="search" />
        <a-button @click="reset">重置</a-button>
      </div>
      <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
        <a-tab-pane key="projects" tab="项目总览">
          <a-table row-key="projectCode" :columns="projectColumns" :data-source="projects" :loading="loading" :pagination="false" :scroll="{x:1300}">
            <template #bodyCell="{column,record}">
              <template v-if="column.key==='status'"><a-tag :color="record.projectStatus==='已开通'?'green':'blue'">{{record.projectStatus}}</a-tag></template>
              <template v-else-if="column.key==='action'"><a-button type="link" @click="openEdit(record)">编辑</a-button></template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="resources" tab="网络资源">
          <a-table row-key="id" :columns="resourceColumns" :data-source="page.records" :loading="loading" :pagination="pagination" :scroll="{x:1200}" @change="pageChange" />
        </a-tab-pane>
        <a-tab-pane key="routes" tab="地址池与路由">
          <a-table row-key="id" :columns="routeColumns" :data-source="page.records" :loading="loading" :pagination="pagination" :scroll="{x:1100}" @change="pageChange" />
        </a-tab-pane>
        <a-tab-pane key="documents" tab="文档检索">
          <a-table row-key="id" :columns="documentColumns" :data-source="page.records" :loading="loading" :pagination="pagination" @change="pageChange">
            <template #bodyCell="{column,record}"><template v-if="column.key==='content'"><div class="content-cell">{{record.content}}</div></template></template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>
    <a-modal v-model:open="editOpen" title="编辑随行专网项目" :confirm-loading="saving" @ok="saveProject">
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="项目名称"><a-input :value="editForm.projectName" disabled /></a-form-item>
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="项目状态"><a-select v-model:value="editForm.projectStatus" :options="statusOptions" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="要求开通日期"><a-input v-model:value="editForm.requestedOpenDate" placeholder="YYYY-MM-DD" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="带宽"><a-input v-model:value="editForm.bandwidth" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="预计用户数"><a-input-number v-model:value="editForm.expectedUserCount" :min="0" style="width:100%" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="DNN/APN"><a-input v-model:value="editForm.dnn" /></a-form-item>
        <a-form-item label="UPF"><a-input v-model:value="editForm.upfName" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="editForm.remark" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getTocDocuments, getTocProjects, getTocResources, getTocRoutes, getTocSummary, updateTocProject } from '../api/tocPrivateNetwork.api';

const loading=ref(false), activeTab=ref('projects'), projects=ref<any[]>([]), summary=ref<any>({});
const filters=reactive({projectCode:undefined as string|undefined,keyword:''});
const editOpen=ref(false),saving=ref(false),editForm=reactive<any>({});
const statusOptions=['待开通','开通中','已开通','已停用'].map(value=>({label:value,value}));
const page=reactive({records:[] as any[],total:0,current:1,size:20});
const projectOptions=computed(()=>projects.value.map(x=>({label:x.projectName,value:x.projectCode})));
const statCards=computed(()=>[
  {label:'随行专网项目',value:summary.value.projectCount||0,suffix:'个'}, {label:'已开通项目',value:summary.value.openedCount||0,suffix:'个'},
  {label:'预计终端用户',value:summary.value.expectedUserCount||0,suffix:'户'}, {label:'网络资源项',value:summary.value.resourceCount||0,suffix:'项'}]);
const pagination=computed(()=>({current:page.current,pageSize:page.size,total:page.total,showSizeChanger:true,showTotal:(n:number)=>`共 ${n} 条`}));
const projectColumns=[{title:'项目名称',dataIndex:'projectName',fixed:'left',width:220},{title:'客户',dataIndex:'customerName',width:180},{title:'DNN/APN',dataIndex:'dnn',width:190},{title:'ServiceID',dataIndex:'serviceId',width:180},{title:'带宽',dataIndex:'bandwidth',width:90},{title:'UPF',dataIndex:'upfName',width:130},{title:'预计用户',dataIndex:'expectedUserCount',width:100},{title:'资源项',dataIndex:'resourceCount',width:80},{title:'要求开通',dataIndex:'requestedOpenDate',width:120},{title:'状态',key:'status',width:90},{title:'操作',key:'action',fixed:'right',width:80}];
const resourceColumns=[{title:'项目',dataIndex:'projectName',fixed:'left',width:210},{title:'电路',dataIndex:'circuitNo',width:120},{title:'资源类型',dataIndex:'resourceType',width:150},{title:'资源名称',dataIndex:'resourceName',width:210},{title:'网络侧',dataIndex:'networkSide',width:100},{title:'主用值',dataIndex:'resourceValue',width:230},{title:'备用值',dataIndex:'backupValue',width:180},{title:'状态',dataIndex:'resourceStatus',width:90},{title:'备注',dataIndex:'remark',width:220}];
const routeColumns=[{title:'项目',dataIndex:'projectName',fixed:'left',width:210},{title:'UPF',dataIndex:'upfName',width:150},{title:'类型',dataIndex:'routeType',width:150},{title:'终端地址池',dataIndex:'addressPool',width:180},{title:'目的网段',dataIndex:'destinationCidr',width:180},{title:'下一跳',dataIndex:'nextHop',width:150},{title:'VPN/VRF',dataIndex:'vrf',width:180},{title:'状态',dataIndex:'routeStatus',width:90}];
const documentColumns=[{title:'项目',dataIndex:'projectName',width:200},{title:'类型',dataIndex:'docType',width:150},{title:'标题',dataIndex:'title',width:230},{title:'内容',key:'content',dataIndex:'content'},{title:'来源',dataIndex:'sourceFile',width:220}];

async function loadProjects(){projects.value=await getTocProjects({keyword:activeTab.value==='projects'?filters.keyword:undefined});}
async function loadPage(){if(activeTab.value==='projects')return loadProjects(); const params={...filters,pageNo:page.current,pageSize:page.size}; const fn=activeTab.value==='resources'?getTocResources:activeTab.value==='routes'?getTocRoutes:getTocDocuments; Object.assign(page,await fn(params));}
async function reload(){loading.value=true;try{summary.value=await getTocSummary();await loadProjects();await loadPage();}finally{loading.value=false;}}
async function search(){page.current=1;loading.value=true;try{await loadPage();}finally{loading.value=false;}}
function reset(){filters.projectCode=undefined;filters.keyword='';search();}
function onTabChange(){
  // 关键词只属于发起搜索时的页签，不能带到其他数据粒度的页签中。
  filters.keyword='';
  page.current=1;
  page.records=[];
  search();
}
async function pageChange(p:any){
  page.current=p.current;
  page.size=p.pageSize;
  loading.value=true;
  try{await loadPage();}finally{loading.value=false;}
}
function openEdit(record:any){Object.keys(editForm).forEach(key=>delete editForm[key]);Object.assign(editForm,record);editOpen.value=true;}
async function saveProject(){saving.value=true;try{await updateTocProject(editForm.projectCode,editForm);message.success('保存成功');editOpen.value=false;await reload();}finally{saving.value=false;}}
onMounted(reload);
</script>

<style scoped lang="less">
.toc-query{padding:20px;background:#f3f6fa;min-height:100%;}.hero{display:flex;align-items:center;justify-content:space-between;padding:28px 32px;border-radius:14px;color:#fff;background:linear-gradient(120deg,#102a56,#176b87 65%,#23a29a);box-shadow:0 10px 28px rgba(16,42,86,.18)}
.hero h1{margin:4px 0 6px;color:#fff;font-size:28px}.hero p{margin:0;color:rgba(255,255,255,.78)}.eyebrow{font-size:11px;letter-spacing:2.2px;color:#79e1d2}.stats{margin-top:16px}.stats :deep(.ant-card){border-radius:12px;box-shadow:0 4px 16px rgba(38,63,91,.06)}.main-card{margin-top:16px;border-radius:12px}.filters{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:4px}.content-cell{max-width:640px;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;white-space:normal;line-height:1.6}
@media(max-width:700px){.toc-query{padding:12px}.hero{padding:22px 18px}.hero h1{font-size:22px}.hero p{display:none}}
</style>
