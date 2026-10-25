import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { MyStudentsPage } from '../src/pages/RegistrationPages'
import { teachingServiceLifecycleApi } from '../src/services/teachingServiceLifecycle'
import { api, http, type StudentServiceSubject } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: params.get('layout') || 'inbox' }))
const long = params.has('long')
if (params.has('tasks')) teachingServiceLifecycleApi.detail = async () => ({ closed: false, availableActions: [], history: [] })
const rows: StudentServiceSubject[] = [1, 2].map(id => ({
  personId: 7, personNo: 'XY-FIXTURE-7', name: '示例学员',
  serviceRelationId: id,
  service: {
    serviceRelationId: id, courseName: `课程${id}${long ? '大健康轻创技能提升与职业发展'.repeat(3) : ''}`,
    className: `班级${id}${long ? '技能提升2026秋班（班主任：示例老师）'.repeat(3) : ''}`,
    status: 'active', acceptanceStatus: id === 1 ? 'pending' : 'accepted', owner: true, version: 0,
    ownerUserId: 20 + id, ownerUserName: `规划师${id}${long ? '服务负责人'.repeat(8) : ''}`,
    contentDirectorUserId: 30 + id, contentDirectorUserName: `编导${id}`,
    operatorUserId: 40 + id,
  },
}))
const state = { fail: false, delay: 0, completed: false, invalidate: () => window.dispatchEvent(new Event('zsjos-workbench-task-invalidated')) }
Object.assign(window, { sidebarFixture: state })
http.defaults.adapter = async () => { throw Error('No live API in fixture') }
const projected = () => rows.map(row => ({ ...structuredClone(row), pendingTasks: params.has('tasks') && row.serviceRelationId === 2 && !state.completed ? [
  { id: 51, taskType: 'student_contact', title: '学员跟进', dueAt: Date.now() - 86400000, overdue: true },
  { id: 52, taskType: 'student_first_contact_assistance', title: '首联协助', overdue: false },
] : [] }))
api.myStudentServices = async query => { if (params.has('tasks') && !query.pendingTasksFirst) throw Error('未请求待办置顶'); return { list: projected().sort((a,b) => Number(Boolean(b.pendingTasks.length)) - Number(Boolean(a.pendingTasks.length))), total: rows.length } }
api.myStudentService = async id => {
  if (id === 1 && state.delay) await new Promise(resolve => setTimeout(resolve, state.delay))
  if (state.fail) throw Error('服务读取失败')
  return projected().find(row => row.serviceRelationId === id)!
}
api.advancedFilterCatalog = async () => ({ fields: [], relativeDateOptions: [] })
api.advancedFilterTemplates = async () => []
api.dictDataByType = async () => []
api.deliveryClasses.page = async () => ({ list: [], total: 0 })
api.studentContactContext = async id => ({
  serviceRelationId: id, version: 0, availableActions: [], visibleTabs: [], firstContactChecklist: [], quickNotes: [],
  acceptanceStatus: rows[id - 1].service.acceptanceStatus, deliveryStageLabel: '常规督学',
  currentTask: { id: 100 + id, type: 'student_contact', dueAt: '2026-11-07 18:32:00', overdue: false },
} as Awaited<ReturnType<typeof api.studentContactContext>>)
api.studentContactRecords = async () => ({ list: [], total: 0 })
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter><MyStudentsPage permissions={['zsjos:student:query-my']} /></MemoryRouter></App></ThemeProvider>)
