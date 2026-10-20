import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import DeliveryClassPage from '../src/pages/DeliveryClassPage'
import { http, type DeliveryClass } from '../src/services/api'

const state = new URLSearchParams(location.search).get('state')
const candidates = [{ id: 20, name: '测试规划师甲', deptName: '测试交付部' }, { id: 21, name: '测试规划师乙', deptName: '测试交付部' }]
const rows: DeliveryClass[] = []
const calls: { method?: string; url?: string; data: Record<string, unknown> }[] = []
let failSave = state === 'error'
Object.assign(window, { classBatch: { rows, calls, allowSave: () => { failSave = false } } })
// Exercise the real typed service and response unwrapping, with all HTTP isolated from business data.
http.defaults.adapter = async config => {
  const payload = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  calls.push({ method: config.method, url: config.url, data: payload })
  let data: unknown
  if (config.url?.endsWith('/filter-context')) data = { projects: [], homerooms: candidates }
  else if (config.url?.endsWith('/page')) data = { list: rows, total: rows.length }
  else if (config.url?.endsWith('/homeroom-candidates')) {
    if (state === 'candidate-error') throw new Error('班主任加载失败，请重试')
    data = state === 'empty' ? [] : candidates
  } else if (config.url?.endsWith('/batch-create')) {
    await new Promise(resolve => setTimeout(resolve, 250))
    if (failSave) return { data: { code: 1001, msg: '班主任不可用，请重新选择' }, status: 200, statusText: 'OK', headers: {}, config }
    data = payload.homeroomUserIds.map((id: number) => {
      const rowId = 100 + rows.length
      rows.push({ id: rowId, classNo: `BJ${rowId}`, className: payload.className, startDate: payload.startDate,
        examScheduleSnapshot: payload.startDate, systemClass: false, homeroomUserId: id,
        homeroomUserNameSnapshot: candidates.find(row => row.id === id)?.name, status: 'SERVING', studentCount: 0, version: 0 })
      return rowId
    })
  } else if (config.method === 'put') {
    Object.assign(rows.find(row => row.id === Number(config.url?.split('/').pop()))!, payload)
    data = true
  } else throw new Error(`Unexpected fixture request: ${config.method} ${config.url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = ['zsjos:delivery-class:query-managed', 'zsjos:delivery-class:update']
if (state !== 'denied') permissions.push('zsjos:delivery-class:create')
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><DeliveryClassPage permissions={permissions} /></BrowserRouter></App></ThemeProvider>)
