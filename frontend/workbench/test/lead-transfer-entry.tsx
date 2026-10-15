// UTF-8. Synthetic API adapter only; this fixture never writes business data.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import LeadDetail from '../src/components/LeadDetail'
import { http, type ManagedLead } from '../src/services/api'

const params = new URLSearchParams(location.search)
const scenario = params.get('case') || 'both'
const calls: { url: string; method?: string; body?: unknown }[] = []
Object.assign(window, { transferCalls: calls })
http.defaults.adapter = async config => {
  const url = config.url || ''
  calls.push({ url, method: config.method, body: config.data ? JSON.parse(config.data) : undefined })
  let data: unknown = []
  if (url.endsWith('/transfer-candidates')) data = [{ id: 200, nickname: '验证销售', status: 0 }]
  else if (config.method === 'post' && url.endsWith('/transfer')) data = true
  else if (url.includes('/page')) data = { list: [], total: 0 }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const qualification = { code: 'QUALIFICATION_TRANSFER' as const, enabled: scenario !== 'disabled' && scenario !== 'qualification-disabled' }
const supervisor = { code: 'SUPERVISOR_TRANSFER' as const, enabled: scenario !== 'disabled' && scenario !== 'supervisor-disabled' }
const availableActions = scenario === 'none' ? [] : scenario === 'qualification' ? [qualification]
  : scenario === 'supervisor' ? [supervisor] : [qualification, supervisor]
const lead = {
  id: 101, leadNo: 'TEST-TRANSFER', submittedName: '转派入口验证客资', status: 'submitted',
  assignmentStatus: 'recycle_pending', qualificationStatus: 'pending', followUpStatus: 'paused',
  operationalStatus: 'active', visibleTabs: ['overview'], availableActions,
} as ManagedLead
function Fixture() {
  const [completed, setCompleted] = useState(0)
  return <App><output id="completed">{completed}</output><LeadDetail lead={lead} categories={[]}
    categoryLabel={() => '-'} channelLabel={() => '-'} mode={params.get('mode') === 'manager' ? 'manager-readonly' : params.get('mode') === 'readonly' ? 'student-readonly' : 'owner'}
    autoExpandFollowUp={false} onDirtyChange={() => {}} onChanged={() => setCompleted(value => value + 1)} /></App>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><OverlayCoordinatorProvider><Fixture /></OverlayCoordinatorProvider></ThemeProvider>)
