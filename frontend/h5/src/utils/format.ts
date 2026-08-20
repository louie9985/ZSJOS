import dayjs from 'dayjs'

export type ApiDateValue = string | number[]

/** 格式化金额 */
export function formatAmount(amount: number | undefined | null): string {
  if (amount == null) return '0.00'
  return amount.toFixed(2)
}

/** 格式化日期 */
export function formatDate(dateValue: ApiDateValue | undefined | null, format = 'YYYY-MM-DD'): string {
  if (!dateValue) return '--'
  const normalized = Array.isArray(dateValue)
    ? `${dateValue[0]}-${String(dateValue[1]).padStart(2, '0')}-${String(dateValue[2]).padStart(2, '0')}T${String(dateValue[3] || 0).padStart(2, '0')}:${String(dateValue[4] || 0).padStart(2, '0')}:${String(dateValue[5] || 0).padStart(2, '0')}`
    : dateValue
  const parsed = dayjs(normalized)
  return parsed.isValid() ? parsed.format(format) : '--'
}

/** 格式化日期时间 */
export function formatDateTime(dateValue: ApiDateValue | undefined | null): string {
  return formatDate(dateValue, 'YYYY-MM-DD HH:mm')
}

/** 用户可见客资编号；内部 ID 不作为回退值。 */
export function formatLeadNo(value?: string | null): string {
  return value?.trim() || '客资编号暂未生成'
}

const LEAD_STATUS_LABELS: Record<string, string> = {
  submitted: '已提交',
  suspended: '已挂起',
  valid: '有效',
  invalid: '无效',
  won: '已成交',
  closed: '已关闭',
  converted: '已转化'
}

/** 客资状态只展示中文标签，未知协议值不直接暴露给用户。 */
export function formatLeadStatus(status?: string | null): string {
  return status ? LEAD_STATUS_LABELS[status] || '未知状态' : '未知状态'
}

/** 手机号脱敏 */
export function maskMobile(mobile: string | undefined | null): string {
  if (!mobile) return '--'
  if (mobile.length >= 7) {
    return mobile.slice(0, 3) + '****' + mobile.slice(-4)
  }
  return mobile
}

/** 银行卡号脱敏 */
export function maskCardNumber(cardNumber: string | undefined | null): string {
  if (!cardNumber) return '--'
  return '****' + cardNumber.slice(-4)
}
