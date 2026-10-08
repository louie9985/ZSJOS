// UTF-8. Real pages, synthetic transport only; no business data or service writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PersonalCalendarPage from '../src/pages/PersonalCalendarPage'
import CourseCalendarPage from '../src/pages/CourseCalendarPage'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import MediaCalendarPage from '../src/pages/MediaCalendarPage'
import LeadFollowUpCalendarPage from '../src/pages/LeadFollowUpCalendarPage'
import { api, http, ApiError, type PersonalCalendarEvent, type CourseCalendarEvent, type ExamSchedule, type MediaAccountCalendarItem } from '../src/services/api'
import { calendarSearchApi, type CalendarSearchQuery } from '../src/services/calendarSearch'
import { leadCalendarApi, type CalendarCard } from '../src/services/leadCalendar'
import { examCalendarNote } from '../src/services/examCalendarNote'

const kind = new URLSearchParams(location.search).get('kind') || 'personal'
const calls: { query: CalendarSearchQuery; scope?: unknown }[] = []
const pageCalls: number[] = []
let behavior = 'normal'
let removed = false
const date = '2027-03-21'
const start = Date.parse(date + 'T09:00:00+08:00'), end = start + 3600000
const personal = Array.from({ length: 21 }, (_, i) => ({ id: i + 1, title: `命中日程${i + 1}`, description: '说明关键词%_中文', startTime: start, endTime: end, allDay: false, ownerUserId: 7, ownerName: '测试人员' })) as PersonalCalendarEvent[]
const courses = personal.map(row => ({ id: row.id, courseName: `命中课程${row.id}`, courseFormValue: 'live', courseFormLabelSnapshot: '直播', startTime: date + 'T09:00:00', endTime: date + 'T10:00:00', remark: row.description, attachmentIds: [] })) as CourseCalendarEvent[]
const exams = personal.map(row => ({ id: row.id, scheduleName: `命中考期${row.id}`, scheduleType: row.id === 21 ? 'MULTI_DAY' : 'EXACT', exactDate: row.id === 21 ? undefined : date, startDate: row.id === 21 ? '2027-03-20' : undefined, endDate: row.id === 21 ? '2027-03-22' : undefined, recordStatus: 'PUBLISHED', displayStatus: 'UPCOMING', remark: row.description, categoryPathSnapshot: [] })) as ExamSchedule[]
const media = Array.from({ length: 201 }, (_, i) => ({ id: i + 1, accountNo: `ACC-${i + 1}`, nickname: `命中账号${i + 1}`, startDate: date, endDate: date, currentStatusValue: 'active', currentStatusLabelSnapshot: '启用' })) as MediaAccountCalendarItem[]
const leads = Array.from({ length: 25 }, (_, i) => ({ lead: { id: i + 1, leadNo: `LEAD-${i + 1}`, submittedName: `命中姓名${i + 1}`, submittedMobile: '', submittedWechatId: '', availableActions: [], leadCategoryLabelSnapshot: '测试分类', salesStageLabelSnapshot: '待跟进' }, deadline: start, canReadFollowUp: false })) as CalendarCard[]
const paged = <T,>(rows: T[], q: { pageNo?: number; pageSize?: number }) => ({ list: rows.slice(((q.pageNo || 1) - 1) * (q.pageSize || 20), (q.pageNo || 1) * (q.pageSize || 20)), total: rows.length })
const inRange = (day: string, q: { rangeStart?: string; rangeEnd?: string }) => (!q.rangeStart || day >= q.rangeStart.slice(0, 10)) && (!q.rangeEnd || day <= q.rangeEnd.slice(0, 10))

async function search<T>(rows: T[], query: CalendarSearchQuery, scope?: unknown) {
  calls.push({ query, scope })
  if (query.keyword === '慢请求') await new Promise(resolve => setTimeout(resolve, 700))
  if (behavior === 'denied') throw new ApiError(403, '禁止访问')
  if (behavior === 'error') throw new Error('验证用查询失败')
  if (query.keyword === '空结果') return { list: [], total: 0 }
  const visible = removed ? rows.slice(0, -1) : rows
  return paged(visible, query)
}
http.defaults.adapter = async () => { throw new Error('禁止真实业务请求') }
api.dictDataByType = async () => []
api.simpleUsers = async () => [{ id: 7, nickname: '测试人员' }]
api.mediaAccount.calendarCandidates = async () => ({ directors: [{ id: 7, nickname: '测试编导' }], operators: [] })
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
calendarSearchApi.personal = (q, scope) => search(personal, q, scope)
calendarSearchApi.course = q => search(courses, q)
calendarSearchApi.exam = (q, recordStatus) => search(exams, q, { recordStatus })
calendarSearchApi.media = (q, scope) => search([...media.slice(0, 20), media[200]], q, scope)
calendarSearchApi.lead = q => search([...leads.slice(0, 20), leads[24]], q)
api.personalCalendar.list = async q => inRange(date, q) ? (removed ? personal.slice(0, -1) : personal) : []
api.courseCalendar.page = async q => inRange(date, q) ? (removed ? courses.slice(0, -1) : courses) : []
api.examCalendar.exactPage = async q => paged(inRange(date, q) ? exams.filter(r => r.scheduleType === 'EXACT') : [], q)
api.examCalendar.multiDayPage = async q => paged(!removed && inRange('2027-03-20', q) ? exams.filter(r => r.scheduleType === 'MULTI_DAY') : [], q)
api.mediaAccount.calendar = async q => { pageCalls.push(q.pageNo); return { ...paged(inRange(date, q) ? (removed ? media.slice(0, -1) : media) : [], q), unscheduledCount: 0 } }
leadCalendarApi.days = async (s, e) => date >= s && date < e ? [{ date, count: 25 }] : []
leadCalendarApi.cards = async q => { pageCalls.push(q.pageNo); return paged(q.start === date ? (removed ? leads.slice(0, -1) : leads) : [], q) }
Object.assign(window, { calendarSearchFixture: { calls, pageCalls, setBehavior: (value: string) => { behavior = value }, remove: () => { removed = true } } })
const permissions = ['zsjos:personal-calendar:query', 'zsjos:course-calendar:query', 'zsjos:exam-calendar:query', 'zsjos:lead-follow-up-calendar:query', 'zsjos:lead:query']
const page = kind === 'course' ? <CourseCalendarPage permissions={permissions} /> : kind === 'exam' ? <ExamCalendarPage permissions={permissions} /> : kind === 'media' ? <MediaCalendarPage /> : kind === 'lead' ? <LeadFollowUpCalendarPage permissions={permissions} /> : <PersonalCalendarPage permissions={permissions} tenantReadAll />
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>{page}</App></ThemeProvider></BrowserRouter>)
