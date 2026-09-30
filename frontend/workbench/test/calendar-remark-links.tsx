// UTF-8. Actual components with isolated synthetic transport; never writes business data.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App, Button } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PersonalCalendarPage from '../src/pages/PersonalCalendarPage'
import CourseCalendarPage from '../src/pages/CourseCalendarPage'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import LeadFollowUpCalendarPage from '../src/pages/LeadFollowUpCalendarPage'
import FollowUpModal from '../src/components/FollowUpModal'
import FollowUpTimeline from '../src/components/FollowUpTimeline'
import CalendarNotificationPanel from '../src/components/CalendarNotificationPanel'
import { LinkedText } from '../src/components/ResourceLink'
import { api, type LeadFollowUp, type ManagedLead, type ExamSchedule } from '../src/services/api'
import { leadCalendarApi } from '../src/services/leadCalendar'
import { withExamClock } from '../src/services/examReedit'

const params = new URLSearchParams(location.search)
const scene = params.get('scene') || 'personal'
const date = dayjs().format('YYYY-MM-DD')
const link = 'https://example.test/' + 'lesson'.repeat(24) + '?q=1&next=%2F#part'
const remark = '课前  请看 ' + link + '，准备资料。\n第二行 (https://example.test/wiki/a_(b))。\n普通说明保持不变。'
const state = { remark, link, date, parentEvents: 0, writes: 0, notifications: 0 }
Object.assign(window, { remarkFixture: state })
const blockedWrite = async () => { state.writes++; throw new Error('Fixture forbids business writes') }
api.dictDataByType = async type => [{ id: 1, dictType: type, label: '验证选项', value: 'fixture', status: 0, sort: 1 }]
api.simpleDepartments = async () => []
api.personalCalendar.list = async () => [{ id: 1, title: '验证日程', description: remark,
  startTime: date + 'T09:00:00', endTime: date + 'T10:00:00', allDay: false }]
api.personalCalendar.create = blockedWrite
api.personalCalendar.update = blockedWrite
api.personalCalendar.delete = blockedWrite
api.courseCalendar.page = async () => [{ id: 1, courseName: '验证课程', courseFormValue: 'fixture', courseFormLabelSnapshot: '验证选项', remark,
  startTime: date + 'T09:00:00', endTime: date + 'T10:00:00' }]
api.courseCalendar.create = blockedWrite
api.courseCalendar.update = blockedWrite
api.courseCalendar.delete = blockedWrite
const exact: ExamSchedule = { id: 1, scheduleName: '验证单日考期', scheduleType: 'EXACT', exactDate: date,
  recordStatus: 'DRAFT', displayStatus: 'DRAFT', categoryPathSnapshot: [], remark }
const multi: ExamSchedule = { ...exact, id: 2, scheduleName: '验证多日考期', scheduleType: 'MULTI_DAY',
  startDate: date, endDate: dayjs().add(2, 'day').format('YYYY-MM-DD'), exactDate: undefined }
const revoked: ExamSchedule = { ...exact, id: 3, scheduleName: '验证重新编辑', recordStatus: 'REVOKED', displayStatus: 'REVOKED',
  canReedit: true, reeditDeadline: Date.now() + 300000, serverTime: Date.now() }
api.examCalendar.exactPage = () => withExamClock(async () => ({ list: [exact, revoked], total: 2 }))
api.examCalendar.multiDayPage = async () => ({ list: [multi], total: 1 })
api.examCalendar.reedit = async () => ({ scheduleType: 'EXACT', scheduleName: revoked.scheduleName!, exactDate: date, remark })
api.examCalendar.create = blockedWrite
api.examCalendar.update = blockedWrite
api.examCalendar.publish = blockedWrite
api.examCalendar.revoke = blockedWrite
for (const client of [api.examCalendar, api.courseCalendar]) {
  client.notifyUsers = async () => ({ list: [{ id: 1, nickname: '验证人员' }], total: 1 })
  client.previewNotify = async () => ({ calendarVersion: 1, title: '验证安排', time: date, remark,
    recipientCount: 1, notifiedCount: 0, newRecipientCount: 1, previewToken: 'fixture', contentHash: 'fixture' })
  client.notify = async () => { state.notifications++; throw new Error('Fixture forbids sending notifications') }
}
const lead = { id: 1, leadNo: 'KZ-FIXTURE-001', submittedName: '验证客资', status: 'valid', assignmentStatus: 'owned',
  relationTypes: ['owner'], leadCategory: 'fixture', leadCategoryLabelSnapshot: '验证选项', salesStage: 'fixture',
  salesStageLabelSnapshot: '验证选项', availableActions: [{ code: 'ADD_FOLLOW_UP', enabled: true }],
  attachments: [], intendedProducts: [], visibleTabs: ['overview'], overviewVisible: true } as ManagedLead
const followUp = { id: 1, recordScope: 'lead', leadId: 1, occurredAt: Date.now(), method: 'fixture', methodLabel: '验证选项',
  result: 'fixture', resultLabel: '验证选项', operatorName: '验证人员', images: [], remark } as LeadFollowUp
leadCalendarApi.days = async () => [{ date, count: 1 }]
leadCalendarApi.cards = async () => ({ list: [{ lead, deadline: Date.now(), canReadFollowUp: !params.has('noFollowUp'), lastFollowUp: followUp }], total: 1 })
api.managedLead = async () => lead

const permissions = params.has('denied') ? [] : ['*:*:*']
const pages = { personal: PersonalCalendarPage, course: CourseCalendarPage, exam: ExamCalendarPage, lead: LeadFollowUpCalendarPage }
const Page = pages[scene as keyof typeof pages]
const view = Page ? <Page permissions={permissions} />
  : scene === 'notify' ? <CalendarNotificationPanel calendarType={params.has('exam') ? 'EXAM' : 'COURSE'} calendarId={1} permissions={permissions} onClose={() => {}} />
  : scene === 'form' ? <FollowUpModal lead={lead} open onClose={() => {}} onSuccess={() => {}} />
  : scene === 'timeline' ? <FollowUpTimeline records={[{ ...followUp, remark: remark + '\n' + '更多说明\n'.repeat(10) + '末尾 https://example.test/end' }]} />
  : <div data-testid="click-parent" onClick={() => state.parentEvents++} onAuxClick={() => state.parentEvents++} onKeyDown={() => state.parentEvents++} onKeyUp={() => state.parentEvents++}>
    <LinkedText text={remark} mode="remark" /><Button>后续按钮</Button>
  </div>
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><main style={{ padding: 12 }}>{view}</main></App></ThemeProvider></BrowserRouter>)
