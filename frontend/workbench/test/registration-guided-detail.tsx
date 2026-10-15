import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RegistrationPoolPage } from '../src/pages/RegistrationPages'
import { api, http, type RegistrationCase } from '../src/services/api'
http.defaults.adapter = async () => { throw new Error('Isolated fixture: no live API') }
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
let failItem = true
let failDetail = true
const state = new URLSearchParams(location.search).get('state')
const rows: RegistrationCase[] = [1, 2].map(id => ({
 id, orderId: 99, orderItemId: 100 + id, orderNo: 'TEST-99', productName: '履约产品' + id,
 leadNo: 'KZ-TEST-' + id, studentMobile: '未提供', studentName: '验收学员' + id, status: 'pending', statusLabel: '待处理', version: 0,
 orderStatus: 'pending_approval', orderStatusLabel: '待财务审核', assignmentMode: 'class_per_item',
 deliveryEstablished: false, assignable: true, deliveries: [], completable: false,
 completionBlockCode: 'finance_pending', completionBlockReason: '财务审核通过后才能完成报名履约',
 classAssignments: [{ orderItemId: 100 + id, productName: '履约产品' + id, classId: state === 'unassigned' ? undefined : 20, className: '首次班级' }],
 items: [{ id: 900 + id, itemKey: 'file', itemType: 'attachment', title: '报名材料', sort: 2, attachmentRequired: true, attachments: [] }, { id: 80 + id, itemKey: 'checked', itemType: 'checkbox', title: '报名资料确认', sort: 1, checked: false }], routes: []
}))
Object.assign(window, { registrationRows: rows, approveFinance: () => { rows.forEach(row => Object.assign(row, { orderStatus: 'effective', orderStatusLabel: '已生效', completable: true, completionBlockCode: undefined, completionBlockReason: undefined })); window.dispatchEvent(new Event('zsjos:registration-refresh')) } })
api.advancedFilterCatalog = async () => ({ scene: 'registration', fields: [] })
api.advancedFilterTemplates = async () => []
api.deliveryClasses.options = async () => [{ id: 20, classNo: 'CLASS-20', className: '首次班级', systemClass: false, homeroomUserId: 7, homeroomUserName: '测试规划师' }, { id: 21, classNo: 'WAIT', className: '待分班', systemClass: true }]
api.registrationPoolPage = async params => ({ list: rows.filter(row => params.statusGroup === 'all' || (params.statusGroup === 'processed' ? row.status === 'completed' : row.status !== 'completed')), total: rows.filter(row => row.status !== 'completed').length })
api.registrationCase = async id => {
 if (state === 'detail-error' && failDetail) { failDetail = false; throw new Error('详情加载失败，请重试') }
 if (state === 'slow-detail' && id === 1) await delay(600)
 return structuredClone(rows.find(row => row.id === id)!)
}
api.updateRegistrationClassAssignments = async (id, data) => {
 if (state === 'slow-save') await delay(700)
 if (state === 'error') throw new Error('保存分班失败，请重试')
 const row = rows.find(row => row.id === id)!
 row.classAssignments![0].classId = data.assignments[0].classId
 Object.assign(row, { deliveryEstablished: true, assignable: false, assignmentBlockReason: '已建立交付服务，变更班级请通过班级管理办理转班', status: 'processing', statusLabel: '处理中', version: 1,
 deliveries: [{ serviceRelationId: 1000 + id, orderItemId: row.orderItemId, status: 'active', classId: 30, className: '当前交付班级', ownerUserId: 7, ownerUserName: '测试规划师', acceptanceStatus: 'accepted' }] })
 return structuredClone(row)
}
api.completeRegistration = async id => { Object.assign(rows.find(row => row.id === id)!, { status: 'completed', statusLabel: '已完成' }); return true }

api.updateRegistrationItem = async (id, itemId, data) => {
 if (state === 'slow-item') await delay(700)
 if (state === 'item-error' && failItem) { failItem = false; throw new Error('清单保存失败，请重试') }
 const row = rows.find(row => row.id === id)!
 Object.assign(row.items!.find(item => item.id === itemId)!, { checked: data.checked, checkedByUserName: '测试操作员', checkedAt: '2026-10-15 16:00:00' })
 row.version++
 return structuredClone(row)
}
api.uploadRegistrationAttachment = async (id, itemId, file) => {
 const row = rows.find(row => row.id === id)!
 const attachment = { id: 1, infraFileId: 1, fileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', originalName: file.name, contentType: 'image/png', fileSize: 5, uploadedByUserId: 1 }
 row.items!.find(item => item.id === itemId)!.attachments = [attachment]
 row.version++
 return attachment
}
api.deleteRegistrationAttachment = async (id, itemId) => { const row=rows.find(row=>row.id===id)!; row.items!.find(item=>item.id===itemId)!.attachments=[]; row.version++; return structuredClone(row) }
api.closeRegistration = async id => { Object.assign(rows.find(row=>row.id===id)!, {status:'ended',statusLabel:'已结束',cancelReason:'无需交付',cancelledAt:'2026-10-15 16:00:00'}); return true }
api.registrationRouteCandidates = async () => [{ id:7,nickname:'测试规划师' }]
api.updateRegistrationRoutes = async (id, data) => {const row=rows.find(row=>row.id===id)!; row.routes!.forEach(route=>Object.assign(route,data.routes.find(value=>value.routeId===route.id))); row.deliveryEstablished=true; row.assignable=false; return structuredClone(row)}
if (state === 'ready') rows.forEach(row=>Object.assign(row,{completable:true,completionBlockReason:undefined,orderStatus:'effective'}))
if (['completed','ended','cancelled'].includes(state || '')) rows.forEach(row=>Object.assign(row,{status:state,statusLabel:state==='completed'?'已完成':state==='ended'?'已结束':'已关闭',completedAt:'2026-10-15 16:00:00',cancelledAt:'2026-10-15 16:00:00',cancelReason:'测试原因'}))
if (state === 'legacy') rows.forEach(row=>Object.assign(row,{assignmentMode:'legacy_planner',routes:[{id:1,optionKey:'test',departmentId:1,departmentName:'测试部门',assigneeType:'study_planner',assigneeTypeLabel:'学习规划师',sort:1,selected:false}]}))
if (state === 'long') rows.forEach(row=>{row.studentName='长姓名显示验证';row.classAssignments![0].productName='多规格报名产品长名称验证'.repeat(6);row.items!.push(...Array.from({length:18},(_,i)=>({id:2000+i,itemKey:'check'+i,itemType:'checkbox',title:'履约核对事项 '+(i+1),sort:i+3})))})
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><div style={{height:"100vh",padding:12}}><RegistrationPoolPage permissions={state === 'denied' ? ['zsjos:registration:query-pool'] : ['zsjos:registration:query-pool', 'zsjos:registration:update', 'zsjos:registration:complete', 'zsjos:registration:close']} /></div></BrowserRouter></App></ThemeProvider>)
