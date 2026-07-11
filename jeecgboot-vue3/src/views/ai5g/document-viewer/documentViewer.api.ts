import { defHttp } from '/@/utils/http/axios';

export interface DocTypeItem {
  id: string;
  level: number;
  code: string;
  name: string;
  parentCode?: string;
  status?: number;
}

export interface DocFileItem {
  id: string;
  actualFileName?: string;
  originalName?: string;
  displayName?: string;
  version?: number;
  uploadTime?: string;
  fileType?: string;
  categoryPath?: string;
  fileYear?: number;
  remark?: string;
  latest?: boolean;
  processStatus?: string;
  contentType?: string;
  size?: number;
  storagePath?: string;
  mdConverted?: boolean;
  mdPath?: string;
  assetRoot?: string;
}

export interface DocPageResult {
  records: DocFileItem[];
  total: number;
  size: number;
  current: number;
}

export interface DocPageParams {
  pageNo: number;
  pageSize: number;
  typeCode1?: string;
  typeCode2?: string;
  typeCode3?: string;
  title?: string;
  fileYear?: number;
}

export const listDocTypes = () => defHttp.get<DocTypeItem[]>({ url: '/ai5g/type/list' });

export const pageDocs = (params: DocPageParams) => defHttp.get<DocPageResult>({ url: '/ai5g/doc/page', params });

export const getDoc = (id: string) => defHttp.get<DocFileItem>({ url: `/ai5g/doc/get/${id}` });

export const getMarkdownPreview = async (id: string) => {
  const resp: any = await defHttp.get(
    { url: `/ai5g/doc/preview-md/${id}`, responseType: 'blob', timeout: 30000 },
    { isReturnNativeResponse: true, isTransformResponse: false, errorMessageMode: 'none' },
  );
  const blob: Blob = resp?.data as Blob;
  return blob.text();
};
