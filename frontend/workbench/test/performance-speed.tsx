// UTF-8. Explicit isolated fixture: no application or production transport is used.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesPerformancePage from '../src/pages/SalesPerformancePage'
import { http } from '../src/services/api'

let now = Date.now(), mode = 'ok', update = () => {}, slow = false
const originalNow = Date.now
Date.now = () => now
const counts: Record<string, number> = {}
const asOf = '2026-10-08T12:00:00'
const groups = [{ key: 'fixture', label: '验证分类', amount: 1280, count: 1, share: 1 }]
function metric(key: string, amount = 1280) { return { key, label: key, start: '2026-10-01T00:00:00', end: asOf, amount, orders: 1, converted: 1, denominator: 2, rate: .5, average: amount, averageAmount: amount, averageOrders: 1 } }
http.defaults.adapter = async config => {
  const endpoint = (config.url ?? '').split('/').pop()!
  counts[endpoint] = (counts[endpoint] ?? 0) + 1; update()
  const capturedMode = mode, scope = config.params?.scopeId
  if (slow && endpoint === 'analysis') await new Promise(resolve => setTimeout(resolve, scope === 1 ? 1800 : 100))
  if (capturedMode === 'error' && endpoint !== 'tree') throw new Error('模拟网络失败')
  let data: unknown
  const amount = scope === 2 ? 2560 : 1280
  if (endpoint === 'tree') data = [{ key: 'USER:1', title: '验证销售甲', scopeType: 'USER', scopeId: 1, selectable: true }, { key: 'USER:2', title: '验证销售乙', scopeType: 'USER', scopeId: 2, selectable: true }]
  else if (endpoint === 'overview') data = { asOf, attributionAvailableSince: asOf, targets: [], performance: [metric('本月', amount)], conversion: [metric('month', amount)], pending: {}, missingAttributionOrders: 0, missingAttributionAmount: 0, canDetail: true }
  else if (endpoint === 'history') data = [{ month: 10, amount, previousAmount: 1000 }]
  else if (endpoint === 'analysis') data = { asOf, start: '2026-10-01', end: '2026-10-08', averages: [metric('整体', amount)], trend: [metric('2026-10-08', amount)], sources: groups, products: groups, contributors: [], contributionMetrics: [], averageTrends: {}, target: null }
  else if (endpoint === 'lead-workload') data = { asOf, start: '2026-10-08', end: '2026-10-08', workload: { assigned: 2, missed: 0, received: 2, valid: 1, followUps: 1 }, categories: groups, stages: groups, followUp: groups, categoryTrend: [] }
  else if (endpoint === 'lead-calendar') data = { asOf, start: '2026-10-01', end: '2026-10-31', calendar: [{ date: '2026-10-08', received: 2, valid: 1, invalid: 0, pending: 1, overdue: 0, ended: 0, lateCompleted: 0, onTime: 1, dueCount: 1, unknown: 0 }], funnel: groups }
  else if (endpoint === 'details') data = { list: [], total: 0 }
  else data = []
  return { data: capturedMode === 'denied' && endpoint !== 'tree' ? { code: 1900090001, msg: '无权查看该业绩范围' } : { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const initialPermissions = ['zsjos:sales-performance:query', 'zsjos:sales-performance:detail', 'zsjos:sales-performance:department']
function Fixture() {
  const [, render] = useState(0), [permissions, setPermissions] = useState(initialPermissions)
  update = () => render(x => x + 1)
  return <><Space wrap><Button onClick={() => { now += 599000; update() }}>前进599秒</Button><Button onClick={() => { now += 1000; update() }}>前进1秒</Button>
    <Button onClick={() => { mode = 'error' }}>模拟失败</Button><Button onClick={() => { mode = 'denied' }}>模拟无权限</Button><Button onClick={() => { mode = 'ok' }}>恢复正常</Button>
    <Button onClick={() => setPermissions(p => p.length === initialPermissions.length ? p.slice(0, 2) : initialPermissions)}>变更权限上下文</Button><Button onClick={() => { slow = !slow }}>切换慢请求</Button></Space>
    <pre aria-label="请求计数" style={{ overflowX: 'auto' }}>{JSON.stringify(counts)}</pre><SalesPerformancePage permissions={permissions}/></>
}
void originalNow
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><Fixture/></App></ThemeProvider></BrowserRouter>)
