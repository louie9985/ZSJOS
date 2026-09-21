// UTF-8. Isolated browser fixture: all HTTP requests are intercepted, no business mutations.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import SalesOrderApprovalPage from '../src/pages/SalesOrderApprovalPage'
import { http } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const center = params.get('center') === 'registration' ? 'registration' : 'finance'
const taskDefinitionKey = center === 'finance' ? 'financeReview' : 'registrationReview'
const required = params.get('mode') === 'missing' ? undefined : params.get('mode') === 'old'
let failed = false
const order = { id: 1, orderNo: 'TEST-ORDER', orderType: 'first_purchase', status: 'pending_approval',
  studentName: '测试学员', totalAmount: 100, items: [], paymentVouchers: [], attachments: [], version: 1, approvalRoundVersion: 1,
  currentApprovalRoundId: 1, taskId: 'test-task', taskDefinitionKey, approvalReasonRequired: required,
  registrationApproval: { status: 'pending' }, financeApproval: { status: 'pending' } }
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/filter-profile')) data = { centers: [{ key: center, label: center === 'finance' ? '财务中心' : '报名履约中心' }], groups: [{ key: 'pending', label: '待处理', options: [{ key: 'all', label: '全部' }] }] }
  else if (url.includes('/inbox-')) data = { list: [order], total: 1, hasMore: false }
  else if (url.endsWith('/task-target')) { data = { orderId: 1, taskId: 'test-task', taskDefinitionKey, center, approvalReasonRequired: required ?? false } }
  else if (url.endsWith('/catalog')) data = { fields: [], operators: [] }
  else if (/\/(approve|reject)$/.test(url)) {
    const body = JSON.parse(config.data)
    document.getElementById('result')!.textContent = `${url.endsWith('/approve') ? '通过' : '驳回'}提交：reason=${JSON.stringify(body.reason)}`
    if (params.has('fail') && !failed) { failed = true; throw new Error('测试提交失败，请重试') }
    data = true
  } else if (url.endsWith('/sales-order/1')) data = order
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><div id="result" role="status">测试请求尚未提交</div><MemoryRouter initialEntries={[params.has('deep') ? '/?orderId=1&taskId=test-task' : '/']}><SalesOrderApprovalPage permissions={['zsjos:sales-order:review']} /></MemoryRouter></App></ThemeProvider>)
