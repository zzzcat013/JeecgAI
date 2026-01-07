import { defHttp } from '/@/utils/http/axios';

export interface UploadParams {
  directoryName: string;
  typeCode1: string;
  typeCode2: string;
  typeCode3: string;
  title?: string;
  fileYear?: number;
  remark?: string;
}

export function uploadDoc(file: File, params: UploadParams) {
  return defHttp.uploadFile(
    { url: '/ai5g/doc/upload', params: {} },
    {
      file,
      name: 'file',
      data: {
        directoryName: params.directoryName,
        typeCode1: params.typeCode1,
        typeCode2: params.typeCode2,
        typeCode3: params.typeCode3,
        ...(params.title ? { title: params.title } : {}),
        ...(params.fileYear != null ? { fileYear: params.fileYear } : {}),
        ...(params.remark ? { remark: params.remark } : {}),
      },
    },
    { isReturnResponse: true }
  );
}

export function pageDoc(params: { pageNo?: number; pageSize?: number; typeCode1?: string; typeCode2?: string; typeCode3?: string; title?: string; fileYear?: number }) {
  return defHttp.get<any>({ url: '/ai5g/doc/page', params });
}
