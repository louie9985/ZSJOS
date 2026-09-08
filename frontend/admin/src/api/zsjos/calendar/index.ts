import request from '@/config/axios'

export interface MediaCalendarItem { id: number; accountNo: string; nickname?: string; studentName?: string; directorUserName?: string; operatorUserName?: string; currentStatusLabelSnapshot?: string; stageLabelSnapshot?: string; startDate: string; endDate: string }
export interface MediaCalendarResult { list: MediaCalendarItem[]; total: number; unscheduledCount: number }
export interface CalendarUser { id: number; nickname: string }
export interface MediaCalendarCandidates { directors: CalendarUser[]; operators: CalendarUser[] }
export interface PersonalCalendarEvent { id: number; title: string; description?: string; startTime: number; endTime: number; allDay: boolean; status: string }
export interface PersonalCalendarInput { title: string; description?: string; startTime: string; endTime: string; allDay: boolean }

export const getMediaCalendar = (params: Record<string, unknown>) => request.get<MediaCalendarResult>({ url: '/zsjos/media-account/calendar', params })
export const getMediaCalendarCandidates = () => request.get<MediaCalendarCandidates>({ url: '/zsjos/media-account/calendar/candidates' })
export const getPersonalCalendar = (params: { rangeStart: string; rangeEnd: string }) => request.get<PersonalCalendarEvent[]>({ url: '/zsjos/personal-calendar', params })
export const createPersonalCalendar = (data: PersonalCalendarInput) => request.post<number>({ url: '/zsjos/personal-calendar', data })
export const updatePersonalCalendar = (id: number, data: PersonalCalendarInput) => request.put({ url: `/zsjos/personal-calendar/${id}`, data })
export const deletePersonalCalendar = (id: number) => request.delete({ url: `/zsjos/personal-calendar/${id}` })
