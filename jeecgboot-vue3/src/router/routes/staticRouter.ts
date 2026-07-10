import type { AppRouteRecordRaw } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

export const AI_ROUTE: AppRouteRecordRaw = {
  path: '',
  name: 'ai-parent',
  component: LAYOUT,
  meta: {
    title: 'ai',
  },
  children: [
    {
      path: '/ai',
      name: 'ai',
      component: () => import('/@/views/dashboard/ai/index.vue'),
      meta: {
        title: 'AI助手',
      },
    },
    {
      path: '/ai5g/document-viewer',
      name: 'ai5g-document-viewer',
      component: () => import('/@/views/ai5g/document-viewer/index.vue'),
      meta: {
        title: '文档查看',
      },
    },
  ],
};

export const staticRoutesList = [AI_ROUTE];
