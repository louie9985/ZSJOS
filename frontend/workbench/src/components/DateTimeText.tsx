import { Tooltip } from 'antd'
import { formatTimestamp, type DateTimePrecision, type TimestampValue } from '../services/time'

export default function DateTimeText({
  value,
  emptyText = '-',
  precision = 'minute'
}: {
  value?: TimestampValue | null
  emptyText?: string
  precision?: DateTimePrecision
}) {
  const text = formatTimestamp(value, emptyText, precision)
  const fullText = formatTimestamp(value, emptyText, 'second')
  const content = <time className="date-time-text">{text}</time>

  return precision === 'minute' && fullText !== emptyText && fullText !== text
    ? <Tooltip title={fullText}>{content}</Tooltip>
    : content
}
