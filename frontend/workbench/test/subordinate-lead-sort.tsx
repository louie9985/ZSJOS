// UTF-8. Isolated HTTP adapter for the real subordinate page; no live requests.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App, Button } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SubordinateSalesPage from '../src/pages/SubordinateSalesPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'

let fail = false
const sales = { userId: 5, name: '排序测试员工', username: 'synthetic', mobile: '', presence: 'offline', accepting: false, accountStatus: 0, canReceiveNewLeads: false, todayPendingCount: 0, validLeadCount: 0, convertedLeadCount: 0, effectiveOrderAmount: 0, effectiveOrderCount: 0, suspendedLeadCount: 0, firstFollowTimeoutCount: 0, categoryCounts: [] }
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (/\/5\/leads/.test(url)) {
    const params = config.method === 'post' ? JSON.parse(config.data) : config.params
    document.getElementById('request')!.textContent = JSON.stringify(params)
    if (fail) { fail = false; throw new Error('模拟排序加载失败') }
    const field = params.sortField || 'lastActivityAt'
    const rows = Array.from({ length: 42 }, (_, i) => ({ id: i + 1, leadNo: `SORT-${i + 1}`, submittedName: `测试${String(i + 1).padStart(2, '0')}`, submittedMobile: `1380000${String(i + 1).padStart(4, '0')}`, submittedAt: 1700000000000 + i * 60000, lastActivityAt: 1700000000000 + i * 60000, nextFollowUpAt: 1700000000000 + i * 60000, currentAssignmentFirstFollowUpDeadlineAt: 1700000000000 + i * 60000, status: 'submitted', assignmentStatus: 'assigned' }))
    rows.sort((a, b) => String(a[field as keyof typeof a]).localeCompare(String(b[field as keyof typeof b])) * (params.sortOrder === 'ascend' ? 1 : -1))
    const offset = (params.pageNo - 1) * params.pageSize
    data = { list: rows.slice(offset, offset + params.pageSize), total: rows.length }
  } else if (url === '/zsjos/subordinate-sales/page') data = { list: [sales], total: 1 }
  else if (url.endsWith('/overview')) data = sales
  else if (url.includes('catalog')) data = { fields: [{ fieldKey: 'subordinate.accountStatus', label: '账号状态', valueType: 'select', operators: ['eq'], options: [{ value: '0', label: '启用' }, { value: '1', label: '停用' }] }], relativeDateOptions: [] }
  else if (url.endsWith('/page')) data = { list: [], total: 0 }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App>
  <Button onClick={() => { fail = true }}>下次查询失败</Button><pre id="request" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }} />
  <div style={{ height: 'calc(100vh - 110px)' }}><SubordinateSalesPage permissions={[]} /></div>
</App></ThemeProvider></MemoryRouter>)
