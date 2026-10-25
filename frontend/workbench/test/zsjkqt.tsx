// UTF-8. Synthetic HTTP adapter only; this page never reads or writes live business APIs.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import ZsjkqtPage from '../src/features/zsjkqt/ZsjkqtPage'
import { http } from '../src/services/api'
import type { ZsjkqtDraft, ZsjkqtSave, ZsjkqtSnapshot } from '../src/services/zsjkqt'
import type { AttendanceMonth, ExportedAttendanceReport, PersonnelProfiles } from '../src/features/zsjkqt/core/models'
import type { DepartmentScheduleSettings } from '../src/features/zsjkqt/core/departmentSchedules'
import '../src/styles/index.css'

const query = new URLSearchParams(location.search)
const storageKey = 'zsjkqt_browser_fixture'
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const saved = JSON.parse(sessionStorage.getItem(storageKey) || '{}') as { drafts?: ZsjkqtDraft[]; config?: { version: number; value: DepartmentScheduleSettings }; profiles?: PersonnelProfiles }
const defaultConfig = { version: 0, value: { default: { morning: { start: '08:30', end: '12:00' }, afternoon: { start: '13:30', end: '18:00' } }, departments: { '合成测试部': { mode: 'default' as const }, '合成研发部': { mode: 'custom' as const, morning: { start: '09:00', end: '12:00' }, afternoon: { start: '13:00', end: '18:00' } } } } }
const state = {
  drafts: saved.drafts || [], config: saved.config || defaultConfig, profiles: saved.profiles || {},
  requests: [] as Array<{ url: string; method: string; body: unknown; params: unknown }>,
  failOperation: '', failMessage: '合成网络失败，请重试', delayOperation: '', delayMs: 0,
  denyApi: query.has('api-denied'), imports: 0, exports: [] as string[], activeWrites: 0, maxWrites: 0,
  exported: null as ZsjkqtSnapshot | null,
  sourceOverride: null as AttendanceMonth | null,
  bumpVersion(id: number) { const row = this.drafts.find(d => d.id === id); if (row) { row.version++; row.name += '（其他人修订）'; this.persist() } },
  persist() { sessionStorage.setItem(storageKey, JSON.stringify({ drafts: this.drafts, config: this.config, profiles: this.profiles })) }
}
Object.assign(window, { zsjkqtFixture: state })

const attendance: AttendanceMonth = {
  year: 2026, month: 6, startDate: '2026-06-01', endDate: '2026-06-30', sourceFileName: '合成月报_20260601-20260630.xlsx', sourceFileHash: 'synthetic-month-hash',
  employees: ['合成员工甲', '合成员工乙'].map((name, i) => ({ name, account: `synthetic-employee-${i}`, rule: '合成规则', department: i ? '合成研发部' : '合成测试部', position: '', jobNo: '', summaries: { '应出勤天数(天)': 21, '实际出勤天数(天)': 21 }, dayDetails: Object.fromEntries(Array.from({ length: 30 }, (_, d) => [d + 1, '正常- 08:30;18:00'])) }))
}

