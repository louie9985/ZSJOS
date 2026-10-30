import type { Timestamp } from './types'
/** The editor uses Beijing wall-clock ISO values; backend reads may be epoch milliseconds. */
export function metricPickerDate(value: Timestamp | string): string {
  if (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$/.test(value)
  )
    return value
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23'
    })
      .formatToParts(date)
      .map((p) => [p.type, p.value])
  )
  const milliseconds = date.getUTCMilliseconds()
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}${milliseconds ? '.' + String(milliseconds).padStart(3, '0') : ''}`
}
