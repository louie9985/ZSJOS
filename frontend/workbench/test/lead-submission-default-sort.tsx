// UTF-8. Real page, synthetic transport; no shared business mutations.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import dayjs from 'dayjs'
import LeadFollowUpCalendarPage from '../src/pages/LeadFollowUpCalendarPage'
import { leadCalendarApi } from '../src/services/leadCalendar'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { api, http, type ManagedLead } from '../src/services/api'
import { STORAGE_KEYS } from '../src/constants'
import { MENU_TASK_INVALIDATED_EVENT } from '../src/services/menuTaskRefresh'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: params.get('layout') || 'split' }))
localStorage.removeItem('zsjos.lead-management.list-collapsed')
localStorage.setItem('zsjos_lead_inbox_unseen', '[3]')
const state = { completed: false, fail: false, reads: 0, invalidate: () => window.dispatchEvent(new Event(MENU_TASK_INVALIDATED_EVENT)) }
Object.assign(window, { taskFixture: state })
const leads = (): ManagedLead[] => [3, 1, 2].map(id => ({
  submittedAt: id === 1 ? 300 : 200, lastActivityAt: id === 2 ? 999 : 1,
  id, leadNo: `KZ-TEST-${id}`, submittedName: `任务验证${id}`, status: 'submitted', assignmentStatus: 'owned',
  qualificationStatus: 'pending', operationalStatus: 'active', visibleTabs: [], availableActions: [],
  pendingTasks: id !== 3 && !state.completed ? [{ id: id * 10, taskType: 'lead_first_follow_up', title: '首次跟进', overdue: false },
    ...(id === 2 ? [{ id: 21, taskType: 'lead_qualification', title: '客资判定', dueAt: Date.now() - 86400000, overdue: true }] : [])] : []
} as ManagedLead))
http.defaults.adapter = async config => ({ data: { code: 0, data: config.url?.endsWith('/page') ? { list: [], total: 0 } : [] }, status: 200, statusText: 'OK', headers: {}, config })
api.dictDataByType = async () => []
api.leadInboxFilterProfile = async () => ({ groups: [] })
api.allLeadPage = async query => { if (query.pendingTasksFirst) throw new Error('不应请求待办置顶'); state.reads++; if (state.fail) throw new Error('测试列表读取失败'); return { list: leads(), total: 3 } }
leadCalendarApi.days = async () => [{ date: dayjs().format('YYYY-MM-DD'), count: 3 }]
leadCalendarApi.cards = async query => { Object.assign(state, { calendarQuery: query }); return { list: leads().map(lead => ({ lead, deadline: Date.now(), canReadFollowUp: false })), total: 3 } }
api.managedLead = async id => leads().find(item => item.id === id)!
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC">{params.get('calendar') ? <LeadFollowUpCalendarPage permissions={['zsjos:lead-follow-up-calendar:query', 'zsjos:lead:query']}/> : <LeadManagementPage permissions={[]}/>}</RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
