import type { DictData, SalesOrder, SalesOrderListItem } from './api'

export type DictionaryLoadState = 'loading' | 'ready' | 'error'

export function buildDictionaryLabelMap(items: DictData[]) {
  return new Map(items.map(item => [item.value, item.label]))
}

export function resolveDictionaryLabel(value: string | undefined, labels: Map<string, string>, state: DictionaryLoadState) {
  if (!value) return '-'
  if (state === 'loading') return '标签加载中'
  if (state === 'error') return '标签加载失败'
  return labels.get(value) || '标签未配置'
}

export function validateSalesOrderSubmission(
  mobile: string | undefined,
  wechatId: string | undefined,
  totalAmount: number,
  voucherCount: number
) {
  if (!mobile?.trim() && !wechatId?.trim()) return '请填写手机号或微信号'
  if (voucherCount === 0) return '所有订单必须上传至少一份缴费凭证'
  if (voucherCount > 6) return '缴费凭证最多上传 6 份'
  return undefined
}

export function salesOrderTaskKey(item: SalesOrderListItem) {
  return item.taskId || `order:${item.id}`
}

export function canReviewSalesOrderTask(
  order: Pick<SalesOrder, 'status' | 'registrationApproval' | 'financeApproval'>,
  task: Pick<SalesOrderListItem, 'taskId' | 'taskDefinitionKey'>
) {
  if (order.status !== 'pending_approval' || !task.taskId || !task.taskDefinitionKey) return false
  const approval = task.taskDefinitionKey === 'registrationReview'
    ? order.registrationApproval
    : task.taskDefinitionKey === 'financeReview' ? order.financeApproval : undefined
  return approval?.status === 'pending'
}

export function mergeSalesOrderListItems(
  current: SalesOrderListItem[],
  next: SalesOrderListItem[],
  keyOf: (item: SalesOrderListItem) => string | number = item => item.id
) {
  const values = new Map(current.map(item => [keyOf(item), item]))
  next.forEach(item => values.set(keyOf(item), item))
  return [...values.values()]
}

export function salesOrderDetailToListItem(order: SalesOrder): SalesOrderListItem {
  return {
    id: order.id,
    orderNo: order.orderNo,
    leadId: order.leadId,
    personId: order.personId,
    orderType: order.orderType,
    status: order.status,
    studentName: order.studentName,
    studentMobile: order.studentMobile,
    totalAmount: order.totalAmount,
    approvalRoundNo: order.approvalRoundNo,
    submittedAt: order.submittedAt,
    effectiveAt: order.effectiveAt
  }
}
