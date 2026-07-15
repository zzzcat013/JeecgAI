import { defHttp } from '/@/utils/http/axios';

const base = '/ai5g/toc-private-network';
export const getTocSummary = () => defHttp.get<Record<string, number>>({ url: `${base}/summary` });
export const getTocProjects = (params?: Record<string, any>) => defHttp.get<any[]>({ url: `${base}/projects`, params });
export const getTocResources = (params?: Record<string, any>) => defHttp.get<any>({ url: `${base}/resources`, params });
export const getTocRoutes = (params?: Record<string, any>) => defHttp.get<any>({ url: `${base}/routes`, params });
export const getTocDocuments = (params?: Record<string, any>) => defHttp.get<any>({ url: `${base}/documents`, params });
export const updateTocProject = (projectCode: string, data: Record<string, any>) =>
  defHttp.put<boolean>({ url: `${base}/projects/${projectCode}`, data });
