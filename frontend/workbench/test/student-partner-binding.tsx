// UTF-8. Isolated browser fixture; all transport is synthetic and no business writes are allowed.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const account = { id: 1, accountNo: 'TEST-ACCOUNT', nickname: '测试账号', version: 1, availableActions: ['MAINTAIN_ACCOUNT'] }
const service = { serviceRelationId: 1, status: 'active' }
const students = Array.from({ length: 45 }, (_, i) => ({ personId: i + 1, personNo: `TEST-${i + 1}`, name: `测试学员${i + 1}`, services: [service] }))
const fixture = { mode: 'success', calls: 0, delay: 0, opened: false, operator: 9 as number | undefined, conflict: false, invitation: JSON.parse(sessionStorage.getItem('test-student-invitation') || 'null') as Record<string, unknown> | null, created: 0 }

Object.assign(window, { railFixture: fixture })
http.defaults.adapter = async config => {

  fixture.calls++
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/partner/page')) {
    if (fixture.mode === 'partner-error') throw new Error('兼职列表加载失败')
    if (fixture.mode === 'partner-denied') return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 403, msg: '无权查询兼职' } }
    const candidates = Array.from({ length: 12 }, (_, i) => ({ id: i + 1, partnerNo: `P-TEST-${i+1}`, name: `测试兼职${i+1}`, mobile: '13800000000', status: 'enabled' }))
      .filter(x => !config.params.keyword || x.name.includes(config.params.keyword))
    data = { list: fixture.mode === 'partner-empty' ? [] : candidates.slice((config.params.pageNo - 1) * 10, config.params.pageNo * 10), total: fixture.mode === 'partner-empty' ? 0 : candidates.length }
  } else if (url.endsWith('/partner-student-link/bind')) {
    if (fixture.mode === 'bind-conflict') return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 1900014007, msg: '兼职账号或学员已绑定其他身份' } }
    Object.assign(fixture, { boundParams: config.params })
    fixture.opened = true
    data = true
  } else if (url.endsWith('/student/context')) {
    if (fixture.mode === 'context-error') throw new Error('兼职状态加载失败')
    if (fixture.mode === 'denied') return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 403, msg: '无权查看兼职状态' } }
    data = { opened: fixture.opened, defaultOperatorUserId: fixture.operator, operatorAssignmentConflict: fixture.conflict, invitation: fixture.opened ? undefined : fixture.invitation }
  } else if (url.endsWith('/operator-candidates')) {
    if (fixture.mode === 'operator-error') throw new Error('运营加载失败')
    data = { list: fixture.mode === 'empty-operators' ? [] : [{ id: 9, nickname: '测试运营甲' }, { id: 10, nickname: '测试运营乙' }], total: fixture.mode === 'empty-operators' ? 0 : 2 }
  } else if (url.endsWith('/student/create') && config.method === 'post') {
    if (fixture.mode === 'create-error') throw new Error('测试生成失败')
    const body = JSON.parse(config.data)
    fixture.invitation = { ...body, id: 1, status: 'active', inviteCode: 'TEST1234', assignedOperatorName: body.assignedOperatorUserId === 9 ? '测试运营甲' : '测试运营乙' }
    sessionStorage.setItem('test-student-invitation', JSON.stringify(fixture.invitation))
    fixture.created++
    data = fixture.invitation
  } else if (config.method !== 'get') throw new Error('Unexpected fixture write')
  else if (url.endsWith('/media-students/page')) {
    if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
    if (fixture.mode === 'error') throw new Error('测试列表加载失败')
    if (fixture.mode === 'denied') return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 403, msg: '无权查看学员列表' } }
    const filtered = fixture.mode === 'empty' ? [] : students.filter(x => !config.params.keyword || x.name.includes(config.params.keyword))
    const start = (config.params.pageNo - 1) * 20
    data = { list: filtered.slice(start, start + 20), total: filtered.length }
  } else if (/media-students\/\d+$/.test(url)) {
    data = { student: students.find(x => x.personId === Number(url.split('/').pop())), accounts: [account], contents: [], positioningCards: [], positioningDrafts: [] }
  } else if (url.endsWith('/profile')) {
    data = { account, config: { id: 1, versionNo: 1, fields: [{ key: 'nickname', label: '账号昵称', type: 'text', group: 'PROFILE', enabled: true, ownerType: 'DIRECTOR' }] }, values: { nickname: '测试账号' }, snapshots: [], files: {}, sourceNotes: {}, editableFields: ['nickname'], missingFields: [], missingByOwner: {}, canViewHistory: false }
  } else if (url.includes('contact-context')) {
    data = { availableActions: [], visibleTabs: [], currentStage: 'active', version: 1 }
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/media-students?personId=1']}><div style={{ height: '100vh', padding: 12 }}><MediaStudentsPage permissions={location.search.includes('no-permission') ? [] : ['zsjos:media-account:query', 'zsjos:partner:manage-all', ...(location.search.includes('manage-only') ? [] : ['zsjos:partner-invitation:create-student'])]} /></div></MemoryRouter></App></ThemeProvider></ConfigProvider>)
