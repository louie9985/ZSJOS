import type { RestDay } from '../../../services/calendarRestDays'

export function homeRestDates(rows: RestDay[], year: number, month: number): number[] {
  const prefix = `${year}-${String(month).padStart(2, '0')}-`
  const last = new Date(year, month, 0).getDate()
  return [...new Set(rows.filter(row => row.rest && row.date.startsWith(prefix) && /^\d{4}-\d{2}-\d{2}$/.test(row.date))
    .map(row => Number(row.date.slice(8))).filter(day => day >= 1 && day <= last))].sort((a, b) => a - b)
}

export function mergeHomeRestDays(restDays: number[], paidRestDays: number[], days: number[], kind: 'rest' | 'paid') {
  const additions = days.filter(day => !restDays.includes(day) && !paidRestDays.includes(day))
  const merge = (current: number[]) => [...new Set([...current, ...additions])].sort((a, b) => a - b)
  return { restDays: kind === 'rest' ? merge(restDays) : restDays, paidRestDays: kind === 'paid' ? merge(paidRestDays) : paidRestDays }
}
