export function validateSalesOrderSubmission(
  mobile: string | undefined,
  wechatId: string | undefined,
  totalAmount: number,
  voucherCount: number
) {
  if (!mobile?.trim() && !wechatId?.trim()) return '手机号和微信号至少填写一个'
  if (totalAmount > 0 && voucherCount === 0) return '已付款的非零订单必须上传缴费凭证'
  return undefined
}

export function salesOrderTaskKey(item: SalesOrderListItem) {
  return item.taskId || `order:${item.id}`
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
import type { SalesOrderListItem } from './api'
