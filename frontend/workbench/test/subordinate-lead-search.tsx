// UTF-8. Isolated adapter for actual pages; synthetic data, no live requests or writes.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import SubordinateSalesPage from '../src/pages/SubordinateSalesPage'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import { http, type AdvancedFilterGroup } from '../src/services/api'
import '../src/styles/index.css'

const state = { requests: [] as Record<string, unknown>[], nextDelay: 0, nextFailure: '', completed: 0 }
Object.assign(window, { searchFixture: state })
const sales = [5, 6].map(userId => ({ userId, name: `测试销售${userId}`, username: `synthetic${userId}`, presence: 'offline', accepting: false, accountStatus: 0, canReceiveNewLeads: false, todayPendingCount: 0, validLeadCount: 0, convertedLeadCount: 0, effectiveOrderAmount: 0, effectiveOrderCount: 0, categoryCounts: [] }))
const rows = Array.from({ length: 42 }, (_, i) => ({ id: i + 1, leadNo: `SEARCH-${i + 1}`, submittedName: `客资${String(i + 1).padStart(2, '0')}`, submittedMobile: `1380000${String(i + 1).padStart(4, '0')}`, submittedWechatId: `wx${i + 1}`, submittedAt: 1700000000000 + i * 60000, lastActivityAt: 1700000000000 + i * 60000, status: 'submitted', assignmentStatus: 'owned' }))
const rootCondition = { fieldKey: 'lead.name', operator: 'contains', value: '客资' }
const templates = [{ id: 1, name: '根和子组', scope: 'personal', filter: { logic: 'AND', conditions: [rootCondition], groups: [{ logic: 'OR', conditions: [{ fieldKey: 'lead.name', operator: 'contains', value: '42' }], groups: [] }] } }]
function matches(row: typeof rows[number], filter?: AdvancedFilterGroup): boolean {
  if (!filter) return true
  if (filter.groups.some(g => !g.conditions.length)) throw new Error('不允许空子组')
  const values = [...filter.conditions.map(c => row.submittedName.includes(String(c.value))), ...filter.groups.map(g => matches(row, g))]
  return filter.logic === 'AND' ? values.every(Boolean) : values.some(Boolean)
}
http.defaults.adapter = async config => {
  const url = config.url || ''
  const params = config.method === 'post' ? JSON.parse(config.data) : config.params || {}
  let data: unknown = []
  let code = 0
  if (/\/subordinate-sales\/\d+\/leads/.test(url) || /\/lead\/(search-page|page)$/.test(url) || /\/media-students\/(search-page|page)$/.test(url)) {
    const delay = state.nextDelay; state.nextDelay = 0
    const failure = state.nextFailure; state.nextFailure = ''
    state.requests.push({ ...params, url, method: config.method })
    const filtered = rows.filter(row => (!params.keyword || [row.leadNo, row.submittedName, row.submittedMobile, row.submittedWechatId].some(v => v.includes(params.keyword))) && matches(row, params.advancedFilter))
    const field = params.sortField || 'lastActivityAt'
    filtered.sort((a, b) => String(a[field as keyof typeof a]).localeCompare(String(b[field as keyof typeof b])) * (params.sortOrder === 'ascend' ? 1 : -1))
    if (delay) await new Promise(resolve => setTimeout(resolve, delay))
    state.completed++
    if (failure === 'error') throw new Error('查询失败，请重试')
    if (failure === 'denied') code = 403
    const offset = (params.pageNo - 1) * params.pageSize
    data = { list: url.includes('media-students') ? [] : filtered.slice(offset, offset + params.pageSize), total: url.includes('media-students') ? 0 : filtered.length }
  } else if (url === '/zsjos/subordinate-sales/page') data = { list: sales, total: 2 }
  else if (url.endsWith('/overview')) data = sales.find(s => url.includes(`/${s.userId}/`))
  else if (url.endsWith('/tasks')) {
    const delay = state.nextDelay; state.nextDelay = 0
    const failure = state.nextFailure; state.nextFailure = ''
    state.requests.push({ ...params, url, method: config.method })
    if (delay) await new Promise(resolve => setTimeout(resolve, delay))
    state.completed++
    if (failure) throw new Error('待办查询失败')
    data = { list: [], total: 0 }
  } else if (url.endsWith('/catalog')) data = { fields: params.scene === 'subordinate_sales'
    ? [{ fieldKey: 'subordinate.accountStatus', label: '账号状态', group: '账号', valueType: 'select', operators: ['in'], options: [{ value: '0', label: '启用' }, { value: '1', label: '停用' }] }]
    : [{ fieldKey: 'lead.name', label: '提交姓名', group: '身份', valueType: 'text', operators: ['contains'], options: [] }], relativeDateOptions: [] }
  else if (url.endsWith('/visible-list')) data = templates
  else if (url.includes('filter-profile')) data = { groups: [] }
  else if (/\/lead\/\d+$/.test(url)) data = rows.find(row => row.id === Number(url.split('/').pop()))
  else if (url.endsWith('/page')) data = { list: [], total: 0 }
  else if (config.method !== 'get') throw new Error(`Unexpected fixture write: ${url}`)
  return { data: { code, msg: code ? '无权查询客资' : '', data }, config, status: 200, statusText: 'OK', headers: {} }
}
const surface = new URLSearchParams(location.search).get('surface')
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC"><WorkbenchPageNavigation canOpen={() => true}>
  <div style={{ height: '100vh' }}>{surface === 'lead' ? <LeadManagementPage permissions={[]} /> : surface === 'media' ? <MediaStudentsPage permissions={['zsjos:media-student:query-my']} /> : <SubordinateSalesPage permissions={['zsjos:subordinate-sales:lead-transfer']} />}</div>
</WorkbenchPageNavigation></RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></MemoryRouter>)
