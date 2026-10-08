import { http, unwrap, type PersonalCalendarEvent, type CourseCalendarEvent, type ExamSchedule, type MediaAccountCalendarItem } from './api'
import type { CalendarCard } from './leadCalendar'
import { formatTimestamp, type TimestampValue } from './time'
import { withExamClock } from './examReedit'
import dayjs from 'dayjs'

export type CalendarSearchQuery = { keyword: string; rangeStart?: string; rangeEnd?: string; sort: 'nearest' | 'asc' | 'desc'; pageNo: number; pageSize: number }
export type SearchPage<T> = { list: T[]; total: number }
export type PersonalSearchScope = { readScope: 'SELF' | 'ALL' | 'USER'; targetUserId?: number }
export type MediaSearchScope = { currentStatusValue?: string; stageValue?: string; directorUserId?: number; operatorUserId?: number }
export type SearchPresentation = { id: number; title: string; start: string; end: string; locationEnd?: string; summary?: string; label?: string }

const get = async <T>(path: string, params: CalendarSearchQuery & object, signal?: AbortSignal) =>
  unwrap<SearchPage<T>>(await http.get(path, { params, signal }))

export const calendarSearchApi = {
  personal: (query: CalendarSearchQuery, scope: PersonalSearchScope, signal?: AbortSignal) => get<PersonalCalendarEvent>('/zsjos/personal-calendar/search', { ...query, ...scope }, signal),
  course: (query: CalendarSearchQuery, signal?: AbortSignal) => get<CourseCalendarEvent>('/zsjos/course-calendar/search', query, signal),
  exam: (query: CalendarSearchQuery, recordStatus?: string, signal?: AbortSignal) => withExamClock(() => get<ExamSchedule>('/zsjos/exam-calendar/search', { ...query, ...{ recordStatus } }, signal)),
  media: (query: CalendarSearchQuery, scope: MediaSearchScope, signal?: AbortSignal) => get<MediaAccountCalendarItem>('/zsjos/media-account/calendar/search', { ...query, ...scope }, signal),
  lead: (query: CalendarSearchQuery, signal?: AbortSignal) => get<CalendarCard>('/zsjos/lead-follow-up-calendar/search', query, signal),
}

export const calendarDate = (value: TimestampValue) => formatTimestamp(value, '', 'date')
export const calendarWallTime = (value: TimestampValue) => dayjs(formatTimestamp(value, '', 'second'))
export function calendarIntervalEnd(start: TimestampValue, end: TimestampValue) {
  const first = formatTimestamp(start, '', 'second'), last = formatTimestamp(end, '', 'second')
  // Timed calendars have an exclusive end; midnight must locate on the preceding occupied day.
  return last > first && last.endsWith('00:00:00')
    ? calendarDate(Date.parse(last.replace(' ', 'T') + '+08:00') - 1) : calendarDate(end)
}
export function locationDate(start: string, end: string, today = calendarDate(Date.now())) {
  return start <= today && end >= today ? today : start
}
export const searchPresentation = {
  personal: (row: PersonalCalendarEvent): SearchPresentation => ({ id: row.id, title: row.title, start: calendarDate(row.startTime), end: calendarDate(row.endTime), locationEnd: calendarIntervalEnd(row.startTime, row.endTime), summary: row.description, label: row.ownerName }),
  course: (row: CourseCalendarEvent): SearchPresentation => ({ id: row.id, title: row.courseName, start: calendarDate(row.startTime), end: calendarDate(row.endTime), locationEnd: calendarIntervalEnd(row.startTime, row.endTime), summary: row.remark, label: row.courseFormLabelSnapshot }),
  exam: (row: ExamSchedule): SearchPresentation => ({ id: row.id, title: row.scheduleName || '未命名考期', start: row.exactDate || row.startDate || '', end: row.exactDate || row.endDate || '', summary: row.remark, label: row.scheduleType === 'MULTI_DAY' ? '多日考期' : '单日考期' }),
  media: (row: MediaAccountCalendarItem): SearchPresentation => ({ id: row.id, title: [row.accountNo, row.nickname].filter(Boolean).join(' · '), start: row.startDate, end: row.endDate, label: row.currentStatusLabelSnapshot }),
  lead: (row: CalendarCard): SearchPresentation => ({ id: row.lead.id, title: [row.lead.leadNo, row.lead.submittedName].filter(Boolean).join(' · ') || '未填写姓名', start: calendarDate(row.deadline), end: calendarDate(row.deadline), summary: [row.lead.submittedMobile, row.lead.submittedWechatId].filter(Boolean).join(' · '), label: '未完成跟进' }),
}

export class CalendarResultExpired extends Error {
  constructor() { super('该搜索结果已失效或不再符合当前条件，请重新搜索'); this.name = 'CalendarResultExpired' }
}

/** Only traverse the target date window, stopping as soon as the requested record is found. */
export async function findCalendarPage<T>(fetchPage: (page: number) => Promise<SearchPage<T>>, matches: (row: T) => boolean, signal?: AbortSignal) {
  const accumulated: T[] = []
  for (let pageNo = 1; ; pageNo++) {
    signal?.throwIfAborted()
    const page = await fetchPage(pageNo)
    signal?.throwIfAborted()
    accumulated.push(...page.list)
    if (page.list.some(matches)) return { ...page, pageNo, accumulated }
    if (!page.list.length || accumulated.length >= page.total) throw new CalendarResultExpired()
  }
}

export function matchingExcerpt(text: string | undefined, keyword: string) {
  if (!text) return ''
  const at = text.toLocaleLowerCase().indexOf(keyword.toLocaleLowerCase())
  const start = Math.max(0, at - 45)
  return `${start ? '…' : ''}${text.slice(start, start + 180)}${text.length > start + 180 ? '…' : ''}`
}
