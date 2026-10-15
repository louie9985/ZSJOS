import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RegistrationPoolPage } from '../src/pages/RegistrationPages'
import { api, http, type RegistrationCase } from '../src/services/api'
http.defaults.adapter = async () => { throw new Error('Isolated fixture: no live API') }
const state = new URLSearchParams(location.search).get('state')
const rows: RegistrationCase[] = [1, 2].map(id => ({
 id, orderId: 99, orderItemId: 100 + id, orderNo: 'TEST-99', productName: '履约产品' + id,
 studentName: '验收学员', status: 'pending', statusLabel: '待处理', version: 0,
 orderStatus: 'pending_approval', orderStatusLabel: '待财务审核', assignmentMode: 'class_per_item',
 deliveryEstablished: false, assignable: true, deliveries: [], completable: false,
 completionBlockCode: 'finance_pending', completionBlockReason: '财务审核通过后才能完成报名履约',
 classAssignments: [{ orderItemId: 100 + id, productName: '履约产品' + id, classId: 20, className: '首次班级' }],
 items: [{ id: 80 + id, itemKey: 'checked', itemType: 'checkbox', title: '报名资料确认', sort: 1, checked: true }], routes: []
}))
Object.assign(window, { registrationRows: rows, approveFinance: () => { rows.forEach(row => Object.assign(row, { orderStatus: 'effective', orderStatusLabel: '已生效', completable: true, completionBlockCode: undefined, completionBlockReason: undefined })); window.dispatchEvent(new Event('zsjos:registration-refresh')) } })
api.advancedFilterCatalog = async () => ({ scene: 'registration', fields: [] })
api.advancedFilterTemplates = async () => []
api.deliveryClasses.options = async () => [{ id: 20, classNo: 'CLASS-20', className: '首次班级', systemClass: false, homeroomUserId: 7, homeroomUserName: '测试规划师' }]
api.registrationPoolPage = async params => ({ list: rows.filter(row => params.statusGroup === 'all' || (params.statusGroup === 'processed' ? row.status === 'completed' : row.status !== 'completed')), total: rows.filter(row => row.status !== 'completed').length })
api.registrationCase = async id => structuredClone(rows.find(row => row.id === id)!)
api.updateRegistrationClassAssignments = async id => {
 if (state === 'error') throw new Error('保存分班失败，请重试')
 const row = rows.find(row => row.id === id)!
 Object.assign(row, { deliveryEstablished: true, assignable: false, assignmentBlockReason: '已建立交付服务，变更班级请通过班级管理办理转班', status: 'processing', statusLabel: '处理中', version: 1,
 deliveries: [{ serviceRelationId: 1000 + id, orderItemId: row.orderItemId, status: 'active', classId: 30, className: '当前交付班级', ownerUserId: 7, ownerUserName: '测试规划师', acceptanceStatus: 'accepted' }] })
 return structuredClone(row)
}
api.completeRegistration = async id => { Object.assign(rows.find(row => row.id === id)!, { status: 'completed', statusLabel: '已完成' }); return true }
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><RegistrationPoolPage permissions={state === 'denied' ? ['zsjos:registration:query-pool'] : ['zsjos:registration:query-pool', 'zsjos:registration:update', 'zsjos:registration:complete']} /></BrowserRouter></App></ThemeProvider>)
