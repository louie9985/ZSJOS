import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { CashbackPage } from '../src/pages/ManagementPages'
import { http } from '../src/services/api'
import '../src/styles/index.css'
// UTF-8. Synthetic transport for isolated browser acceptance, no live business writes.
const state = { fail: false, delay: 0, calls: [] as Array<{url: string; body: Record<string, unknown>}> }
Object.assign(window, { cashbackFixture: state })
const rows = [{ id: 1, version: 0, cashbackNo: 'CB-TEST-001', beneficiaryName: '验收受益人', partnerName: '验收合作方', type: 'valid', status: 'available', amount: 20, productNameSnapshot: '测试课程', generatedAt: '2026-09-27T09:00:00', availableAt: '2026-09-27T09:00:00', source: { leadAccess: 'denied', orderAccess: 'not_applicable' }, blockReason: undefined as string | undefined }]
const history: Array<{ id: number; action: string; reason: string; operatorName: string; occurredAt: string }> = []
const adapter = async (config: any) => {
 const url = config.url || ''; let data: unknown = []
 if (url.includes('advanced-filter/catalog')) data = { scene: 'cashback', fields: [{ fieldKey: 'cashback.type', options: [{ value: 'valid', label: '有效返现' }] }, { fieldKey: 'cashback.status', options: [{ value: 'available', label: '可提现' }, { value: 'blocked', label: '不可提现' }] }] }
 else if (url.includes('filter-scheme')) data = { fields: [], conditions: [], groups: [] }
 else if (url.includes('/cashback/')) {
  if (config.method === 'put') {
   const body = JSON.parse(config.data); state.calls.push({ url, body });
   if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay))
   if (state.fail) throw new Error('返现状态已变化，请刷新后重试')
   const action = url.endsWith('/unblock') ? 'unblock' : 'block'
   rows[0].status = action === 'block' ? 'blocked' : 'available'; rows[0].version++; rows[0].blockReason = action === 'block' ? body.reason : undefined
   history.unshift({ id: history.length + 1, action, reason: body.reason, operatorName: '验收财务', occurredAt: '2026-09-27T10:00:00' }); data = true
  } else if (url.endsWith('/control-history')) data = { list: history, total: history.length }
  else if (url.endsWith('/withdrawals')) data = { list: [], total: 0 }
  else if (url.endsWith('/page') || url.endsWith('/search-page')) { const filtered = rows.filter(x => !config.params?.status || x.status === config.params.status); data = { list: filtered, total: filtered.length } }
  else data = rows[0]
 }
 return { config, request: { responseType: 'json' }, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}

http.defaults.adapter = adapter
const permissions = ['zsjos:cashback:finance-query', ...(location.search.includes('read-only') ? [] : ['zsjos:cashback:block','zsjos:cashback:unblock'])]
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider locale={zhCN}><App><CashbackPage permissions={permissions}/></App></ConfigProvider></ThemeProvider></MemoryRouter>)
