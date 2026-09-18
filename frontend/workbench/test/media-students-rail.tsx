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
const fixture = { mode: 'success', calls: 0, delay: 0 }
Object.assign(window, { railFixture: fixture })
http.defaults.adapter = async config => {
  if (config.method !== 'get') throw new Error('Fixture does not allow writes')
  fixture.calls++
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/media-students/page')) {
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
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/media-students?personId=1']}><div style={{ height: '100vh', padding: 12 }}><MediaStudentsPage permissions={['zsjos:media-account:query']} /></div></MemoryRouter></App></ThemeProvider></ConfigProvider>)
