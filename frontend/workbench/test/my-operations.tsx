// UTF-8. Real components; synthetic, isolated transport with no business writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import MyOperationsPage from '../src/pages/MyOperationsPage'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http, type ManagedLead } from '../src/services/api'
import '../src/styles/index.css'

const options = new URLSearchParams(location.search)
const fixture = { requests: [] as Array<{ url: string; method?: string; body?: unknown }>, failMembers: options.has('error'), failLeads: options.has('lead-error') }
Object.assign(window, { myOperationsFixture: fixture })
const lead = (member: number): ManagedLead => ({
  id: member * 10, leadNo: `KZ-OPERATIONS-${member}`, personId: member * 10,
  submittedName: member === 21 ? '代录归属客资' : '运营直接提交客资',
  sourceType: 'internal_new_media', sourceLabel: '运营客资', sourceUserName: '业务提供方',
  status: 'submitted', assignmentStatus: 'owned', qualificationStatus: 'pending',
  followUpStatus: 'first_follow_pending', operationalStatus: 'active',
  submittedAt: 1786154400000, createTime: 1786154400000, updateTime: 1786154400000,
  relationTypes: [], visibleTabs: options.has('overlap') ? ['overview', 'appeals', 'follow-ups'] : ['overview'],
  availableActions: options.has('overlap') ? [{ code: 'ADD_FOLLOW_UP', enabled: true }, { code: 'SUPERVISOR_TRANSFER', enabled: true }, { code: 'REQUEST_SUBMITTER_ASSIST', enabled: true }] : [], intendedProducts: [], attachments: [],
})
http.defaults.adapter = async config => {
  const url = config.url || ''
  fixture.requests.push({ url, method: config.method, body: config.data ? JSON.parse(config.data) : undefined })
  let data: unknown = []
  let code = 0
  let msg = ''
  if (url.endsWith('/my-operations/members')) {
    if (fixture.failMembers) { fixture.failMembers = false; code = 500; msg = '运营团队暂时不可用' }
    else data = options.has('empty') ? [] : [{ userId: 21, name: '测试运营甲', accountStatus: 0, leadCount: 3 }, { userId: 22, name: '测试运营乙', accountStatus: 1, leadCount: 1 }]
  } else if (url.endsWith('/my-operations/leads/filter-profile')) data = { groups: [{ key: 'all', label: '全部客资', sections: [{ key: 'quick_condition', label: '快捷筛选', options: [{ key: 'all', label: '全部' }, { key: 'today', label: '今日提交' }] }] }] }
  else if (/my-operations\/\d+\/leads\/search-page$/.test(url)) {
    if (fixture.failLeads) { code = 403; msg = '无权查看该运营的客资' }
    else { const member = Number(url.split('/').at(-3)); data = { list: [lead(member)], total: 1 } }
  } else if (/my-operations\/\d+\/leads\/\d+$/.test(url)) data = lead(Number(url.split('/').at(-3)))
  else if (url.endsWith('/system/dict-data/simple-list')) data = [
    { dictType: 'common_status', value: '0', label: '启用' }, { dictType: 'common_status', value: '1', label: '停用' },
  ]
  else if (/lead\/\d+\/follow-ups\/page$/.test(url)) data = { list: [], total: 0 }
  else if (/lead\/appeal\/lead\/\d+\/context$/.test(url)) data = { invalidEventId: 1, nextRoundNo: 1, canSubmit: true, records: [] }
  else if (url.endsWith('/advanced-filter/catalog')) data = { fields: [] }
  else if (url.endsWith('/advanced-filter-template/visible-list')) data = []
  else throw new Error(`Unexpected fixture request: ${config.method} ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code, msg, data } }
}
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/my-operations']}>
  <OverlayCoordinatorProvider><RealtimeProvider platform="PC"><WorkbenchPageNavigation canOpen={() => true}><div style={{ height: '100vh' }}>
    <MyOperationsPage permissions={options.has('denied') ? [] : ['zsjos:my-operations:query', 'zsjos:subordinate-sales:lead-transfer', 'zsjos:lead:appeal-create']} />
  </div></WorkbenchPageNavigation></RealtimeProvider></OverlayCoordinatorProvider>
</MemoryRouter></App></ThemeProvider></ConfigProvider>)
