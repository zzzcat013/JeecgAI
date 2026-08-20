import type { AppRouteModule } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

const ai5g: AppRouteModule = {
  path: '/ai5g',
  name: 'Ai5g',
  component: LAYOUT,
  redirect: '/ai5g/document-viewer',
  meta: {
    orderNo: 11,
    icon: 'ion:document-text-outline',
    title: 'AI5G文档',
  },
  children: [
    {
      path: 'document-viewer',
      name: 'Ai5gDocumentViewer',
      component: () => import('/@/views/ai5g/document-viewer/index.vue'),
      meta: {
        title: '文档查看',
      },
    },
    {
      path: 'document-overview',
      name: 'Ai5gDocumentOverview',
      component: () => import('/@/views/biz/ai5g/pages/DocumentOverview.vue'),
      meta: {
        title: '文档管理概览',
        icon: 'ant-design:bar-chart-outlined',
      },
    },
    {
      path: 'doc-manage',
      name: 'Ai5gDocumentManage',
      component: () => import('/@/views/biz/ai5g/pages/DocumentManage.vue'),
      meta: {
        title: '文档管理',
        icon: 'ant-design:folder-open-outlined',
      },
    },
    {
      path: 'toc-private-network',
      name: 'TocPrivateNetworkQuery',
      component: () => import('/@/views/biz/ai5g/pages/TocPrivateNetworkQuery.vue'),
      meta: {
        title: '随行专网查询',
        icon: 'ant-design:global-outlined',
      },
    },
  ],
};

export default ai5g;
