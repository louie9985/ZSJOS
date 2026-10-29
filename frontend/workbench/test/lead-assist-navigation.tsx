import RetainedReviewRoute from '../src/layouts/RetainedReviewRoutes'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
// UTF-8. Isolated browser fixture: synthetic API data, no business server writes.
import { createRoot } from 'react-dom/client'
import { App, Button } from 'antd'
import { MemoryRouter, Routes, Route, useLocation, useNavigate } from 'react-router-dom'
import { api, http, type ManagedLead, type PendingLead } from '../src/services/api'
import LeadAssignmentHost from '../src/components/LeadAssignmentHost'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import { APP_ROUTES } from '../src/constants'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const mode = params.get('mode') || 'normal'
const pause = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
let accepted = 0, detailAttempts = 0
let pending = (mode === 'queue' || mode === 'race' ? [101, 102] : [101]).map(id => ({
  id, leadNo: `TEST-${id}`, maskedName: `测试客资${id}`, intendedProducts: [], attachmentUrls: [], attachments: [],
  dispatchMode: mode === 'specified' ? 'specified' : 'auto', rejectable: mode !== 'specified', deferrable: true
} as PendingLead))
const detail = (id: number) => ({ id, leadNo: `TEST-${id}`, submittedName: `测试客资${id}`, intendedProducts: [],
  visibleTabs: mode === 'denied' ? ['overview'] : ['overview', 'follow-ups', 'assist-history'],
  availableActions: mode === 'denied' ? [] : [{ code: 'ADD_FOLLOW_UP', label: '新增跟进' }], attachments: []
} as unknown as ManagedLead)
api.myPendingLeads = async () => { if (mode === 'refresh-error' && accepted) throw new Error('待接列表刷新失败（测试）'); return pending }
api.acceptLead = async id => { if (mode === 'accept-error') throw new Error('接单已超时（测试）'); accepted++; pending = pending.filter(item => item.id !== id); return true }
api.rejectLead = async id => { pending = pending.filter(item => item.id !== id); return true }
api.managedLead = async id => {
  detailAttempts++
  if (mode === 'race' && id === 101) await pause(1600)
  if (mode === 'detail-error' && detailAttempts === 1) throw new Error('详情加载失败（测试）')
  if (mode === 'object-denied') throw new Error('无权查看此客资（测试）')
  return detail(id)
}
api.allLeadPage = async () => ({ list: [], total: 0 })
api.dictDataByType = async () => [{ value: 'test', label: '测试选项' }]
api.leadFollowUpPage = async () => ({ list: [], total: 0 })
api.createLeadFollowUp = async () => { document.getElementById('saved')!.textContent = '跟进已保存'; return true as never }
http.defaults.adapter = async config => {
  const url = config.url || ''
  const data = url.includes('catalog') ? { fields: [], relativeDateOptions: [] } : url.includes('page') ? { list: [], total: 0 } : []
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}

api.leadInboxFilterProfile = async () => ({ groups: [{ key: 'all', label: '全部', sections: [] }] })
api.businessTaskSummary = async () => ({today:0, overdue:0, future:0, unscheduled:2})
api.businessTaskList = async q => ({list:q.bucket === 'unscheduled' ? [101,102].map(id=>({id,bizId:id,title:`协助测试${id}`,actionCode:'OPEN_LEAD_SUBMITTER_ASSIST',actionable:true,status:'pending'} as any)) : [], total:q.bucket === 'unscheduled' ? 2:0})
function Fixture() {
 const route=useLocation(),navigate=useNavigate()
 const { setInboxLayoutMode }=useTheme()
 return <><Button onClick={()=>setInboxLayoutMode('table')}>表格模式</Button><Button onClick={()=>navigate('/home')}>返回首页</Button>
 <RetainedReviewRoute active={route.pathname==='/home'}><TodayTasksPage permissions={[]} onOpenAssignment={()=>{}} /></RetainedReviewRoute>
 <RetainedReviewRoute active={route.pathname===APP_ROUTES.LEAD_MANAGEMENT}><LeadManagementPage permissions={[]} /></RetainedReviewRoute></>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={['/home']}><ThemeProvider><App><WorkbenchPageNavigation canOpen={()=>true} canRetain={()=>true}><OverlayCoordinatorProvider><RealtimeProvider platform="PC"><AnnouncementProvider enabled={false}><Fixture /></AnnouncementProvider></RealtimeProvider></OverlayCoordinatorProvider></WorkbenchPageNavigation></App></ThemeProvider></MemoryRouter>)
