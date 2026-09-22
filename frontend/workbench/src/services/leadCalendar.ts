import { http, unwrap, type ManagedLead, type LeadFollowUp } from './api'
import type { Timestamp } from './time'

export type CalendarDay = { date: string; count: number }
export type CalendarCard = { lead: ManagedLead; deadline: Timestamp; lastFollowUp?: LeadFollowUp; canReadFollowUp: boolean }
export type CalendarSort = 'deadline' | 'category'
export type CalendarDirection = 'asc' | 'desc'
const BASE = '/zsjos/lead-follow-up-calendar'
export const leadCalendarApi = {
  days: async (start: string, end: string, signal?: AbortSignal) =>
    unwrap<CalendarDay[]>(await http.get(`${BASE}/days`, { params: { start, end }, signal })),
  cards: async (params: { start: string; end: string; pageNo: number; pageSize: number; sort: CalendarSort; direction: CalendarDirection }, signal?: AbortSignal) =>
    unwrap<{ list: CalendarCard[]; total: number }>(await http.get(`${BASE}/cards`, { params, signal })),
}
