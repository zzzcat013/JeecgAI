import { defHttp } from '/@/utils/http/axios';

export interface DocTypeItem {
  id?: string;
  level: number;
  code: string;
  name: string;
  parentCode?: string;
  status?: number;
}

export function listTypes(params: { level?: number; parentCode?: string }) {
  return defHttp.get<DocTypeItem[]>({ url: '/ai5g/type/list', params });
}

export function saveType(data: DocTypeItem) {
  return defHttp.post<DocTypeItem>({ url: '/ai5g/type/save', params: {}, data });
}

export function updateType(data: Partial<DocTypeItem> & { id: string }) {
  return defHttp.put<DocTypeItem>({ url: '/ai5g/type/update', params: {}, data });
}

export function removeType(id: string) {
  return defHttp.delete<boolean>({ url: `/ai5g/type/remove/${id}` });
}

export function moveType(data: { id: string; targetParentCode: string }) {
  return defHttp.post<boolean>({ url: '/ai5g/type/move', params: {}, data });
}
