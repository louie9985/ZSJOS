import type { ExportTask } from './api'

// Fixed export commands owned by the async-export API, not business dictionaries.
const types: Record<ExportTask['exportType'], string> = {
  lead: '客资', order: '订单', finance_order: '财务订单', cashback: '返现', withdrawal: '提现',
}
const statuses: Record<ExportTask['status'], string> = {
  queued: '排队中', prechecking: '校验中', generating: '生成中', ready: '可下载',
  failed: '失败', cancelled: '已取消', expired: '已过期',
}
export function exportTypeLabel(value?: string) {
  return !value ? '—' : Object.hasOwn(types, value) ? types[value as ExportTask['exportType']] : '未知类型'
}
export function exportStatusLabel(value?: string) {
  return !value ? '—' : Object.hasOwn(statuses, value) ? statuses[value as ExportTask['status']] : '未知状态'
}
