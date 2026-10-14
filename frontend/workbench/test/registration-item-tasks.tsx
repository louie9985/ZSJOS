import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RegistrationPoolPage } from '../src/pages/RegistrationPages'
import { api, http, type RegistrationCase } from '../src/services/api'

const scenario = new URLSearchParams(location.search).get('state')
http.defaults.adapter = async () => { throw new Error('Isolated fixture: no live API') }
const requests: unknown[] = []
Object.assign(window, { poolRequests: requests })
const statuses = ['pending', 'processing', 'completed', 'cancelled']
const labels = ['待处理', '处理中', '已完成', '已取消']
const rows: RegistrationCase[] = Array.from({ length: 3 }, (_, i) => ({
  id: i + 1, orderId: 99, orderNo: `TEST-99`, studentName: `验收学员`, orderItemId: 101+i, productName: `履约产品${i+1}`,
  status: "pending", statusLabel: "待处理", version: 0, orderStatus: 'effective',
  assignmentMode: 'class_per_item', classAssignments: [], completable: true, items: [], routes: [],
}))
api.advancedFilterCatalog = async () => ({ scene: 'registration', fields: [] })
api.advancedFilterTemplates = async () => []
api.deliveryClasses.options = async () => []
let attempts = 0
api.registrationPoolPage = async params => {
  requests.push(params)
  await new Promise(resolve => setTimeout(resolve, scenario === 'slow' && params.statusGroup === 'processed' ? 500 : 40))
  if (scenario === 'error' && attempts++ === 0) throw new Error('履约列表暂不可用')
  const filtered = scenario === 'empty' ? [] : rows.filter(row =>
    (params.statusGroup === 'all' || (params.statusGroup === 'pending' ? ['pending', 'processing'] : ['completed', 'cancelled', 'ended']).includes(row.status))
    && (!params.keyword || row.orderNo?.includes(params.keyword)))
  return { list: filtered.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize), total: filtered.length }
}
api.registrationCase = async id => ({ ...rows.find(row => row.id === id)! })
api.completeRegistration = async id => { Object.assign(rows.find(row => row.id === id)!, { status: 'completed', statusLabel: '已完成' }); return true }
api.closeRegistration = async id => { Object.assign(rows.find(row => row.id === id)!, { status: 'ended', statusLabel: '已结束（无需交付）', completable: false }); return true }
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><RegistrationPoolPage permissions={['zsjos:registration:query-pool', 'zsjos:registration:complete', 'zsjos:registration:close']} /></BrowserRouter></App></ThemeProvider>)
