// UTF-8. Synthetic transport for isolated browser verification only.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { CashbackPage } from '../src/pages/ManagementPages'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const state = { fail: false, catalogFail: params.has('catalog-error'), catalogEmpty: params.has('catalog-empty'), delay: 0, calls: [] as Array<{ url: string; body: Record<string, unknown> }> }
Object.assign(window, { cashbackTabsFixture: state })
const types = [{ value: 'valid', label: '有效返现' }, { value: 'deal', label: '成交返现' }, { value: 'upgrade', label: '升级返现' }]
const statuses = [{ value: 'pending', label: '观察期' }, { value: 'available', label: '可提现' }, { value: 'blocked', label: '不可提现' }, { value: 'withdrawing', label: '提现中' }, { value: 'settled', label: '已结算' }, { value: 'cancelled', label: '已取消' }]
const rows = types.flatMap(type => statuses.flatMap(status => Array.from({ length: 12 }, (_, index) => ({
  id: types.indexOf(type) * 1000 + statuses.indexOf(status) * 20 + index + 1,
  cashbackNo: 'CB-' + type.value + '-' + status.value + '-' + (index + 1), type: type.value, status: status.value,
  amount: 20, beneficiaryName: '合成受益人', partnerName: '合成合作方', productNameSnapshot: '合成课程',
  source: { leadAccess: 'denied', orderAccess: 'not_applicable' },
}))))
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.includes('advanced-filter/catalog')) {
    if (state.catalogFail) throw new Error('目录加载失败')
    data = { scene: 'cashback', fields: state.catalogEmpty ? [] : [{ fieldKey: 'cashback.type', options: types }, { fieldKey: 'cashback.status', options: statuses }] }
  } else if (url.includes('filter-scheme')) data = { fields: [], conditions: [], groups: [] }
  else if (url.includes('/cashback/') && url.endsWith('page')) {
    const body = config.method === 'post' ? JSON.parse(config.data as string) : config.params
    state.calls.push({ url, body: { ...body } })
    if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay))
    if (state.fail) throw new Error('列表加载失败，请重试')
    const filtered = rows.filter(row => (!body.type || row.type === body.type) && (!body.status || row.status === body.status) && (!body.keyword || row.cashbackNo.includes(body.keyword)))
    const start = (body.pageNo - 1) * body.pageSize
    data = { list: filtered.slice(start, start + body.pageSize), total: filtered.length }
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const permissions = params.has('unauthorized') ? [] : [params.has('personal') ? 'zsjos:cashback:my-query' : 'zsjos:cashback:finance-query']
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider locale={zhCN}><App><CashbackPage permissions={permissions}/></App></ConfigProvider></ThemeProvider></MemoryRouter>)
