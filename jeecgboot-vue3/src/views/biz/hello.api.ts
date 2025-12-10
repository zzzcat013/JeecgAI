import { defHttp } from '/@/utils/http/axios';

export function helloTest() {
  return defHttp.get<string>({ url: '/hello/test' });
}

export function helloOk() {
  return defHttp.get<string>({ url: '/hello/ok' });
}

