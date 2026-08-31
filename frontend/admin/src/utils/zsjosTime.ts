import type { TableColumnCtx } from 'element-plus'
import type { Timestamp } from '@/api/zsjos/types'

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23'
})

export function formatZsjosTimestamp(value?: Timestamp, emptyText = ''): string {
  if (value == null || !Number.isFinite(value)) return emptyText
  const parts = Object.fromEntries(
    DATE_TIME_FORMATTER.formatToParts(value).map((part) => [part.type, part.value])
  )
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

export function zsjosDateFormatter(
  _row: any,
  _column: TableColumnCtx<any>,
  cellValue?: Timestamp
): string {
  return formatZsjosTimestamp(cellValue)
}
