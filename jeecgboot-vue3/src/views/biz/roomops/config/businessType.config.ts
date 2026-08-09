export const businessTypeOptions = [
  { label: '巡检', value: 'inspection' },
  { label: '故障处理', value: 'fault' },
  { label: '工程施工', value: 'engineering' },
];

const businessTypeLabelMap = Object.fromEntries(businessTypeOptions.map((item) => [item.value, item.label]));

export function businessTypeLabel(value?: string) {
  return businessTypeLabelMap[value || ''] || value || '-';
}

export function businessTypeColumn(title = '业务类型') {
  return {
    title,
    dataIndex: 'businessType',
    key: 'businessTypeName',
    customRender: ({ text }) => businessTypeLabel(text),
  };
}

export function businessTypeField(defaultValue = 'inspection') {
  return {
    name: 'businessType',
    label: '业务类型',
    type: 'select',
    options: businessTypeOptions,
    defaultValue,
  };
}