http.defaults.adapter = async config => {
  const url = config.url || '', method = config.method || 'get'
  const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  const params = config.params || {}
  state.requests.push({ url, method, body: body instanceof FormData ? 'multipart' : clone(body ?? null), params: clone(params) })
  const response = (data: unknown, code = 0, msg = '') => ({ data: { code, data, msg }, config, status: 200, statusText: 'OK', headers: {} })
  if (!url.startsWith('/zsjos/zsjkqt/') && url !== '/zsjos/calendar-rest-day/list') throw new Error(`Unexpected fixture request: ${url}`)
  if (state.denyApi) return response(null, 403, '没有中世健考勤通访问权限')
  const operation = /\/drafts(?:\/\d+)?$/.test(url) ? (method === 'get' ? (url.endsWith('/drafts') ? 'list' : 'get') : method === 'delete' ? 'delete' : 'save')
    : url.includes('/parse/') ? 'parse' : url.includes('/export/') ? 'export' : url.endsWith('/name') ? 'rename' : url.endsWith('/config/schedules') ? (method === 'get' ? 'config' : 'schedules') : 'profiles'
  if (state.delayOperation === operation) { const ms = state.delayMs; state.delayOperation = ''; await new Promise(resolve => setTimeout(resolve, ms)) }
  if (state.failOperation === operation) { state.failOperation = ''; return response(null, 500, state.failMessage) }
  let data: unknown
  if (url.endsWith('/config/schedules') && method === 'get') data = clone(state.config)
  else if (url.endsWith('/config/profiles')) data = { version: 0, value: clone(state.profiles) }
  else if (url.endsWith('/config/schedules')) {
    if (body.version !== state.config.version) return response(null, 1091050005, '部门时段已被其他人修改，请重新加载后重试')
    state.config = { version: body.version + 1, value: clone(body.value) }; data = clone(state.config); state.persist()
  } else if (url.endsWith('/profiles')) {
    state.profiles[body.key] = { name: body.name, department: body.department, entry_date: body.entryDate, enabled: body.enabled }; data = true; state.persist()
  } else if (url.endsWith('/parse/source')) {
    const file = body.get('file') as File; state.imports++; const source = state.sourceOverride || attendance
    data = { attendance: { ...clone(source), sourceFileName: file.name, sourceFileHash: file.name.startsWith('different') ? 'another-synthetic-hash' : source.sourceFileHash }, issues: [] }
  } else if (url.endsWith('/parse/report')) {
    const project = state.exported?.project
    const report: ExportedAttendanceReport = project ? {
      year: project.year, month: project.month, sourceFileName: '合成续改.xlsx', sourceFileHash: 'synthetic-exported-hash',
      employees: project.employees.filter(e => e.enabled).map(e => ({ name: e.displayName, department: e.displayDepartment, sourceIndex: e.sourceIndex, days: e.days.map(d => ({ day: d.day, morning: d.finalMorning, afternoon: d.finalAfternoon })), summary: clone(e.summary) })),
      recoveredParameters: { source: 'metadata', shouldAttendanceDays: project.shouldAttendanceDays, payrollDays: project.payrollDays, restDays: project.restDays, paidRestDays: project.paidRestDays, restDayCandidates: [], paidRestDayCandidates: [], warnings: [] }
    } : { year: 2026, month: 6, employees: [], sourceFileHash: 'synthetic-exported-hash' }
    data = report
  } else if (url.includes('/export/')) {
    const id = Number(url.match(/drafts\/(\d+)/)?.[1]), row = state.drafts.find(d => d.id === id)
    if (!row || row.version !== Number(params.version)) return response(null, 1091050001, '草稿已被其他人修改，请重新加载或另存副本')
    state.exported = clone(row.snapshot); state.exports.push(url)
    return { data: new Blob(['synthetic workbook download'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), config, status: 200, statusText: 'OK', headers: {} }
  } else if (url.endsWith('/drafts') && method === 'get') {
    const rows = state.drafts.filter(d => (!params.keyword || d.name.includes(params.keyword)) && (!params.year || d.year === Number(params.year)) && (!params.month || d.month === Number(params.month))).sort((a, b) => (b.updateTime || 0) - (a.updateTime || 0) || b.id - a.id)
    const page = Number(params.pageNo || 1), size = Number(params.pageSize || 20); data = { list: clone(rows.slice((page - 1) * size, page * size).map(d => ({ ...d, snapshot: null }))), total: rows.length }
  } else if (operation === 'get') {
    data = clone(state.drafts.find(d => d.id === Number(url.split('/').at(-1))))
  } else if (operation === 'save') {
    state.activeWrites++; state.maxWrites = Math.max(state.maxWrites, state.activeWrites)
    try {
      const req = body as ZsjkqtSave, id = url.endsWith('/drafts') ? undefined : Number(url.split('/').at(-1)), existing = id ? state.drafts.find(d => d.id === id) : state.drafts.find(d => d.clientId === req.clientId)
      if (req.name.length > 120) return response(null, 400, '草稿名称不能超过120个字符')
      if (req.snapshot.project?.employees.some(employee => Object.values(employee.summary).some(value => typeof value === 'string' && value.length > 32767))) return response(null, 1091050002, '考勤内容不能超过32767个字符，请缩短后保存或导出')
      if (id && (!existing || existing.version !== req.version)) return response(null, 1091050001, '草稿已被其他人修改，请重新加载或另存副本')
      if (!id && existing) return req.name === existing.name && JSON.stringify(req.snapshot) === JSON.stringify(existing.snapshot)
        ? response(clone(existing)) : response(null, 1091050001, '草稿已被其他人修改，请重新加载或另存副本')
      const project = req.snapshot.project || req.snapshot.month!
      const result: ZsjkqtDraft = { ...clone(req), id: id || Math.max(0, ...state.drafts.map(d => d.id)) + 1, year: project.year, month: project.month, sourceFileName: project.sourceFileName || '', sourceFileHash: project.sourceFileHash || '', employeeCount: project.employees.length, confirmedCount: req.snapshot.project?.employees.filter(e => e.confirmed).length || 0, version: existing ? req.version + 1 : 0, updateTime: Date.now(), updater: '合成操作员' }
      state.drafts = [...state.drafts.filter(d => d.id !== result.id), result];state.persist();data = clone(result)
    } finally { state.activeWrites-- }
  } else if (operation === 'rename') {
    const row = state.drafts.find(d => d.id === Number(url.match(/drafts\/(\d+)/)?.[1]));if (!row || row.version !== body.version) return response(null, 1091050001, '版本冲突');row.name = body.name;row.version++;state.persist();data = true
  } else if (operation === 'delete') {
    const id = Number(url.split('/').at(-1)), row = state.drafts.find(d => d.id === id);if (!row || row.version !== Number(params.version)) return response(null, 1091050001, '版本冲突');state.drafts = state.drafts.filter(d => d.id !== id);state.persist();data = true
  } else if (url.includes('/calendar-rest-day/list')) { data = [2,3,7].map(day => ({ date: `2026-06-${String(day).padStart(2,'0')}`, rest: true, version: 1 }))
  } else throw new Error(`Unhandled fixture request: ${method} ${url}`)
  return response(data)
}

createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={['/hrm/zsjkqt']}><ThemeProvider><App><WorkbenchPageNavigation canOpen={() => true}>
  <div style={{ width: '100%', minWidth: 0, padding: 8 }}><div id="zsjkqt-isolation-sentinel"><button style={{ background: 'rgb(33, 44, 55)', color: 'white' }}>外部按钮</button><input aria-label="外部输入框" defaultValue="不受考勤样式影响" /></div><ZsjkqtPage permissions={query.has('denied') ? [] : ['zsjos:zsjkqt:access', ...(query.has('home-rest') ? ['zsjos:business-task:query'] : [])]} /></div>
</WorkbenchPageNavigation></App></ThemeProvider></MemoryRouter>)
