import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import DeliveryClassPage from '../src/pages/DeliveryClassPage'
import { RegistrationPoolPage, StudentPlannerOperations } from '../src/pages/RegistrationPages'
import TeachingServiceLifecyclePanel from '../src/components/TeachingServiceLifecyclePanel'
import { api, http, type DeliveryClass, type RegistrationCase, type StudentContactContext } from '../src/services/api'
import { classGroups } from '../src/services/deliveryClassGroups'

const query = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: query.has('cards') ? 'inbox' : 'table' }))
const rows: DeliveryClass[] = [
  { id: 1, classNo: 'PENDING', className: '待分班', classKind: 'PENDING', systemClass: true, status: 'SERVING', studentCount: 0, version: 0 },
  { id: 2, classNo: 'US20', className: '未确定考期', classKind: 'UNSCHEDULED', systemClass: true, homeroomUserId: 20, homeroomUserNameSnapshot: '规划师甲', status: 'SERVING', studentCount: 2, version: 0 },
  { id: 3, classNo: 'BJ3', className: '正式班', classKind: 'NORMAL', systemClass: false, homeroomUserId: 20, homeroomUserNameSnapshot: '规划师甲', status: 'SERVING', studentCount: 1, version: 0 },
  { id: 4, classNo: 'EX20', className: '异常结业', classKind: 'EXCEPTION', systemClass: true, homeroomUserId: 20, homeroomUserNameSnapshot: '规划师甲', status: 'SERVING', studentCount: 1, version: 0 },
]
const record: RegistrationCase = { id: 1, orderId: 99, orderItemId: 101, orderNo: 'TEST-99', studentName: '验收学员', productName: '履约产品', status: 'pending', version: 0, assignmentMode: 'class_per_item', assignable: true, completable: false, classAssignments: [{ orderItemId: 101, productName: '履约产品' }], items: [], routes: [] }
const state = { requests: [] as unknown[], fail: query.has('fail') }
Object.assign(window, { unscheduledFixture: state })
http.defaults.adapter = async config => {
  if (config.url?.endsWith('/lifecycle')) return { data: { code: 0, data: { closed: true, previousClassId: 2, availableActions: ['RESTORE_SERVICE'], history: [] } }, status: 200, statusText: 'OK', headers: {}, config }
  if (config.url?.endsWith('/restore-targets')) return { data: { code: 0, data: [ { ...rows[1], homeroomUserName: '规划师甲' } ] }, status: 200, statusText: 'OK', headers: {}, config }
  throw Error('Isolated fixture: unexpected request '+config.url)
}
api.advancedFilterCatalog = async () => ({ fields: [], relativeDateOptions: [] })
api.advancedFilterTemplates = async () => []
api.deliveryClasses.page = async params => { state.requests.push(params); return { list: params.assignableOnly ? rows.filter(r => r.classKind === 'NORMAL' || r.classKind === 'UNSCHEDULED') : rows, total: params.assignableOnly ? 2 : 4 } }
classGroups.page = async () => ({ list: rows.map(row => ({ key: `class:${row.id}`, type: 'CLASS' as const, name: row.className, single: row, classCount: 1, serviceCount: row.studentCount || 0, homeroomCount: 1, servingCount: 1, completedCount: 0, examDate: '未设置', projects: [], autoExpand: false })), total: rows.length, classTotal: rows.length })
api.deliveryClasses.filterContext = async () => ({ projects: [], homerooms: [{ id: 20, name: '规划师甲' }], pendingClass: rows[0] })
api.deliveryClasses.options = async () => {
  if (state.fail) { state.fail = false; throw Error('班级候选加载失败') }
  return rows.filter(r => r.classKind !== 'EXCEPTION').map(r => ({ ...r, homeroomUserName: r.homeroomUserNameSnapshot }))
}
api.registrationPoolPage = async () => ({ list: [record], total: 1 })
api.registrationCase = async () => structuredClone(record)
api.updateRegistrationClassAssignments = async (_id, body) => { state.requests.push(body.assignments); record.classAssignments![0].classId = body.assignments[0].classId; return structuredClone(record) }
const permissions = query.has('denied') ? ['zsjos:registration:query-pool'] : ['zsjos:registration:query-pool', 'zsjos:registration:update', 'zsjos:delivery-class:query-managed', 'zsjos:delivery-class:update', 'zsjos:delivery-class:complete', 'zsjos:delivery-class:direct-transfer', 'zsjos:student-service:restore']
const service = { serviceRelationId: 90, classId: query.has('transfer') ? 99 : 3, className: '正式班', ownerUserId: 20, status: 'active', version: 1 }
const context = { serviceRelationId: 90, version: 1, availableActions: query.has('denied') ? [] : ['DIRECT_TRANSFER'], visibleTabs: [], firstContactChecklist: [], quickNotes: [], deliveryStages: [], formFields: [] } as unknown as StudentContactContext
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter>
  {query.has('classes') ? <DeliveryClassPage permissions={permissions} /> : query.has('transfer') ? <StudentPlannerOperations student={{ personId: 7, name: '验收学员', services: [service] }} service={service} context={context} permissions={permissions} onRefresh={async () => {}}>{actions => <>{actions.map(a => <button key={a.key} onClick={a.onClick}>{a.label}</button>)}</>}</StudentPlannerOperations> : query.has('restore') ? <TeachingServiceLifecyclePanel relationId={90} version={1} permissions={permissions} onChanged={async () => {}} /> : <RegistrationPoolPage permissions={permissions} />}
</MemoryRouter></App></ThemeProvider>)
