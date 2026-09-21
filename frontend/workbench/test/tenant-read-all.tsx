// UTF-8. Isolated synthetic responses; no business requests or writes.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PersonalCalendarPage from '../src/pages/PersonalCalendarPage'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import WorkOrderCenterPage from '../src/pages/WorkOrderCenterPage'
import { WithdrawalPage } from '../src/pages/ManagementPages'
import { MyStudentsPage } from '../src/pages/RegistrationPages'
import FeedbackPage from '../src/pages/FeedbackPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const fixture = { requests: [] as { url: string; params: Record<string, unknown> }[], writes: 0, mode: 'success' }
Object.assign(window, { tenantReadFixture: fixture })
http.defaults.adapter = async config => {
  if (config.method !== 'get') { fixture.writes++; throw new Error('unexpected mutation') }
  const url = config.url || '', query = config.params || {}
  fixture.requests.push({ url, params: query })
  if (fixture.mode === 'error' && !url.includes('simple-list')) throw new Error('测试读取失败')
  const self = !query.readScope || query.readScope === 'SELF'
  const feedback = { id: 1, feedbackType: 'BUG', feedbackNo: 'FB-TEST', title: '反馈读取测试', status: 'WAITING',
    submitterUserId: 20, submitterName: '测试人员', assigneeName: '处理人员', version: 1, unread: false,
    canReply: false, canResubmit: false, canSubmitSurvey: false, fields: [], values: {}, replies: [] }
  let data: unknown = []
  if (url.includes('/system/user/simple-list')) data = [{ id: 20, nickname: '停用测试人员', status: 1 }]
  else if (fixture.mode === 'empty') data = url.includes('summary') ? { today: 0, future: 0, overdue: 0, unscheduled: 0 } : url.includes('page') ? { list: [], total: 0 } : []
  else if (url.includes('/personal-calendar')) data = [{ id: 1, title: self ? '本人日程' : '他人日程', ownerUserId: 20,
    ownerName: '测试人员', startTime: dayjs().hour(9).format('YYYY-MM-DDTHH:mm:ss'), endTime: dayjs().hour(10).format('YYYY-MM-DDTHH:mm:ss'), allDay: false }]
  else if (url.includes('my-summary')) data = { today: self ? 1 : 3, future: 0, overdue: 0, unscheduled: 0 }
  else if (url.includes('my-task-page')) data = { list: [{ id: 1, title: self ? '本人任务' : '他人任务', status: 'pending',
    assigneeId: 20, assigneeName: '测试人员', actionCode: self ? 'COMPLETE_BIRTHDAY_CARE' : null, actionable: self }], total: 1 }
  else if (url.includes('/work-order/my-page')) data = { list: [{ id: 1, orderNo: 'WO-TEST', businessType: 'GENERIC', status: 'IN_PROGRESS', sceneName: '测试工单', sourceName: '测试发起人', targetName: '测试处理人', currentRound: 1, availableActions: [], version: 0 }], total: 1 }
  else if (url.includes('/withdrawal/') && url.includes('page')) data = { list: [{ id: 1, withdrawalNo: 'WD-TEST', applicantUserId: 20, applicationAmount: 10, status: 'pending_review', maskedCardNumber: '****1234' }], total: 1 }
  else if (url.includes('/student/my-page')) data = { list: [], total: 0 }
  else if (url.includes('/delivery-class/') && url.includes('page')) data = { list: [], total: 0 }
  else if (url.includes('/feedback/portal')) data = { entries: [], recent: [] }
  else if (url.includes('/feedback/my-page')) data = { list: [feedback], total: 1 }
  else if (url.includes('/feedback/1')) data = feedback
  return { config, data: { code: 0, data }, status: 200, statusText: 'OK', headers: {} }
}
const permissions = ['zsjos:personal-calendar:query','zsjos:personal-calendar:create','zsjos:personal-calendar:update','zsjos:personal-calendar:delete','zsjos:feedback:read','zsjos:feedback:reply-self','zsjos:business-task:query','zsjos:withdrawal:my-query','zsjos:withdrawal:apply']
const tenantReadAll = !params.has('ordinary')
const view = params.get('view')
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter initialEntries={[view === 'workorders' ? '/zsjos/work-orders/mine' : '/']}>
  {view === 'tasks' ? <TodayTasksPage permissions={permissions} tenantReadAll={tenantReadAll} onOpenAssignment={() => { throw new Error('unexpected assignment') }} />
    : view === 'workorders' ? <WorkOrderCenterPage tenantReadAll={tenantReadAll} />
    : view === 'withdrawal' ? <WithdrawalPage permissions={permissions} tenantReadAll={tenantReadAll} />
    : view === 'students' ? <MyStudentsPage permissions={permissions} tenantReadAll={tenantReadAll} />
    : view === 'feedback' ? <FeedbackPage permissions={permissions} tenantReadAll={tenantReadAll} />
    : <PersonalCalendarPage permissions={permissions} tenantReadAll={tenantReadAll} />}
</MemoryRouter></App></ThemeProvider>)
