import type { SubordinateBatchResult, SubordinateSales } from './api'

export function receiveStatusLabel(sales: Pick<SubordinateSales, 'canReceiveNewLeads'>) {
  return sales.canReceiveNewLeads ? '可接收' : '不可接收'
}

export function todayStatusLabel(status: SubordinateSales['todayFollowUpStatus']) {
  return status === 'completed' ? '已完成' : '未完成'
}

export function summarizeBatchResult(result: SubordinateBatchResult) {
  return `成功 ${result.successCount} 条，失败 ${result.failureCount} 条`
}

export function formatCurrency(value: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value || 0)
}
