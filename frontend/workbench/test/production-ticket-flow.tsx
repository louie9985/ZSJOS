// UTF-8. Isolated browser fixture; requests never reach business services.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { ProductionTicketsPage, ProductionTicketAssignmentHost } from '../src/pages/MediaFeaturePage'
import { http, type ProductionTicket } from '../src/services/api'

const permissions = ['zsjos:production-ticket:accept', 'zsjos:production-ticket:claim', 'zsjos:production-ticket:pool-query']
const pending = location.search.includes('assignment')
const ticket = { id: 92, ticketNo: 'PT-TEST-92', status: pending ? 'pending_accept' : 'in_production', version: 1,
  availableActions: pending ? ['ACCEPT_TICKET'] : ['SUBMIT_TICKET'],
  formFields: [{ key: 'reference_work_link', label: '参考作品链接', type: 'text' }],
  formValues: { reference_work_link: 'https://example.com/reference' },
  requestAttachments: [{ id: 12, name: '拍摄要求.txt', type: 'text/plain', url: 'https://example.com/test.txt' }],
  dispatchContext: { accountId: 1, operatorRemark: '运营要求', accountName: '测试账号' },
} as ProductionTicket
let claimed = false
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (config.method === 'post') {
    if (url.endsWith('/accept')) { ticket.status = 'accepted'; ticket.availableActions = ['START_TICKET'] }
    else if (url.endsWith('/claim')) { claimed = true; ticket.status = 'accepted'; ticket.availableActions = ['START_TICKET'] }
    else if (url.endsWith('/submit')) {
      const body = JSON.parse(config.data)
      if ('videoSentToOperator' in body || 'attachmentId' in body) throw new Error('Unexpected legacy submission')
      ticket.dispatchContext = { ...ticket.dispatchContext!, completionUrl: body.completionUrl, completionRemark: body.remark }
      ticket.status = 'submitted'; ticket.availableActions = ['APPROVE_TICKET']
    }
    ticket.version++; data = true
  } else if (url.endsWith('/assignment/my-pending')) data = ticket.status === 'pending_accept' ? [ticket] : []
  else if (url.endsWith('/pool/page')) data = { list: claimed ? [] : [{ ...ticket, status: 'public_pool', availableActions: ['CLAIM_TICKET'] }], total: claimed ? 0 : 1 }
  else if (url.endsWith('/get')) data = ticket
  else if (url.endsWith('/page')) data = { list: location.search.includes('deep') ? [] : [ticket], total: 1 }
  else throw new Error(`Unexpected fixture endpoint: ${url}`)
  return { data: { code: 0, data: structuredClone(data) }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/production-tickets?ticketId=92']}><ProductionTicketsPage permissions={permissions} />{pending && <ProductionTicketAssignmentHost permissions={permissions} />}</MemoryRouter></App></ThemeProvider>)
