// UTF-8. Real page, synthetic transport; no shared business mutations.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { api, http, type ManagedLead } from '../src/services/api'
import { STORAGE_KEYS } from '../src/constants'
import { MENU_TASK_INVALIDATED_EVENT } from '../src/services/menuTaskRefresh'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: params.get('layout') || 'split' }))
localStorage.removeItem('zsjos.lead-management.list-collapsed')
localStorage.setItem('zsjos_lead_inbox_unseen', '[1]')
const state = { completed: false, fail: false, reads: 0, releaseRead: () => {}, invalidate: () => window.dispatchEvent(new Event(MENU_TASK_INVALIDATED_EVENT)) }
Object.assign(window, { taskFixture: state })
const leads = (): ManagedLead[] => [3, 1, 2].map(id => ({
  id, leadNo: `KZ-TEST-${id}`, submittedName: `任务验证${id}`, status: 'submitted', assignmentStatus: 'owned',
  qualificationStatus: 'pending', operationalStatus: 'active', visibleTabs: [], availableActions: [],
  submittedAt: 1700000000000 + id * 1000,
  pendingTasks: id !== 3 && !state.completed ? [{ id: id * 10, taskType: 'lead_first_follow_up', title: '首次跟进', overdue: false },
    ...(id === 2 ? [{ id: 21, taskType: 'lead_qualification', title: '客资判定', dueAt: Date.now() - 86400000, overdue: true }] : [])] : []
} as ManagedLead))
http.defaults.adapter = async config => ({ data: { code: 0, data: config.url?.endsWith('/page') ? { list: [], total: 0 } : [] }, status: 200, statusText: 'OK', headers: {}, config })
api.dictDataByType = async () => []
api.leadInboxFilterProfile = async () => ({ groups: [] })
api.allLeadPage = async query => { if (query.pendingTasksFirst !== true) throw new Error('默认列表必须请求待办置顶'); state.reads++; if (params.has('hold') && state.reads === 1) await new Promise<void>(resolve => { state.releaseRead = resolve }); if (state.fail) throw new Error('测试列表读取失败'); return { list: leads().sort((a, b) => Number(Boolean(b.pendingTasks?.length)) - Number(Boolean(a.pendingTasks?.length)) || b.submittedAt! - a.submittedAt!), total: 3 } }
api.managedLead = async id => leads().find(item => item.id === id)!
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC"><LeadManagementPage permissions={[]}/></RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
