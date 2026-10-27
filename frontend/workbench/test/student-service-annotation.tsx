// UTF-8. Production components, isolated synthetic transport; no authenticated requests.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { MyStudentsPage } from '../src/pages/RegistrationPages'
import LeadDetailOverview from '../src/components/LeadDetailOverview'
import StudentServiceAnnotationCard from '../src/components/StudentServiceAnnotationCard'
import { teachingServiceLifecycleApi } from '../src/services/teachingServiceLifecycle'
import { STUDENT_ANNOTATION_READ, STUDENT_ANNOTATION_UPDATE, type StudentServiceAnnotation } from '../src/services/studentServiceAnnotation'
import { api, http, type StudentServiceSubject } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: 'inbox' }))
const state = { mode: params.get('mode') || 'success', saves: [] as { id: number; data: unknown }[], calls: [] as string[], delay: 0 }
Object.assign(window, { annotationFixture: state })
const rows: StudentServiceSubject[] = [1, 2].map(id => ({
  personId: 7, personNo: 'XY-FIXTURE-7', name: '示例学员', serviceRelationId: id,
  service: { serviceRelationId: id, courseName: `课程${id}`, className: `班级${id}`, status: 'active', acceptanceStatus: 'accepted', owner: true, version: 0, ownerUserId: 20 + id, ownerUserName: `规划师${id}` },
}))
const saved = new Map<number, StudentServiceAnnotation>([1, 2].map(id => [id, {
  serviceRelationId: id, version: 0, proxyStudent: null, proxyStudentLabel: null, agentName: null,
  serviceNeeded: null, serviceNeededLabel: null, canEdit: state.mode !== 'readonly',
}]))
http.defaults.adapter = async config => {
  const url = config.url || ''; state.calls.push(url)
  const match = url.match(/\/student\/service\/(\d+)\/annotation(\/options)?$/)
  if (!match) throw Error('Unexpected fixture API: ' + url)
  const id = Number(match[1])
  const response = (data: unknown, code = 0, msg = '') => ({ config, status: 200, statusText: 'OK', headers: {}, data: { code, data, msg } })
  if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay))
  if (match[2]) {
    if (state.mode === 'dict-error') return response(null, 500, '字典读取失败')
    if (state.mode === 'dict-empty') return response([])
    return response([{ value: 'true', label: '是' }, { value: 'false', label: '否' }])
  }
  if (config.method === 'get') {
    if (state.mode === 'load-error') return response(null, 500, '批注读取失败')
    if (state.mode === 'denied') return response(null, 403, '没有读取批注的权限')
    return response(saved.get(id))
  }
  const data = JSON.parse(config.data)
  if (state.mode === 'save-error') return response(null, 500, '保存失败，请重试')
  if (state.mode === 'conflict') return response(null, 1900094001, '批注已被其他人员修改，请读取最新内容后重新填写')
  state.saves.push({ id, data })
  const row: StudentServiceAnnotation = { ...saved.get(id)!, ...data, version: data.version + 1,
    proxyStudentLabel: data.proxyStudent === 'true' ? '是' : data.proxyStudent === 'false' ? '否' : null,
    serviceNeededLabel: data.serviceNeeded === 'true' ? '是' : data.serviceNeeded === 'false' ? '否' : null }
  saved.set(id, row); return response(row)
}
api.myStudentServices = async () => ({ list: rows, total: rows.length })
api.myStudentService = async id => rows.find(row => row.serviceRelationId === id)!
api.advancedFilterCatalog = async () => ({ fields: [], relativeDateOptions: [] })
api.advancedFilterTemplates = async () => []
api.dictDataByType = async () => []
api.deliveryClasses.page = async () => ({ list: [], total: 0 })
api.studentContactContext = async id => ({ serviceRelationId: id, version: 0, availableActions: [], visibleTabs: [], firstContactChecklist: [], quickNotes: [], acceptanceStatus: 'accepted', deliveryStageLabel: '常规督学', currentTask: { id: 100 + id, type: 'student_contact', dueAt: '2026-11-07 18:32:00', overdue: false } } as Awaited<ReturnType<typeof api.studentContactContext>>)
api.studentContactRecords = async () => ({ list: [], total: 0 })
teachingServiceLifecycleApi.detail = async () => ({ closed: false, availableActions: [], history: [] })
const permissions = ['zsjos:student:query-my', ...(state.mode === 'no-permission' ? [] : [STUDENT_ANNOTATION_READ]), ...(state.mode === 'no-update' ? [] : [STUDENT_ANNOTATION_UPDATE])]
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter>{state.mode === 'sales'
  ? <LeadDetailOverview categoryLabel={() => '-'} channelLabel={() => '-'} showFollowUp={false} slots={{ studentAfterFirstContact: <StudentServiceAnnotationCard relationId={1} canUpdate /> }} />
  : <MyStudentsPage permissions={permissions} />}</MemoryRouter></App></ThemeProvider>)
