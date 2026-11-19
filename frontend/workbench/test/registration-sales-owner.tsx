import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RegistrationPoolPage } from '../src/pages/RegistrationPages'
import { api, http, type RegistrationCase } from '../src/services/api'
http.defaults.adapter = async () => { throw new Error('isolated fixture') }
const row: RegistrationCase = { id: 1, orderId: 2, orderItemId: 3, orderNo: 'TEST-1', productName: '合成课程', studentName: '合成学员', status: 'pending', statusLabel: '待处理', version: 0, orderStatus: 'effective', orderStatusLabel: '已生效', assignmentMode: 'sales_owner', deliveryEstablished: false, assignable: false, deliveries: [], completable: true, items: [{ id: 9, itemKey: 'checked', itemType: 'checkbox', title: '确认资料', sort: 1, checked: true }], routes: [] }
Object.assign(window, { fixtureState: row })
api.advancedFilterCatalog = async () => ({ scene: 'registration', fields: [] })
api.advancedFilterTemplates = async () => []
api.registrationPoolPage = async () => ({ list: [structuredClone(row)], total: 1 })
api.registrationCase = async () => structuredClone(row)
api.completeRegistration = async () => { row.status = 'completed'; row.statusLabel = '已完成'; row.deliveryEstablished = true; return true }
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><RegistrationPoolPage permissions={['zsjos:registration:query-pool', 'zsjos:registration:update', 'zsjos:registration:complete']} /></BrowserRouter></App></ThemeProvider>)
