// UTF-8. Isolated browser fixture: synthetic transport, never shared business writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { WithdrawalPage } from '../src/pages/ManagementPages'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const fixture = { calls: [] as Array<{ url: string; body: Record<string, unknown> }>, fail: false, empty: false, loadError: false, delay: 0 }
Object.assign(window, { payoutFixture: fixture })
const rows = [1, 2, 3].map(id => ({ id, withdrawalNo: `TEST-${id}`, status: id === 3 ? 'paid' : 'approved', applicationAmount: 20, accountNameSnapshot: '验收账户', bankNameSnapshot: '验收银行', maskedCardNumber: '****0000', submittedAt: 1789950600000 }))
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.includes('/withdrawal/')) {
    if (config.method === 'put') {
      fixture.calls.push({ url, body: JSON.parse(config.data) })
      if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
      if (fixture.fail) throw new Error('提现状态已变化，请刷新后重试')
      data = true
    } else if (url.endsWith('/page')) {
      if (fixture.loadError) throw new Error('提现列表加载失败')
      data = { list: fixture.empty ? [] : rows, total: fixture.empty ? 0 : 3 }
    } else data = rows[0]
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const permissions = ['zsjos:withdrawal:finance-query', ...(location.search.includes('no-payout') ? [] : ['zsjos:withdrawal:payout'])]
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider locale={zhCN}><App><WithdrawalPage permissions={permissions}/></App></ConfigProvider></ThemeProvider></MemoryRouter>)
