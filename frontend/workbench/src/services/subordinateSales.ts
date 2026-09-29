import type { SubordinateBatchResult, SubordinateSales } from './api'

export function subordinateTaskTypeLabel(value?: string) {
  // SubordinateSalesService exposes only these two LeadConstants follow-up task types.
  const labels: Record<string, string> = { lead_first_follow_up: '首次跟进', lead_follow_up_reminder: '跟进提醒' }
  return !value ? '—' : Object.hasOwn(labels, value) ? labels[value] : '未知类型'
}

export function receiveStatusLabel(sales: Pick<SubordinateSales, 'canReceiveNewLeads'>) {
  return sales.canReceiveNewLeads ? '可接收' : '不可接收'
}

export function todayStatusLabel(status: SubordinateSales['todayFollowUpStatus']) {
  return status === 'completed' ? '已完成' : '未完成'
}

export function summarizeBatchResult(result: SubordinateBatchResult) {
  return `成功 ${result.successCount} 条，失败 ${result.failureCount} 条`
}

export function appendSubordinateSalesRows(current: SubordinateSales[], incoming: SubordinateSales[]) {
  const rows = new Map(current.map(row => [row.userId, row]))
  incoming.forEach(row => rows.set(row.userId, row))
  return Array.from(rows.values())
}

export function formatCurrency(value: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value || 0)
}
