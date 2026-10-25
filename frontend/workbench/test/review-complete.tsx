// UTF-8. Actual student page and group picker; every API is isolated from live data.
import { createRoot } from 'react-dom/client'
import { App, Button } from 'antd'
import { MemoryRouter, useLocation } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { WorkbenchPageNavigation, useWorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import { MyStudentsPage } from '../src/pages/RegistrationPages'
import { ClassGroupOrganizer } from '../src/components/DeliveryClassGroupDialogs'
import { api, http, type ManagedLead, type StudentServiceSubject } from '../src/services/api'
import { studentArchiveApi } from '../src/services/studentArchive'
import { studentArchiveExtraApi } from '../src/services/studentArchiveExtra'
import { teachingServiceLifecycleApi } from '../src/services/teachingServiceLifecycle'
import { classGroups } from '../src/services/deliveryClassGroups'
import ApprovalNavigationMenu from '../src/layouts/ApprovalNavigationMenu'
import '../src/styles/index.css'

const query = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: 'inbox' }))
const state = { leadReads: [] as number[], studentReads: [] as number[], saves: 0, holdSave: false,
  releaseSave: () => {}, candidateFail: false, moved: [] as number[] }
Object.assign(window, { completeReviewFixture: state })
http.defaults.adapter = async () => { throw Error('No live requests in review fixture') }
const rows: StudentServiceSubject[] = [1, 2].map(id => ({ personId: id, personNo: `XY-${id}`, name: `合成学员${id}`, serviceRelationId: id,
  service: { serviceRelationId: id, leadId: 100 + id, courseName: `课程${id}`, status: 'active', acceptanceStatus: 'accepted', owner: true, ownerUserId: 9, ownerUserName: '合成规划师', version: 0 } }))
api.myStudentServices = async () => ({ list: structuredClone(rows), total: rows.length })
api.myStudentService = async id => { state.studentReads.push(id); return structuredClone(rows.find(row => row.serviceRelationId === id)!) }
api.managedLead = async id => { state.leadReads.push(id); return { id, leadNo: `KZ-${id}`, submittedName: `合成客资${id}`, visibleTabs: [], availableActions: [] } as ManagedLead }
api.dictDataByType = async () => []
api.advancedFilterCatalog = async () => ({ fields: [], relativeDateOptions: [] })
api.advancedFilterTemplates = async () => []
api.studentContactRecords = async () => ({ list: [], total: 0 })
api.studentContactContext = async id => ({ serviceRelationId: id, version: 0, availableActions: [], visibleTabs: ['student-info'], firstContactChecklist: [], quickNotes: [], formFields: [], deliveryStages: [] } as Awaited<ReturnType<typeof api.studentContactContext>>)
teachingServiceLifecycleApi.detail = async () => ({ closed: false, availableActions: [], history: [] })
const values: Record<number, Record<string, string>> = { 1: { job: '原职业1' }, 2: { job: '原职业2' } }
studentArchiveApi.detail = async id => ({ personId: id, serviceRelationId: id, productName: `课程${id}`, revision: state.saves, canMaintain: true, history: [],
  information: { id, status: 'SUBMITTED', configVersion: 1, canReadSensitive: true, canExport: false, options: {}, images: [], values: values[id],
    fields: [{ key: 'job', label: '从事职业', type: 'text', group: 'basic', enabled: true, required: false, sensitive: false, sort: 1 }] } })
studentArchiveApi.edit = async id => ({ revision: state.saves, values: values[id], options: {}, images: [] })
studentArchiveApi.areas = async () => []
studentArchiveApi.save = async (id, _version, changes) => {
  if (state.holdSave) await new Promise<void>(resolve => { state.releaseSave = resolve })
  Object.assign(values[id], changes); return ++state.saves
}
studentArchiveExtraApi.detail = async () => ({ revision: 0, visibilityRevision: 0, canMaintain: true, canReadAttachments: true, canConfigureVisibility: false,
  fields: [{ key: 'channel', label: '对接渠道', type: 'text', allowedExtensions: [] }], values: { channel: { text: '原渠道', links: [], attachmentIds: [] } }, attachments: [], history: [], educationVisibility: {} })
studentArchiveExtraApi.save = async () => ++state.saves
classGroups.candidates = async (_keyword, page = 1) => {
  if (state.candidateFail) { state.candidateFail = false; throw new Error('候选翻页失败') }
  return { total: 24, list: Array.from({ length: 12 }, (_, i) => ({ id: (page - 1) * 12 + i + 1, classNo: `BJ-${(page - 1) * 12 + i + 1}`, className: `小班${(page - 1) * 12 + i + 1}`, status: 'SERVING', systemClass: false, version: 0 })) }
}
classGroups.move = async selected => { state.moved = selected.map(row => row.id); return true }
function Content() {
  const location = useLocation(), navigation = useWorkbenchPageNavigation()
  if (query.has('navigation')) return <div style={{ width: 80 }}><ApprovalNavigationMenu menus={[]} activePath="" mode="inline" inlineCollapsed items={[{ key: 'parent', label: 'Parent', children: [{ key: 'leaf', label: 'Leaf' }] }]} /></div>
  if (query.has('groups')) return <ClassGroupOrganizer open group={{ id: 1, name: '目标组', deptId: 1, version: 0, canCreate: true, canUpdate: true, canDelete: true }} onClose={() => {}} onSaved={() => {}} />
  if (location.pathname === '/outside') return <div>已离开学员页</div>
  return <><Button onClick={() => void navigation?.open('/outside')}>离开学员页</Button><MyStudentsPage permissions={['zsjos:student:query-my', 'zsjos:student-archive:read']} /></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/my-students']}><WorkbenchPageNavigation canOpen={() => true} canRetain={() => false}><Content /></WorkbenchPageNavigation></MemoryRouter></App></ThemeProvider>)
