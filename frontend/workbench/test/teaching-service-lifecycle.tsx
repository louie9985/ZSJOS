import OverflowToolbar from '../src/components/OverflowToolbar'
import { EditOutlined } from '@ant-design/icons'
// Isolated fixture: production page, panel and forms; every transport is intercepted.
import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import DeliveryClassPage from '../src/pages/DeliveryClassPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import TeachingServiceLifecyclePanel from '../src/components/TeachingServiceLifecyclePanel'
import { MyStudentsPage, StudentContactForm, StudentPlannerOperations } from '../src/pages/RegistrationPages'
import { api, http, type StudentContactContext, type StudentServiceSubject } from '../src/services/api'
import { classGroups } from '../src/services/deliveryClassGroups'
import type { TeachingServiceLifecycle } from '../src/services/teachingServiceLifecycle'
import '../src/styles/index.css'

const query = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: query.has('cards') ? 'inbox' : 'table' }))
const state = { closed: query.has('closed'), version: 3, requests: [] as { url: string; data: Record<string, unknown> }[], fail: query.has('fail'), failTargets: query.has('targetFail'), saved: 0, lifecycleReads: 0 }
const history: TeachingServiceLifecycle['history'] = []
const rows: StudentServiceSubject[] = [1, 2].map(id => ({ personId: 7, personNo: 'TEST-7', name: '测试学员', serviceRelationId: id,
  service: { serviceRelationId: id, courseName: `课程${id}`, classId: 10, className: '原服务班', ownerUserId: 20, ownerUserName: '规划师', status: 'active', acceptanceStatus: 'accepted', owner: true, version: 3 } }))
