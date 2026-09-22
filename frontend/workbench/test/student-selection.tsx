// UTF-8. Production page with isolated synthetic transport; no business-server requests.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter, useLocation } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const names = ['测试甲', '王静', '测试乙']
if (new URLSearchParams(window.location.search).has('many')) names.push(...Array.from({ length: 22 }, (_, i) => `分页学员${i + 4}`))
const students = names.map((name, i) => ({ personId: i + 1, personNo: `TEST-${i + 1}`, name,
  services: [{ serviceRelationId: (i + 1) * 10, status: 'active', operatorUserName: `原运营${i + 1}` }] }))
const fixture = { delays: {} as Record<string, number>, failures: [] as string[], finished: [] as string[], started: [] as string[], writes: [] as { url: string; body: unknown }[] }
Object.assign(window, { selectionFixture: fixture })
http.defaults.adapter = async config => {
  const url = config.url || ''
  const key = url.endsWith('/media-students/page') ? `search:${config.params.keyword || ''}` : url
  fixture.started.push(key)
  const delay = fixture.delays[key]
  if (delay) await new Promise(resolve => setTimeout(resolve, delay))
  fixture.finished.push(key)
  if (fixture.failures.includes(key)) throw new Error('测试加载失败')
  let data: unknown = []
  if (url.endsWith('/media-students/page')) {
    const filtered = students.filter(x => !config.params.keyword || x.name.includes(config.params.keyword))
    const start = (config.params.pageNo - 1) * config.params.pageSize
    data = { list: filtered.slice(start, start + config.params.pageSize), total: filtered.length }
  } else if (/media-students\/\d+$/.test(url)) {
    data = { student: students.find(x => x.personId === Number(url.split('/').pop())), accounts: [], contents: [], positioningCards: [], positioningDrafts: [] }
  } else if (url.endsWith('/contact-context')) {
    const relationId = Number(url.split('/').at(-2))
    data = { serviceRelationId: relationId, availableActions: ['ASSIGN_OPERATOR'], visibleTabs: [], firstContactChecklist: [], quickNotes: [], version: 1,
      operatorUserId: 8, operatorUserName: `原运营${relationId / 10}` }
  } else if (url.endsWith('/collaborator-candidates')) data = [{ id: 9, nickname: '新运营' }]
  else if (url.endsWith('/collaborators')) {
    fixture.writes.push({ url, body: JSON.parse(config.data) }); data = true
  } else if (config.method !== 'get') throw new Error(`Unexpected write: ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function Fixture() {
  const location = useLocation()
  return <><output id="fixture-route" hidden>{location.search}</output><div style={{ height: '100vh', padding: 12 }}><MediaStudentsPage /></div></>
}
const initial = new URLSearchParams(window.location.search).get('route') || '/zsjos/media-students?personId=1'
createRoot(document.getElementById('root')!).render(<StrictMode><ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={[initial]}>
  <WorkbenchPageNavigation canOpen={() => true}><Fixture /></WorkbenchPageNavigation>
</MemoryRouter></App></ThemeProvider></ConfigProvider></StrictMode>)
