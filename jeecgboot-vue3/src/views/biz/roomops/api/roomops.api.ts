import { defHttp } from '/@/utils/http/axios';

export type RoomopsBusinessType = 'inspection' | 'fault' | 'engineering';

export interface RoomopsRecordQuery {
  businessType?: RoomopsBusinessType;
  roomId?: string;
  date?: string;
}

export function listMachineRooms(params = {}) {
  return defHttp.get({ url: '/roomops/machineRoom/list', params });
}

export function listRecords(params: RoomopsRecordQuery = {}) {
  return defHttp.get({ url: '/roomops/record/list', params });
}

export function listPhotos(params: { recordId?: string } = {}) {
  return defHttp.get({ url: '/roomops/photo/list', params });
}

export function listDingtalkUsers(params = {}) {
  return defHttp.get({ url: '/roomops/dingtalkUser/list', params });
}

export function listSyncLogs(params = {}) {
  return defHttp.get({ url: '/roomops/syncLog/list', params });
}

export function listTasks(params = {}) {
  return defHttp.get({ url: '/roomops/task/list', params });
}

export function queryTask(params: { id?: string; taskId?: string } = {}) {
  return defHttp.get({ url: '/roomops/task/queryById', params });
}

export function createTask(data: Record<string, any>) {
  return defHttp.post({ url: '/roomops/task/add', data });
}

export function updateTask(data: Record<string, any>) {
  return defHttp.put({ url: '/roomops/task/edit', data });
}

export function confirmTask(data: { taskId: string; remark?: string }) {
  return defHttp.post({ url: '/roomops/task/confirm', data });
}

export function rejectTask(data: {
  taskId: string;
  remark?: string;
  reassignUserid?: string;
  reassignName?: string;
  clearAssignee?: boolean;
}) {
  return defHttp.post({ url: '/roomops/task/reject', data });
}

export function pushTask(data: { taskId?: string } = {}) {
  return defHttp.post({ url: '/roomops/task/push', data });
}

export function archiveTask(data: { taskId: string; archived: boolean }) {
  return defHttp.post({ url: '/roomops/task/archive', data });
}

export function deleteTask(id: string) {
  return defHttp.delete({ url: '/roomops/task/delete', data: { id } }, { joinParamsToUrl: true });
}

export function listEngineeringProjects(params = {}) {
  return defHttp.get({ url: '/roomops/engineering/project/list', params });
}

export function queryEngineeringProject(params: { id?: string; projectId?: string } = {}) {
  return defHttp.get({ url: '/roomops/engineering/project/queryById', params });
}

export function createEngineeringProject(data: Record<string, any>) {
  return defHttp.post({ url: '/roomops/engineering/project/add', data });
}

export function updateEngineeringProject(data: Record<string, any>) {
  return defHttp.put({ url: '/roomops/engineering/project/edit', data });
}

export function updateEngineeringStatus(data: { projectId: string; status: string }) {
  return defHttp.post({ url: '/roomops/engineering/project/status', data });
}

export function archiveEngineeringProject(data: { projectId: string; archived: boolean }) {
  return defHttp.post({ url: '/roomops/engineering/project/archive', data });
}

export function deleteEngineeringProject(id: string) {
  return defHttp.delete({ url: '/roomops/engineering/project/delete', data: { id } }, { joinParamsToUrl: true });
}

export function listEngineeringAttachments(params: { projectId: string }) {
  return defHttp.get({ url: '/roomops/engineering/attachment/list', params });
}

export function deleteEngineeringAttachment(id: string) {
  return defHttp.delete({ url: '/roomops/engineering/attachment/delete', data: { id } }, { joinParamsToUrl: true });
}

export function listEngineeringTasks(projectId: string) {
  return defHttp.get({ url: '/roomops/engineering/project/tasks', params: { projectId } });
}

export function saveRoomops(apiBase: string, data: Record<string, any>, isUpdate: boolean) {
  return isUpdate ? defHttp.put({ url: `${apiBase}/edit`, data }) : defHttp.post({ url: `${apiBase}/add`, data });
}

export function deleteRoomops(apiBase: string, id: string) {
  return defHttp.delete({ url: `${apiBase}/delete`, data: { id } }, { joinParamsToUrl: true });
}
