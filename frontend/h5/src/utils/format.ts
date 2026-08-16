import dayjs from 'dayjs'

/** 格式化金额 */
export function formatAmount(amount: number | undefined | null): string {
  if (amount == null) return '0.00'
  return amount.toFixed(2)
}

/** 格式化日期 */
export function formatDate(dateStr: string | undefined | null, format = 'YYYY-MM-DD'): string {
  if (!dateStr) return '--'
  return dayjs(dateStr).format(format)
}

/** 格式化日期时间 */
export function formatDateTime(dateStr: string | undefined | null): string {
  return formatDate(dateStr, 'YYYY-MM-DD HH:mm')
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
