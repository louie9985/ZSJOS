export type Timestamp = number
export type TimestampValue = Timestamp | string | Date
export type DateTimePrecision = 'date' | 'minute' | 'second'

const DATE_TIME_FORMATTERS: Record<DateTimePrecision, Intl.DateTimeFormat> = {
  date: new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }),
  minute: new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }),
  second: new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  })
}

function toTimestamp(value?: TimestampValue | null) {
  if (value == null) return undefined
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined

  if (value instanceof Date) {
    const timestamp = value.getTime()
    return Number.isFinite(timestamp) ? timestamp : undefined
  }

  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? `${value}T00:00:00+08:00`
    : /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value)
      ? `${value.replace(' ', 'T')}+08:00`
      : value
  const timestamp = new Date(normalized).getTime()
  return Number.isFinite(timestamp) ? timestamp : undefined
}

export function formatTimestamp(
  value?: TimestampValue | null,
  emptyText = '-',
  precision: DateTimePrecision = 'minute'
) {
  const timestamp = toTimestamp(value)
  if (timestamp == null) return emptyText
  const parts = Object.fromEntries(
    DATE_TIME_FORMATTERS[precision].formatToParts(timestamp).map(part => [part.type, part.value])
  )
  if (precision === 'date') return `${parts.year}-${parts.month}-${parts.day}`
  const time = `${parts.hour}:${parts.minute}`
  return `${parts.year}-${parts.month}-${parts.day} ${precision === 'second' ? `${time}:${parts.second}` : time}`
}
