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
  ],
};

export default ai5g;

