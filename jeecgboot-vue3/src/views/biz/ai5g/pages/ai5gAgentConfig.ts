export const AI5G_AGENT_APP_ID = '2083017548267618305';

export interface Ai5gDatabaseSourceNode {
  id: string;
  label: string;
  sourceLabel: string;
}

export const AI5G_AGENT_DB_SOURCE_NODES: Ai5gDatabaseSourceNode[] = [
  {
    id: '2082795096418246706',
    label: 'ToB数据库',
    sourceLabel: 'AI5G专网查询插件',
  },
  {
    id: '2082795096418246713',
    label: 'ToC数据库',
    sourceLabel: 'AI5G专网查询插件',
  },
];
