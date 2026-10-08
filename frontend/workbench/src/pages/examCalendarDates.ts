import type { ExamSchedule } from '../services/api'

const businessDate = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
})

export function examToday(now = new Date()): string {
  const parts = businessDate.formatToParts(now)
  const part = (type: string) => parts.find(value => value.type === type)!.value
  return `${part('year')}-${part('month')}-${part('day')}`
}

export function examHasEnded(row: Pick<ExamSchedule, 'scheduleType' | 'exactDate' | 'endDate'>, today = examToday()): boolean {
  const end = row.scheduleType === 'EXACT' ? row.exactDate : row.endDate
  return Boolean(end && end < today)
}

export function examDateError(start?: string, end = start, today = examToday()): string | undefined {
  if (!start || !end) return '请选择完整的考期日期'
  if (end < start) return '结束日期不能早于开始日期'
  if (end < today) return '单日日期或多日结束日期不能早于北京时间今天'
}

export function initialExamDate(selected: string, today = examToday()): string {
  return selected < today ? today : selected
}
