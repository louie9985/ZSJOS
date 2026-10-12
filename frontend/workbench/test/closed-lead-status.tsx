// UTF-8. Real components with synthetic transport; never sends business requests.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import LeadDetailOverview from '../src/components/LeadDetailOverview'
import { api, http, type ManagedLead } from '../src/services/api'
import { STORAGE_KEYS } from '../src/constants'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: params.get('layout') || 'split' }))
const pending = params.has('pending')
const lead: ManagedLead = {
  id: 1, leadNo: 'LD-STATUS-TEST', personId: 1, submittedName: '状态验证客资',
  sourceType: 'internal_new_media', status: pending ? 'submitted' : 'closed',
  assignmentStatus: 'unassigned', handlingStage: 'unassigned', qualificationStatus: 'pending',
  operationalStatus: 'active', relationTypes: [], availableActions: [], visibleTabs: [], overviewVisible: true,
  submittedAt: 1783134000000, createTime: 1783134000000, updateTime: 1783306800000,
  attachments: [], intendedProducts: [], closedAt: pending ? undefined : 1783306800000,
}
api.dictDataByType = async () => []
api.leadInboxFilterProfile = async () => ({ groups: [{ key: 'all', label: '全部客资', sections: [] }, { key: 'closed', label: '已关闭客资', sections: [] }] })
api.allLeadPage = async () => ({ list: [lead], total: 1 })
api.managedLead = async () => lead
api.leadFollowUpPage = async () => ({ list: [], total: 0 })
api.leadFlowHistory = async () => []
http.defaults.adapter = async config => ({ data: { code: 0, data: [] }, status: 200, statusText: 'OK', headers: {}, config })
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC">
  {params.has('overview')
    ? <LeadDetailOverview lead={lead} categoryLabel={() => '未记录'} channelLabel={() => '未记录'} showFollowUp={false} profileVariant="contact-rows" />
    : <LeadManagementPage permissions={['zsjos:lead:query-all']} />}
</RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
