// UTF-8. Isolated browser fixture: no real API requests or persisted business writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const students = Array.from({ length: 25 }, (_, i) => ({ personId: i + 1, personNo: `TEST-${i + 1}`,
  name: `验收学员${i + 1}`, inServicePeriod: i !== 1, services: [], accounts: [] }))
const fixture = { failure: '', delay: 0, writes: 0, canUpdate: true, queries: [] as Record<string, unknown>[] }
Object.assign(window, { servicePeriodFixture: fixture })
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/media-students/page')) {
    fixture.queries.push({ ...config.params })
    const filtered = students.filter(x => (config.params.inServicePeriod === undefined || x.inServicePeriod === config.params.inServicePeriod)
      && (!config.params.keyword || x.name.includes(config.params.keyword)))
    if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
    if (fixture.failure === 'list') throw new Error('列表加载失败，请重试')
    const start = (config.params.pageNo - 1) * config.params.pageSize
    data = { list: filtered.slice(start, start + config.params.pageSize), total: filtered.length }
  } else if (url.endsWith('/service-period') && config.method === 'put') {
    fixture.writes++
    if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
    if (fixture.failure === 'save') throw new Error('服务期保存失败')
    const student = students.find(x => x.personId === Number(url.split('/').at(-2)))!
    student.inServicePeriod = JSON.parse(config.data).inServicePeriod
    data = student.inServicePeriod
  } else if (/media-students\/\d+$/.test(url)) {
    data = { student: { ...students.find(x => x.personId === Number(url.split('/').pop())) },
      canUpdateServicePeriod: fixture.canUpdate, accounts: [], contents: [], positioningCards: [], positioningDrafts: [] }
  } else if (config.method !== 'get') throw new Error('Unexpected fixture write')
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const permissions = ['zsjos:media-student:query-my']
if (!location.search.includes('readonly')) permissions.push('zsjos:media-student:update-service-period')
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/media-students']}>
  <div style={{ height: '100vh', padding: 12 }}><MediaStudentsPage permissions={permissions} /></div>
</MemoryRouter></App></ThemeProvider></ConfigProvider>)
