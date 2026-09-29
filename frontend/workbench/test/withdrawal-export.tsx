// UTF-8. Isolated browser fixture: synthetic transport, never shared business writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExportTaskPage from '../src/pages/ExportTaskPage'
import { WithdrawalPage } from '../src/pages/ManagementPages'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const statusTabs = new URLSearchParams(location.search).has('status-tabs')
const fixture = { calls: [] as Array<{ url: string; body: Record<string, unknown> }>, queries: [] as Record<string, unknown>[], fail: false, empty: false, loadError: false, delay: 0, catalogError: location.search.includes('catalog-error'), catalogDelay: statusTabs ? 700 : 0, listDelay: {} as Record<string, number> }
Object.assign(window, { exportFixture: fixture })
const rows = [1, 2, 3].map(id => ({ id, withdrawalNo: `TEST-${id}`, status: id === 3 ? 'paid' : 'approved', applicationAmount: 20, accountNameSnapshot: '验收账户', bankNameSnapshot: '验收银行', cardNumber: '0001234567890123456', maskedCardNumber: '****3456', submittedAt: 1789950600000 }))
const tabRows = Array.from({ length: 24 }, (_, index) => ({ ...rows[0], id: index + 1, withdrawalNo: `TEST-${index + 1}`, status: index < 12 ? 'approved' : 'paid' }))
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []

    if (url === '/zsjos/export-task' && config.method === 'post') {
      fixture.calls.push({ url, body: JSON.parse(config.data) })
      if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
      if (fixture.fail) throw new Error('导出任务创建失败，请重试')
      data = 91
    } else if (url.includes('/advanced-filter/catalog')) {
      if (fixture.catalogDelay) await new Promise(resolve => setTimeout(resolve, fixture.catalogDelay))
      if (fixture.catalogError) throw new Error('目录加载失败')
      data = { fields: [{ fieldKey: 'withdrawal.status', label: '状态', options: statusTabs ? [{ value: 'pending_review', label: '待审核' }, { value: 'approved', label: '待打款' }, { value: 'paid', label: '已打款' }, { value: 'rejected', label: '已驳回' }, { value: 'cancelled', label: '已撤销' }] : [{ value: 'paid', label: '已打款' }, { value: 'approved', label: '待打款' }] }] }
    } else if (url === '/zsjos/advanced-filter-template/visible-list') {
      data = []
    } else if (url === '/zsjos/export-task/page') {
      data = { list: [{ id: 91, taskNo: 'EXP-TEST', exportType: 'withdrawal', status: 'ready', resultFileName: 'withdrawal-test.xlsx' }], total: 1 }
    } else if (url === '/zsjos/export-task/91/download-url') {
      data = '#test-download'
    }
  if (url.includes('/withdrawal/')) {
    if (config.method === 'put') {
      fixture.calls.push({ url, body: JSON.parse(config.data) })
      if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
      if (fixture.fail) throw new Error('提现状态已变化，请刷新后重试')
      data = true
    } else if ((url.endsWith('/page') || url.endsWith('/my-page'))) {
      const params = { ...config.params, ...(config.data ? JSON.parse(config.data) : {}) }
      fixture.queries.push(params)
      const delay = fixture.listDelay[String(params.status)] || 0
      if (delay) await new Promise(resolve => setTimeout(resolve, delay))
      if (fixture.loadError) throw new Error('提现列表加载失败')
      const filtered = (statusTabs ? tabRows : rows).filter(row => !statusTabs || !params.status || row.status === params.status)
      const start = (Number(params.pageNo || 1) - 1) * 10
      data = { list: fixture.empty ? [] : statusTabs ? filtered.slice(start, start + 10) : rows, total: fixture.empty ? 0 : filtered.length }
    } else data = rows[0]
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const permissions = [...(location.search.includes('own') ? ['zsjos:withdrawal:my-query'] : [location.search.includes('admin-scope') ? 'zsjos:withdrawal:admin-query' : 'zsjos:withdrawal:finance-query']), ...(location.search.includes('no-export') ? [] : ['zsjos:export:withdrawal']), ...(location.search.includes('no-query') ? [] : ['zsjos:export:query'])]
if (statusTabs && !location.search.includes('own')) permissions.push('zsjos:withdrawal:payout')
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider locale={zhCN}><App><Routes><Route path="/" element={<WithdrawalPage permissions={permissions}/>}/><Route path="/zsjos/export-task" element={<ExportTaskPage/>}/></Routes></App></ConfigProvider></ThemeProvider></MemoryRouter>)
