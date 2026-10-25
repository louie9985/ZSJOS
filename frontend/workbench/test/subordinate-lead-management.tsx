// UTF-8. All HTTP is intercepted with synthetic fixtures; no live reads or writes.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import SubordinateSalesPage from '../src/pages/SubordinateSalesPage'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { http } from '../src/services/api'
import { STORAGE_KEYS } from '../src/constants'
import '../src/styles/index.css'

const state = { requests: [] as Record<string, unknown>[], nextDelay: 0, nextFailure: '', transferred: [] as number[], profileFailure: false }
Object.assign(window, { scopedFixture: state })
const query = new URLSearchParams(location.search)
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: query.get('layout') || 'table' }))
const sales = [5, 6].map(userId => ({ userId, name: `测试销售${userId}`, username: `synthetic${userId}`, presence: 'offline', accepting: false, accountStatus: 0, canReceiveNewLeads: false, todayPendingCount: 0, validLeadCount: 0, convertedLeadCount: 0, effectiveOrderAmount: 0, effectiveOrderCount: 0, categoryCounts: [] }))
const rows = [5, 6].flatMap(ownerUserId => Array.from({ length: 42 }, (_, i) => ({
  id: ownerUserId * 100 + i + 1, leadNo: `S${ownerUserId}-${i + 1}`, submittedName: `销售${ownerUserId}客资${String(i + 1).padStart(2, '0')}`,
  ownerUserId, ownerUserName: `测试销售${ownerUserId}`, submittedMobile: `1380000${ownerUserId}${String(i + 1).padStart(3, '0')}`, submittedWechatId: `wx${i + 1}`,
  status: i === 41 ? 'suspended' : i < 3 ? 'valid' : 'submitted', assignmentStatus: i === 40 ? 'recycle_pending' : 'owned',
  sourceLabel: '新媒体提交', sourceType: 'media', leadCategory: 'A', leadCategoryLabelSnapshot: 'A类', sourceChannelLabelSnapshot: '测试渠道',
  submittedAt: 1700000000000 + i * 60000, lastActivityAt: 1700000000000 + i * 60000, today: i > 30,
  visibleTabs: query.has('dirty') ? ['follow-ups'] : ['overview'], overviewVisible: true, availableActions: query.has('dirty') ? [{code: 'ADD_FOLLOW_UP', enabled: true}] : [], attachments: [], intendedProducts: [],
})))
const sections = [
  { key: 'current_stage', label: '当前环节', options: [{ key: 'all', label: '全部' }, { key: 'recycle', label: '回收待处理' }, { key: 'suspended', label: '已挂起' }, { key: 'pending', label: '待首跟' }] },
  { key: 'quick_condition', label: '快捷条件', options: [{ key: 'all', label: '全部' }, { key: 'today', label: '今日待跟进' }, { key: 'empty', label: '跟进已逾期' }] },
]
const profile = { groups: ['全部客资', '待判定客资', '有效客资', '无效客资', '已关闭客资', '手动录入客资'].map((label, i) => ({ key: ['all', 'pending', 'valid', 'invalid', 'closed', 'manual'][i], label, sections })) }
http.defaults.adapter = async config => {
  const url = config.url || ''
  const params = config.method === 'post' ? JSON.parse(config.data || '{}') : config.params || {}
  let data: unknown = []
  if (/\/subordinate-sales\/\d+\/leads(?:\/search-page)?$/.test(url) || /\/lead\/(?:search-page|page)$/.test(url)) {
    state.requests.push({ ...params, url, method: config.method })
    const owner = Number(url.match(/subordinate-sales\/(\d+)/)?.[1])
    const delay = state.nextDelay; state.nextDelay = 0
    const failure = state.nextFailure; state.nextFailure = ''
    if (delay) await new Promise(resolve => setTimeout(resolve, delay))
    if (failure) return { data: { code: failure === 'denied' ? 403 : 500, msg: failure === 'denied' ? '无权查询客资' : '模拟查询失败' }, config, status: 200, statusText: 'OK', headers: {} }
    const list = rows.filter(row => (!owner || row.ownerUserId === owner) && !state.transferred.includes(row.id)
      && (params.inboxStage === 'recycle' ? row.assignmentStatus === 'recycle_pending' : row.assignmentStatus === 'owned')
      && (!params.keyword || `${row.leadNo}${row.submittedName}${row.submittedMobile}${row.submittedWechatId}`.includes(params.keyword))
      && (!params.inboxGroup || params.inboxGroup === 'all' || row.status === params.inboxGroup)
      && (params.inboxStage !== 'suspended' || row.status === 'suspended')
      && (params.inboxQuick !== 'today' || row.today) && params.inboxQuick !== 'empty'
      && (!params.advancedFilter || row.submittedName.endsWith('42')))
    list.sort((a, b) => String(a[(params.sortField || 'lastActivityAt') as keyof typeof a]).localeCompare(String(b[(params.sortField || 'lastActivityAt') as keyof typeof b])) * (params.sortOrder === 'ascend' ? 1 : -1))
    data = { list: list.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize), total: list.length }
  } else if (url === '/zsjos/subordinate-sales/page') data = { list: sales, total: 2 }
  else if (url.endsWith('/overview')) data = sales.find(row => url.includes(`/${row.userId}/`))
  else if (url.endsWith('/tasks') || url.endsWith('/follow-ups/page')) data = { list: [], total: 0 }
  else if (url.endsWith('/filter-profile')) {
    if (state.profileFailure) throw new Error('模拟筛选方案失败')
    data = profile
  } else if (url.endsWith('/catalog')) data = { fields: params.scene === 'subordinate_sales' ? [{ fieldKey: 'subordinate.accountStatus', label: '账号状态', group: '账号', valueType: 'select', operators: ['in'], options: [{value:'0',label:'启用'},{value:'1',label:'停用'}] }] : [{ fieldKey: 'lead.name', label: '提交姓名', group: '身份', valueType: 'text', operators: ['contains'], options: [] }], relativeDateOptions: [] }
  else if (url.endsWith('/visible-list')) data = [{ id: 1, name: '客资42', scope: 'personal', filter: { logic: 'AND', conditions: [{ fieldKey: 'lead.name', operator: 'contains', value: '42' }], groups: [] } }]
  else if (url === '/zsjos/lead/get') { state.requests.push({ url, ...params }); data = rows.find(row => row.id === params.id) }
  else if (url.endsWith('/transfer-candidates') || url.endsWith('/batch-transfer-candidates')) data = [{ id: 7, nickname: '目标销售7' }]
  else if (url.endsWith('/batch-transfer')) {
    state.requests.push({ url, ...params }); state.transferred.push(...params.leadIds)
    data = { successCount: params.leadIds.length, failureCount: 0, items: params.leadIds.map((id: number) => ({ leadId: id, leadNo: rows.find(row => row.id === id)?.leadNo, success: true, message: '转派成功' })) }
  } else if (config.method !== 'get') throw new Error(`Unexpected fixture write: ${url}`)
  return { data: { code: 0, data }, config, status: 200, statusText: 'OK', headers: {} }
}
const permissions = query.has('denied') ? [] : ['zsjos:subordinate-sales:query', 'zsjos:subordinate-sales:lead-transfer']
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={[query.has('deep') ? '/zsjos/subordinate-sales?leadId=601' : '/zsjos/subordinate-sales']}><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC"><WorkbenchPageNavigation canOpen={() => true}>
  <div style={{ height: '100vh' }}>{query.has('general') ? <LeadManagementPage permissions={permissions} /> : <SubordinateSalesPage permissions={permissions} />}</div>
</WorkbenchPageNavigation></RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></MemoryRouter>)
