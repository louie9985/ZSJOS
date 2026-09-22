// UTF-8. Synthetic transport only; no requests reach business services.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ProductionTicketsPage from '../src/pages/ProductionTicketsPage'
import { http, type ProductionTicket } from '../src/services/api'
import { groupStatuses } from '../src/services/productionTicketPresentation'
const now = Date.now()
const rows: ProductionTicket[] = Array.from({ length: 65 }, (_, index) => ({ id: index + 1, accountId: 1, ticketNo: `PT-DEMO-${index + 1}`, version: 1,
  status: index < 42 ? 'accepted' : index < 54 ? 'public_pool' : 'in_production', availableActions: index < 42 ? ['START_TICKET'] : index < 54 ? ['CLAIM_TICKET'] : ['SUBMIT_TICKET'],
  deadlineAt: now + (index - 2) * 3600000, serverNow: now, sceneName: '剪辑设计', assigneeName: '测试制作人', submitterName: '测试运营', currentRound: 2,
  accounts: [{ accountId: 1, accountNo: 'MA-001', accountName: '测试账号甲', homepageUrl: 'https://example.com/a' }, { accountId: 2, accountNo: 'MA-002', accountName: '测试账号乙', homepageUrl: 'https://example.com/b' }],
  dispatchContext: { accountId: 1, studentName: '测试学员', accountName: '测试账号甲', operatorRemark: '前三秒展示重点，保留字幕完整可读，注意结尾行动引导。' },
  formFields: [{ key: 'theme', label: '主题', type: 'text' }, { key: 'count', label: '数量', type: 'number' }, { key: 'request', label: '制作要求', type: 'textarea' }, { key: 'ref', label: '参考链接', type: 'text' }],
  formValues: { theme: '教学短视频', count: 3, request: '保留清晰声音，画面节奏与旁白对应。'.repeat(12), ref: 'https://example.com/reference' },
  timeline: [{ operation: 'create', operatorName: '测试运营', operatedAt: '2026-09-21T10:00:00', roundNo: 1, toStatus: 'PENDING_ACCEPT' }, { operation: 'production-return', operatorName: '测试运营', operatedAt: '2026-09-22T10:00:00', reason: '补充结尾引导', roundNo: 1, toStatus: 'IN_PROGRESS' }],
}))
http.defaults.adapter = async config => {
  const url = config.url || '', p = config.params || {}
  let data: unknown
  if (config.method === 'post') {
    const id = Number(url.split('/').at(-2)), row = rows.find(item => item.id === id)!
    const command = url.split('/').at(-1)
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    if ((p.version || body?.version) !== row.version) throw new Error('工单状态已改变，请刷新')
    if (command === 'claim') { if (location.search.includes('conflict')) { row.status = 'completed'; row.availableActions = []; throw new Error('工单已被其他员工抢走') }; row.status = 'accepted'; row.availableActions = ['START_TICKET'] }
    if (command === 'start-production' || command === 'reaccept') { row.status = command === 'reaccept' ? 'accepted' : 'in_production'; row.availableActions = [command === 'reaccept' ? 'START_TICKET' : 'SUBMIT_TICKET'] }
    if (command === 'submit') { row.status = 'submitted'; row.availableActions = ['APPROVE_TICKET', 'REJECT_TICKET']; row.dispatchContext = { ...row.dispatchContext!, completionUrl: body.completionUrl, completionRemark: body.remark } }
    if (command === 'approve') { row.status = 'completed'; row.availableActions = [] }
    if (command === 'reject') { row.status = 'rejected'; row.availableActions = ['REACCEPT_TICKET']; row.timeline!.push({ operation: 'production-return', toStatus: 'IN_PROGRESS', reason: p.reason, operatedAt: '2026-09-22T12:00:00' }) }
    row.version++; data = true
  } else if (url.endsWith('/get')) { if (location.search.includes('denied')) throw new Error('无权查看该工单'); data = rows.find(row => row.id === Number(p.id)); if (Number(p.id) === 2) await new Promise(resolve => setTimeout(resolve, 350)) }
  else if (url.endsWith('/page')) {
    let list = rows.filter(row => url.includes('/pool/') ? row.status === 'public_pool' : row.status !== 'public_pool')
    if (p.statusGroup && groupStatuses[p.statusGroup]) list = list.filter(row => groupStatuses[p.statusGroup].includes(row.status))
    if (p.status) list = list.filter(row => row.status === p.status)
    if (p.keyword) list = list.filter(row => row.ticketNo.includes(p.keyword))
    if (p.deadlineFrom) list = list.filter(row => row.deadlineAt! >= Date.parse(p.deadlineFrom.replace(' ', 'T') + '+08:00'))
    if (p.deadlineTo) list = list.filter(row => row.deadlineAt! <= Date.parse(p.deadlineTo.replace(' ', 'T') + '+08:00'))
    data = { list: list.slice((p.pageNo - 1) * p.pageSize, p.pageNo * p.pageSize), total: list.length }
  } else throw new Error(`Unexpected endpoint ${url}`)
  return { data: { code: 0, data: structuredClone(data) }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter initialEntries={[`/zsjos/production-tickets${location.search.includes('deep') ? '?ticketId=65' : ''}`]}><div style={{ height: '100vh' }}><ProductionTicketsPage permissions={['zsjos:production-ticket:accept', 'zsjos:production-ticket:pool-query', 'zsjos:production-ticket:claim']} /></div></MemoryRouter></App></ThemeProvider>)