const current = () => ({ ...rows[0], service: { ...rows[0].service, version: state.version, status: state.closed ? 'closed' : 'active', teachingClosed: state.closed, classId: state.closed ? 99 : 10, className: state.closed ? '异常结业' : '原服务班' } })
const permissions = ['zsjos:student:query-my', 'zsjos:delivery-class:query-managed', 'zsjos:student-contact:submit', 'zsjos:student-contact:first-submit', 'zsjos:student-contact:study-plan-submit', 'zsjos:student-collaborator:assign',
  ...(query.has('denied') ? [] : ['zsjos:student-service:close', 'zsjos:student-service:restore'])]
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/lifecycle')) { state.lifecycleReads++; data = { closed: state.closed, previousClassId: 10, availableActions: query.has('denied') ? [] : [state.closed ? 'RESTORE_SERVICE' : 'CLOSE_SERVICE'], history } }
  else if (url.endsWith('/restore-targets')) {
    if (state.failTargets) { state.failTargets = false; throw Error('班级加载失败') }
    data = query.has('empty') ? [] : [{ id: 10, className: '原服务班', classNo: 'BJ10', homeroomUserName: '规划师' }, { id: 11, className: '新服务班', classNo: 'BJ11', homeroomUserName: '新规划师' }]
  } else if (config.method === 'post' && /\/(close|restore)$/.test(url)) {
    const body = JSON.parse(config.data); state.requests.push({ url, data: body })
    if (state.fail) { state.fail = false; throw Error('审批取消失败，请重试') }
    state.closed = url.endsWith('/close'); state.version++
    history.unshift({ action: state.closed ? 'close' : 'restore', reason: body.reason, operatorName: '操作人', occurredAt: '2026-10-21 15:30:00', targetClassName: state.closed ? '异常结业' : '原服务班', targetOwnerName: '规划师' })
    data = true
  } else throw Error(`Unexpected fixture transport ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
Object.assign(window, { teachingFixture: state })
api.myStudentServices = async p => {
  const list = [current(), rows[1]].filter(row => p.serviceStatus ? row.service.status === p.serviceStatus : row.service.status !== 'closed')
  return { list: structuredClone(list), total: list.length }
}
api.myStudentService = async id => structuredClone(id === 1 ? current() : rows[1])
api.advancedFilterCatalog = async () => ({ fields: [], relativeDateOptions: [] })
api.advancedFilterTemplates = async () => []
api.dictDataByType = async () => []
const classRows: import('../src/services/api').DeliveryClass[] = [
  { id: 99, classNo: 'EX20', className: '异常结业', systemClass: true, classKind: 'EXCEPTION', homeroomUserId: 20, homeroomUserNameSnapshot: '规划师', status: 'SERVING', studentCount: 1, version: 0 },
  { id: 98, classNo: 'PENDING', className: '待分班', systemClass: true, classKind: 'PENDING', status: 'SERVING', studentCount: 0, version: 0 },
]
api.deliveryClasses.page = async () => ({ list: query.has('classes') ? classRows : [], total: query.has('classes') ? classRows.length : 0 })
classGroups.page = async () => ({ list: classRows.map(row => ({ key: `class:${row.id}`, type: 'CLASS' as const, name: row.className, single: row, classCount: 1, serviceCount: row.studentCount || 0, homeroomCount: 1, servingCount: 1, completedCount: 0, examDate: '未设置', projects: [], autoExpand: false })), total: classRows.length, classTotal: classRows.length })
api.deliveryClasses.filterContext = async () => ({ projects: [], homerooms: [], pendingClass: classRows[1] })
api.studentContactRecords = async () => ({ list: [], total: 0 })
const context = (): StudentContactContext => ({ serviceRelationId: 1, version: state.version, teachingClosed: state.closed,
  availableActions: state.closed ? [] : ['FOLLOW_UP'], visibleTabs: ['overview', 'contacts'], firstContactChecklist: [], quickNotes: [],
  acceptanceStatus: 'accepted', deliveryStage: 'supervision', deliveryStages: [], formFields: [], firstContactTimeoutMinutes: 120, studyPlanTimeoutMinutes: 1440 })
api.studentContactContext = async () => context()
api.studentStudyPlan = async (id, body) => { state.requests.push({ url: `/study-plan/${id}`, data: body }); state.saved++; return 1 }
api.studentFirstContact = async (id, body) => { state.requests.push({ url: `/first-contact/${id}`, data: body }); state.saved++; return 1 }
function Panel() {
  const [version, setVersion] = useState(state.version)
  if (query.has('mediaShared')) return <StudentPlannerOperations student={{ ...rows[0], services: [rows[0].service] }} service={rows[0].service} context={{ ...context(), teachingClosed: true, availableActions: ['ASSIGN_CONTENT_DIRECTOR'] }} permissions={permissions} onRefresh={async () => {}}>{actions => <>{actions.map(action => <button key={action.key}>{action.label}</button>)}</>}</StudentPlannerOperations>
  if (query.has('manual')) {
    const value = { ...context(), manualTaskType: 'student_study_plan', availableActions: ['STUDY_PLAN'], deliveryStage: 'study_plan' }
    return <StudentContactForm relationId={1} context={value} onDone={() => {}} />
  }
  return <TeachingServiceLifecyclePanel relationId={1} version={version} permissions={permissions} onChanged={async () => setVersion(state.version)}>
    {query.has('toolbar') ? actions => <div data-testid="unified-toolbar" style={{ width: query.has('narrow') ? 240 : 800, maxWidth: '100%' }}><OverflowToolbar actions={[
      ...['首联', '修改信息', '分配编导'].map((label, index) => ({ key: `existing-${index}`, label, icon: <EditOutlined />, onClick: () => {} })), ...actions,
    ]} /></div> : undefined}
  </TeachingServiceLifecyclePanel>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter>
  {query.has('classes') ? <DeliveryClassPage permissions={[...permissions, 'zsjos:delivery-class:update', 'zsjos:delivery-class:complete']} /> : query.has('page') ? <MyStudentsPage permissions={permissions} /> : <div style={{ maxWidth: 900, margin: '20px auto', padding: 12 }}><Panel /></div>}
</MemoryRouter></App></ThemeProvider>)
