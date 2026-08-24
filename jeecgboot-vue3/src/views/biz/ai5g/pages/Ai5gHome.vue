<template>
  <div class="ai5g-home">
    <section class="home-hero">
      <div class="hero-copy">
        <div class="hero-kicker">AI5G OPERATIONS</div>
        <h1>AI5G 运维首页</h1>
        <p>文档、知识库和项目数据查询的统一工作台</p>
      </div>
      <div class="hero-actions">
        <a-button type="primary" @click="go('/ai5g/doc-manage')">
          <template #icon><FileTextOutlined /></template>
          文档管理
        </a-button>
        <a-button ghost @click="go('/ai/app/chat/2083017548267618305')">
          <template #icon><RobotOutlined /></template>
          AI 对话
        </a-button>
      </div>
    </section>

    <section class="home-section">
      <div class="section-head">
        <h2>核心工作台</h2>
        <span>点击卡片进入对应模块</span>
      </div>
      <div class="entry-grid">
        <a-card v-for="entry in entries" :key="entry.path" class="entry-card" :bordered="false" @click="go(entry.path)">
          <div class="entry-icon" :style="{ background: entry.bg, color: entry.color }">
            <component :is="entry.icon" />
          </div>
          <div class="entry-title">{{ entry.title }}</div>
          <div class="entry-desc">{{ entry.desc }}</div>
          <div class="entry-footer">
            <a-tag :color="entry.tagColor">{{ entry.tag }}</a-tag>
            <ArrowRightOutlined class="entry-arrow" />
          </div>
        </a-card>
      </div>
    </section>

    <section class="home-section agent-section">
      <div class="section-head">
        <h2>运维 AI 智能体</h2>
        <span>围绕文档知识库和项目数据表的检索、问答与查询入口</span>
      </div>
      <a-card class="agent-card" :bordered="false">
        <div class="agent-head">
          <div class="agent-icon"><RobotOutlined /></div>
          <div>
            <h3>AI5G 运维助手</h3>
            <p>优先从文档知识库查找资料，再通过受限只读查询访问导入数据库的项目表。</p>
          </div>
        </div>

        <div class="agent-points">
          <div v-for="point in agentPoints" :key="point.title" class="agent-point">
            <CheckCircleOutlined class="point-icon" />
            <div>
              <strong>{{ point.title }}</strong>
              <span>{{ point.desc }}</span>
            </div>
          </div>
        </div>

        <div class="data-scope">
          <div class="data-scope-title">已接入查询范围</div>
          <div class="scope-grid">
            <div v-for="item in dataScopes" :key="item.table" class="scope-item">
              <code>{{ item.table }}</code>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>

        <a-space wrap>
          <a-button type="primary" @click="go('/ai/app/chat/2083017548267618305')">
            <template #icon><RobotOutlined /></template>
            进入 AI 对话
          </a-button>
          <a-button @click="go('/ai5g/toc-private-network')">项目数据查询</a-button>
          <a-button @click="go('/super/airag/aiknowledge/AiKnowledgeBaseList')">知识库管理</a-button>
        </a-space>
      </a-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import {
  ArrowRightOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  GlobalOutlined,
  RobotOutlined,
} from '@ant-design/icons-vue';

const router = useRouter();

const entries = [
  {
    title: '文档管理',
    desc: '上传、预览、AI 转 MD、编辑版本并导入知识库。',
    tag: '上传/转换/导入',
    tagColor: 'blue',
    path: '/ai5g/doc-manage',
    icon: FileTextOutlined,
    color: '#1677ff',
    bg: '#e6f4ff',
  },
  {
    title: '文档管理概览',
    desc: '查看文档状态、分类、源文件大小和转换进度。',
    tag: '状态/分类/容量',
    tagColor: 'cyan',
    path: '/ai5g/document-overview',
    icon: BarChartOutlined,
    color: '#08979c',
    bg: '#e6fffb',
  },
  {
    title: 'AI 知识库',
    desc: '管理知识库、向量化文档并查看知识库文档列表。',
    tag: '向量化/检索',
    tagColor: 'purple',
    path: '/super/airag/aiknowledge/AiKnowledgeBaseList',
    icon: DatabaseOutlined,
    color: '#722ed1',
    bg: '#f9f0ff',
  },
  {
    title: '随行专网查询',
    desc: '查询项目、网络资源、地址池路由及文档检索片段。',
    tag: '项目/资源/路由',
    tagColor: 'green',
    path: '/ai5g/toc-private-network',
    icon: GlobalOutlined,
    color: '#389e0d',
    bg: '#f6ffed',
  },
];

