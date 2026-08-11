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
