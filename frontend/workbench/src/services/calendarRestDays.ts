import { http, unwrap } from './api'
import type { Dayjs } from 'dayjs'

export type RestDay = { date: string; rest: boolean; version: number }
export const REST_DAY_MANAGE = 'zsjos:calendar-rest-day:manage'
export const REST_DAY_CONFLICT = 1900018061
const root = '/zsjos/calendar-rest-day'
export const calendarRestDays = {
  list: async (startDate: string, endDate: string, signal?: AbortSignal) =>
    unwrap<RestDay[]>(await http.get(root + '/list', { params: { startDate, endDate }, signal })),
  save: async (changes: RestDay[]) =>
    unwrap<RestDay[]>(await http.put(root + '/batch-update', { changes }))
}
export function restDayRange(month: Dayjs) {
  const start = month.startOf('month').startOf('week')
  // Ant Design renders six rows even when the month needs fewer.
  return { startDate: start.format('YYYY-MM-DD'), endDate: start.add(41, 'day').format('YYYY-MM-DD') }
}
export function stageRestDays(base: Record<string, RestDay>, pending: Record<string, RestDay>, dates: string[], rest: boolean) {
  const next = { ...pending }
  for (const date of dates) {
    const original = base[date] ?? { date, rest: false, version: 0 }
    if (original.rest === rest) delete next[date]
    else next[date] = { ...original, rest }
  }
  return next
}