const agentPoints = [
  {
    title: '文档知识库问答',
    desc: '基于已转 Markdown 和知识库内容，回答部署、运维、配置等问题。',
  },
  {
    title: '项目数据表查询',
    desc: '通过受限只读查询工具，查询导入数据库的项目、资源和路由配置。',
  },
  {
    title: '组合检索',
    desc: '先检索知识库定位方案，再按项目表字段获取现场配置和网络资源。',
  },
];

const dataScopes = [
  { table: 'biz_5g_toc_project_overview_view', label: '项目汇总' },
  { table: 'biz_5g_toc_network_resource', label: '网络资源明细' },
  { table: 'biz_5g_toc_route_config', label: '地址池与路由' },
  { table: 'biz_5g_toc_doc_fragment', label: '文档检索片段' },
];

function go(path: string) {
  router.push(path);
}
</script>

<style scoped lang="less">
.ai5g-home {
  min-height: 100%;
  padding: 20px;
  background: #f3f6fa;
}

.home-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 34px;
  border-radius: 10px;
  color: #fff;
  background: #12294d;
  box-shadow: 0 10px 28px rgba(18, 41, 77, 0.18);
}

.hero-kicker {
  margin-bottom: 8px;
  color: #74e0d5;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

.home-hero h1 {
  margin: 0 0 8px;
  color: #fff;
  font-size: 30px;
  line-height: 1.3;
}

.home-hero p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.home-section {
  margin-top: 20px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h2 {
  margin: 0;
  color: #1f2937;
  font-size: 18px;
}

.section-head span {
  color: #6b7280;
  font-size: 13px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.entry-card {
  min-height: 178px;
  border: 1px solid #e8edf3;
  border-radius: 10px;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.entry-card:hover {
  box-shadow: 0 8px 24px rgba(38, 63, 91, 0.1);
  transform: translateY(-2px);
}

.entry-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  margin-bottom: 14px;
  border-radius: 8px;
  font-size: 21px;
}

.entry-title {
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.entry-desc {
  min-height: 42px;
  margin-top: 8px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.entry-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.entry-arrow {
  color: #9ca3af;
  font-size: 14px;
}

.agent-card {
  border: 1px solid #e8edf3;
  border-radius: 10px;
}

.agent-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.agent-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: 8px;
  color: #fff;
  background: #1677ff;
  font-size: 24px;
}

.agent-head h3 {
  margin: 0 0 6px;
  color: #111827;
  font-size: 18px;
}

.agent-head p {
  margin: 0;
  color: #6b7280;
}

.agent-points {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}

.agent-point {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
}

.point-icon {
  flex: none;
  margin-top: 3px;
  color: #16a34a;
}

.agent-point strong {
  display: block;
  color: #111827;
  font-size: 14px;
}

.agent-point span {
  display: block;
  margin-top: 4px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.data-scope {
  margin-top: 20px;
}

.data-scope-title {
  margin-bottom: 8px;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.scope-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 8px;
  background: #f0f5ff;
}

.scope-item code {
  color: #1d4ed8;
  font-size: 12px;
  word-break: break-all;
}

.scope-item span {
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .entry-grid,
  .agent-points,
  .scope-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .ai5g-home {
    padding: 12px;
  }

  .home-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px 18px;
  }

  .home-hero h1 {
    font-size: 24px;
  }

  .entry-grid,
  .agent-points,
  .scope-grid {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
