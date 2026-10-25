import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import DeliveryClassPage from '../src/pages/DeliveryClassPage'
import { http, type DeliveryClass } from '../src/services/api'
import type { ClassGroup, ClassGroupNode } from '../src/services/deliveryClassGroups'

const fixtureState = new URLSearchParams(location.search).get('state')
const teachers = [{ id: 20, name: '测试规划师甲', deptId: 80, deptName: '测试交付部' }, { id: 21, name: '测试规划师乙', deptId: 80, deptName: '测试交付部' }]
const groups: ClassGroup[] = [{ id: 1, name: '批量测试', deptId: 80, referenceDate: '2026-12-01', version: 0, canCreate: true, canUpdate: true, canDelete: true }, { id: 2, name: '空组测试', deptId: 80, version: 0, canCreate: true, canUpdate: true, canDelete: true }]
const makeRow = (id: number, kind: DeliveryClass['classKind'] = 'NORMAL', groupId?: number): DeliveryClass => ({ id, classNo: `BJ${id}`, className: kind === 'NORMAL' ? '批量测试小班' : kind === 'UNSCHEDULED' ? '未确定考期' : '异常结业', groupId, systemClass: kind !== 'NORMAL', classKind: kind, availableActions: fixtureState !== 'readonly' && kind === 'NORMAL' ? ['UPDATE', 'MOVE_GROUP', 'COMPLETE'] : [], homeroomUserId: id % 2 ? 20 : 21, homeroomUserNameSnapshot: id % 2 ? teachers[0].name : teachers[1].name, deptId: 80, status: 'SERVING', startDate: '2026-12-01', studentCount: 2, version: 0, projects: [] })
const rows = [...Array.from({ length: 15 }, (_, i) => makeRow(100 + i, 'NORMAL', 1)), makeRow(200), makeRow(300, 'UNSCHEDULED'), makeRow(301, 'UNSCHEDULED'), makeRow(400, 'EXCEPTION'), makeRow(401, 'EXCEPTION')]
if (fixtureState === 'display') {
  const independentName = '2027届学历提升专升本高起专国家开放大学综合培养班（长期学习规划与考试指导服务）InternationalProfessionalDevelopmentProgramme'
  const groupedName = '2027年度公共营养师与健康管理综合提升班（周末直播及考前集中辅导，包含完整课程学习服务与阶段复习安排）'
  const project = { id: 91, name: '职业考证-营养健康', path: '职业考证 / 营养健康' }
  rows.splice(0, rows.length,
    { ...makeRow(1, 'PENDING'), className: '待分班', classNo: 'PENDING', homeroomUserId: undefined, homeroomUserNameSnapshot: undefined, studentCount: 193 },
    makeRow(300, 'UNSCHEDULED'), makeRow(301, 'UNSCHEDULED'),
    { ...makeRow(100, 'NORMAL', 1), className: groupedName },
    { ...makeRow(101, 'NORMAL', 1), className: '组内营养师课程', projects: [project] },
    { ...makeRow(200), className: independentName },
    { ...makeRow(201), className: '普通班稍后补填考期', startDate: undefined },
    { ...makeRow(202), className: '职业健康管理师正式班', projects: [project] },
    makeRow(400, 'EXCEPTION'), makeRow(401, 'EXCEPTION'))
  // Nonempty metadata proves system presentation is intentional, not an empty-data fallback.
  for (const row of rows.filter(r => r.systemClass)) row.projects = [{ id: 92, name: '系统班项目快照', path: '历史系统班项目' }]
}
const calls: { method?: string; url?: string; data: unknown; params: Record<string, unknown> }[] = []
const fixture = { rows, groups, calls, failRoot: fixtureState === 'root-error', failChild: fixtureState === 'child-error', failSave: fixtureState === 'save-error', failMove: fixtureState === 'move-error', delayChildren: false, denyRoot: false, denyChild: false }
Object.assign(window, { classGroupsFixture: fixture })
const hasWrite = fixtureState !== 'readonly'
const key = (r: DeliveryClass) => r.classKind === 'UNSCHEDULED' || r.classKind === 'EXCEPTION' ? `system:${r.classKind}` : r.groupId ? `group:${r.groupId}` : `class:${r.id}`
http.defaults.adapter = async config => {
  const payload = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  const params = (config.params || {}) as Record<string, unknown>
  const path = config.url || ''; calls.push({ method: config.method, url: path, data: payload, params })
  let data: unknown
  if (path.startsWith('/zsjos/student-entry/')) {
    const entryData = path.endsWith('/options') ? { directors: [{ id: 22, name: '责任编导甲' }], courses: [], classes: rows.filter(r => r.classKind !== 'EXCEPTION').map(r => ({ ...r, homeroomUserName: r.homeroomUserNameSnapshot })) }
      : path.endsWith('/check') ? { status: 'new', services: [] } : { personId: 42, personNo: 'XY-SYNTHETIC', serviceRelationId: 44, canRead: false }
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data: entryData } }
  }
  const paginate = <T,>(items: T[]) => ({ list: items.slice((Number(params.pageNo || 1) - 1) * Number(params.pageSize || 12), Number(params.pageNo || 1) * Number(params.pageSize || 12)), total: items.length })
  const filtered = rows.filter(r => (!params.status || r.status === params.status) && (!params.homeroomUserId || r.homeroomUserId === Number(params.homeroomUserId)) && (!params.keyword || [r.className, r.classNo, groups.find(g => g.id === r.groupId)?.name || ''].some(s => s.includes(String(params.keyword)))))
  const error = (msg: string, code = 1091140004) => ({ data: { code, msg }, status: 200, statusText: 'OK', headers: {}, config })
  if (path.endsWith('/filter-context')) data = { projects: [], homerooms: teachers }
  else if (path.endsWith('/homeroom-candidates')) data = teachers
  else if (path.endsWith('/group/page') || path.endsWith('/group/my-page')) {
    if (fixture.denyRoot) return error('无权查询班级组', 1091140002)
    if (fixture.failRoot) return error('班级组查询失败，请重试')
    const keys = [...new Set(filtered.map(key))]
    if (!params.keyword && !params.homeroomUserId && params.status !== 'COMPLETED') for (const g of groups) if (!rows.some(r => r.groupId === g.id) && hasWrite) keys.push(`group:${g.id}`)
    const result: ClassGroupNode[] = keys.map(k => {
      const members = filtered.filter(r => key(r) === k); const g = groups.find(x => `group:${x.id}` === k)
      const type = k.startsWith('class:') ? 'CLASS' : g ? 'GROUP' : 'SYSTEM'
      const name = g?.name || members[0]?.className || ''
      return { key: k, type, name, group: g ? { ...g, canCreate: hasWrite, canUpdate: hasWrite, canDelete: hasWrite } : undefined, single: type === 'CLASS' ? members[0] : undefined, classCount: members.length, homeroomCount: new Set(members.map(r => r.homeroomUserId)).size, serviceCount: members.reduce((n, r) => n + r.studentCount, 0), servingCount: members.filter(r => r.status === 'SERVING').length, completedCount: members.filter(r => r.status === 'COMPLETED').length, examDate: type === 'SYSTEM' ? '—' : members.length ? '2026-12-01' : '—', projects: [], autoExpand: !!params.keyword && !name.includes(String(params.keyword)) }
    })
    data = { ...paginate(result), classTotal: filtered.length }
  } else if (path.endsWith('/children') || path.endsWith('/my-children')) {
    if (fixture.denyChild) return error('无权读取该组小班', 1091140002)
    const captured = paginate(filtered.filter(r => key(r) === params.key))
    if (fixture.delayChildren) await new Promise(resolve => setTimeout(resolve, 700))
    if (fixture.failChild) return error('小班读取失败，请重试')
    data = captured
  } else if (path.endsWith('/group/options')) data = paginate(groups.filter(g => !params.keyword || g.name.includes(String(params.keyword))))
  else if (path.endsWith('/group/candidates')) data = paginate(rows.filter(r => !r.systemClass && (!params.keyword || r.className.includes(String(params.keyword)) || r.classNo.includes(String(params.keyword)))).map(r => ({ ...r, groupName: groups.find(g => g.id === r.groupId)?.name })))
  else if (path.endsWith('/group/create')) { groups.push({ ...payload, id: 10 + groups.length, deptId: 80, version: 0, canCreate: true, canUpdate: true, canDelete: true }); data = groups.at(-1)!.id }
  else if (path.endsWith('/group/create-classes')) {
    if (fixture.failSave) return error('班主任不可用，请重试')
    let groupId = payload.groupId
    if (payload.groupMode === 'NEW') { groupId = 10 + groups.length; groups.push({ ...payload.newGroup, id: groupId, deptId: 80, version: 0, canCreate: true, canUpdate: true, canDelete: true }) }
    const ids: number[] = []
    for (const teacher of payload.homeroomUserIds) { const r = makeRow(1000 + rows.length, 'NORMAL', groupId); Object.assign(r, { className: payload.className, startDate: payload.startDate, homeroomUserId: teacher }); rows.push(r); ids.push(r.id) }
    data = { groupId, classIds: ids }
  } else if (path.endsWith('/group/move')) {
    if (fixture.failMove) return error('班级已变化，请刷新后重试')
    for (const item of payload.classes) { const r = rows.find(x => x.id === item.id)!; r.groupId = payload.targetGroupId; r.version++ }
    data = true
  } else if (config.method === 'put' && path.includes('/group/')) { Object.assign(groups.find(g => g.id === Number(path.split('/').pop()))!, payload); data = true }
  else if (config.method === 'delete') { groups.splice(groups.findIndex(g => g.id === Number(path.split('/').pop())), 1); data = true }
  else if (config.method === 'put') { Object.assign(rows.find(r => r.id === Number(path.split('/').pop()))!, payload); data = true }
  else if (path.endsWith('/complete')) { rows.find(r => r.id === Number(path.split('/').at(-2)))!.status = 'COMPLETED'; data = true }
  else throw new Error(`Unexpected isolated fixture request: ${config.method} ${path}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function Fixture() {
  const { setInboxLayoutMode } = useTheme(); const route = useLocation(); const navigate = useNavigate()
  const permissions = ['zsjos:delivery-class:query-managed', 'zsjos:student:query-my', ...(new URLSearchParams(location.search).has('entry') ? ['zsjos:student:create'] : []), ...(hasWrite ? ['zsjos:delivery-class:create', 'zsjos:delivery-class:update', 'zsjos:delivery-class:complete', 'zsjos:delivery-class:group-delete'] : [])]
  return <><Space><Button onClick={() => setInboxLayoutMode('split')}>测试卡片布局</Button><Button onClick={() => setInboxLayoutMode('table')}>测试表格布局</Button></Space>
    {route.pathname.endsWith('/my-students') ? <Button onClick={() => navigate(-1)}>返回班级列表</Button> : <DeliveryClassPage permissions={permissions} />}</>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><Fixture /></BrowserRouter></App></ThemeProvider>)
