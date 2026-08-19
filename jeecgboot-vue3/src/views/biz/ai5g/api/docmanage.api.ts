import { defHttp } from '/@/utils/http/axios';

export function removeDoc(id: string) {
  return defHttp.delete<boolean>({ url: `/ai5g/doc/remove/${id}` });
}

export function updateDoc(data: { id: string; displayName?: string; remark?: string; fileYear?: number; processStatus?: string; latest?: boolean }) {
  return defHttp.put<any>({ url: '/ai5g/doc/update', params: {}, data });
}

export function previewUrl(id: string) {
  return `/ai5g/doc/preview/${id}`;
}
