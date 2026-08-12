import type { SalesOrder, SalesOrderListItem } from './api'

export function validateSalesOrderSubmission(
  mobile: string | undefined,
  wechatId: string | undefined,
  totalAmount: number,
  voucherCount: number
) {
  if (!mobile?.trim() && !wechatId?.trim()) return '请填写手机号或微信号'
  if (totalAmount > 0 && voucherCount === 0) return '已付款的非零订单必须上传缴费凭证'
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
