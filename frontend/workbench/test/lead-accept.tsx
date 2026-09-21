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
  visibleTabs: mode === 'denied' ? ['overview'] : ['overview', 'follow-ups'],
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
function Fixture() {
  const route = useLocation(), navigate = useNavigate()
  const { setInboxLayoutMode } = useTheme()
  return <><div id="route">{route.pathname}{route.search}</div><div id="saved" />
    <Button onClick={() => setInboxLayoutMode('table')}>表格模式</Button>
    <Button onClick={() => setInboxLayoutMode('split')}>双栏模式</Button>
    <Button onClick={() => navigate(`${APP_ROUTES.LEAD_MANAGEMENT}?leadId=999&tab=overview`)}>旧客资</Button>
    <LeadAssignmentHost canAccept onCountChange={() => {}} openRequest={0} />
    <Routes><Route path={APP_ROUTES.LEAD_MANAGEMENT} element={<LeadManagementPage permissions={[]} />} /><Route path="*" element={<div>其他页面</div>} /></Routes></>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={[params.has('same') ? `${APP_ROUTES.LEAD_MANAGEMENT}?leadId=999&tab=overview` : '/other']}><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC"><Fixture /></RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></MemoryRouter>)
