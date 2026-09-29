import request from '@/config/axios'

export interface MediaCalendarItem { id: number; accountNo: string; nickname?: string; studentName?: string; directorUserName?: string; operatorUserName?: string; currentStatusLabelSnapshot?: string; stageLabelSnapshot?: string; startDate: string; endDate: string }
export interface MediaCalendarResult { list: MediaCalendarItem[]; total: number; unscheduledCount: number }
export interface CalendarUser { id: number; nickname: string }
export interface MediaCalendarCandidates { directors: CalendarUser[]; operators: CalendarUser[] }
export interface PersonalCalendarEvent { ownerUserId?: number; ownerName?: string; id: number; title: string; description?: string; startTime: number; endTime: number; allDay: boolean; status: string }
export interface PersonalCalendarInput { title: string; description?: string; startTime: string; endTime: string; allDay: boolean }

export const getMediaCalendar = (params: Record<string, unknown>) => request.get<MediaCalendarResult>({ url: '/zsjos/media-account/calendar', params })
export const getMediaCalendarCandidates = () => request.get<MediaCalendarCandidates>({ url: '/zsjos/media-account/calendar/candidates' })
export const getPersonalCalendar = (params: { rangeStart: string; rangeEnd: string; readScope?: 'SELF' | 'ALL' | 'USER'; targetUserId?: number }) => request.get<PersonalCalendarEvent[]>({ url: '/zsjos/personal-calendar', params })
export const createPersonalCalendar = (data: PersonalCalendarInput) => request.post<number>({ url: '/zsjos/personal-calendar', data })
export const updatePersonalCalendar = (id: number, data: PersonalCalendarInput) => request.put({ url: `/zsjos/personal-calendar/${id}`, data })
export const deletePersonalCalendar = (id: number) => request.delete({ url: `/zsjos/personal-calendar/${id}` })
export interface CourseCalendarEvent { id: number; courseName: string; courseFormValue: string; courseFormLabelSnapshot: string; startTime: string; endTime: string; remark?: string; attachmentIds: number[] }
export interface CourseCalendarInput { courseName: string; courseFormValue: string; startTime: string; endTime: string; remark?: string; attachmentIds?: number[] }
export interface CalendarNotifyInput { calendarId: number; calendarType: 'EXAM' | 'COURSE'; scope: 'ALL' | 'SPECIFIED'; userIds?: number[]; resend?: boolean; calendarVersion?: number; eventType?: string; idempotencyKey?: string; previewToken?: string; title?: string; time?: string; remark?: string }
export interface CalendarNotifyResult { batchId: number; acceptedCount: number; skippedCount: number; status: string; resend: boolean; sourceEventKey: string }
export interface CalendarNotifyPreview { calendarVersion: number; title: string; time?: string; remark?: string; recipientCount: number; notifiedCount: number; newRecipientCount: number; previewToken: string; contentHash: string }
export interface CalendarNotifyUser { id: number; nickname: string; deptId?: number | null }
export const getCourseCalendar = (params: { rangeStart: string; rangeEnd: string }) => request.get<CourseCalendarEvent[]>({ url: '/zsjos/course-calendar/page', params })
export const createCourseCalendar = (data: CourseCalendarInput) => request.post<number>({ url: '/zsjos/course-calendar', data })
export const updateCourseCalendar = (id: number, data: CourseCalendarInput) => request.put({ url: `/zsjos/course-calendar/${id}`, data })
export const deleteCourseCalendar = (id: number) => request.delete({ url: `/zsjos/course-calendar/${id}` })
export const sendCalendarNotification = (data: CalendarNotifyInput) => request.post<CalendarNotifyResult>({ url: '/zsjos/calendar-notification/send', data })
export const previewCalendarNotification = (data: CalendarNotifyInput) => request.post<CalendarNotifyPreview>({ url: '/zsjos/calendar-notification/preview', data })
export const getCalendarNotifyUsers = (calendarType: 'EXAM' | 'COURSE', keyword?: string, pageNo = 1, pageSize = 20) => request.get<{ list: CalendarNotifyUser[]; total: number }>({ url: '/zsjos/calendar-notification/users', params: { calendarType, keyword, pageNo, pageSize } })
