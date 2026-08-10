export type Timestamp = number

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

export function formatTimestamp(value?: Timestamp, emptyText = '-') {
  if (value == null || !Number.isFinite(value)) return emptyText
  const parts = Object.fromEntries(
    DATE_TIME_FORMATTER.formatToParts(value).map(part => [part.type, part.value])
  )
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}
