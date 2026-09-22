// UTF-8. Isolated synthetic transport; no real business writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useEffect, useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { App, Button } from 'antd'
import LeadDetail from '../src/components/LeadDetail'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http, type ManagedLead, type LeadFollowUp } from '../src/services/api'
const records: LeadFollowUp[] = []
let fail = false
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/follow-ups') && config.method === 'post') {
    const body = JSON.parse(config.data)
    records.unshift({ ...body, id: records.length + 1, occurredAt: Date.now(), methodLabel: '电话', resultLabel: '已联系' })
    data = records[0]
  } else if (url.endsWith('/follow-ups/page')) {
    if (fail) { fail = false; throw new Error('最近跟进读取失败（验证）') }
    data = { list: records.slice(0, config.params?.pageSize || 10), total: records.length }
  } else if (url.includes('dict-data')) data = ['zsjos_lead_follow_up_method', 'zsjos_lead_follow_up_result'].map(dictType => ({ dictType, value: 'test', label: '测试选项', status: 0 }))
  else if (url === '/zsjos/lead/page') data = { list: [{ ...original, nextFollowUpAt: records[0]?.nextFollowUpAt, salesOrderSubmittedAt: 1790049600000, lastActivityAt: records[0]?.occurredAt }], total: 40 }
  else if (url === '/zsjos/lead/get') data = { ...original, nextFollowUpAt: records[0]?.nextFollowUpAt, lastActivityAt: records[0]?.occurredAt }
  else if (url.includes('/page')) data = { list: [], total: 0 }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const original = { id: 1, leadNo: 'TEST-REFRESH', submittedName: '验证客资', status: 'submitted', assignmentStatus: 'owned', qualificationStatus: 'pending', followUpStatus: 'following', operationalStatus: 'active', visibleTabs: ['overview', 'follow-ups'], availableActions: [{ code: 'ADD_FOLLOW_UP', enabled: true }] } as ManagedLead
function TableFixture() {
  const { setInboxLayoutMode } = useTheme()
  useEffect(() => setInboxLayoutMode('table'), [setInboxLayoutMode])
  return <MemoryRouter><RealtimeProvider platform="PC"><LeadManagementPage permissions={[]}/></RealtimeProvider></MemoryRouter>
}
function Fixture() {
  const [lead, setLead] = useState(original)
  return <App><Button onClick={() => { fail = true; setLead({ ...lead, lastActivityAt: Date.now() }) }}>模拟读取失败</Button><LeadDetail lead={lead} categories={[]} categoryLabel={() => '-'} channelLabel={() => '-'} mode="owner" autoExpandFollowUp={false} onDirtyChange={() => {}} onChanged={() => setLead({ ...lead, nextFollowUpAt: records[0]?.nextFollowUpAt, lastActivityAt: Date.now() })}/></App>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><OverlayCoordinatorProvider>{location.search.includes('table') ? <App><TableFixture /></App> : <Fixture />}</OverlayCoordinatorProvider></ThemeProvider>)
