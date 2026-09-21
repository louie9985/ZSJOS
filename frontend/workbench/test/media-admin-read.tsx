// UTF-8. Isolated read-only fixture, excluded from the production entry point.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const student = { personId: 2, personNo: 'XS-TEST', name: '验收学员', services: [] }
const card = { id: 9, cardNo: 'PC-TEST', status: 'co_creating', version: 1, availableActions: [],
  fieldsSnapshot: [{ key: 'goal', title: '业务目标', label: '业务目标', type: 'text', enabled: true }],
  valuesSnapshot: { goal: '历史定位草稿内容' }, dictSnapshot: {} }
const fixture = { mode: 'success', writes: 0 }
Object.assign(window, { mediaAdminReadFixture: fixture })
http.defaults.adapter = async config => {
  if (config.method !== 'get') { fixture.writes++; throw new Error('Read fixture forbids writes') }
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/media-students/page')) data = { list: [student], total: 1 }
  else if (url.endsWith('/media-students/2')) data = { student, accounts: [], contents: [], positioningCards: [], positioningDrafts: [card] }
  else if (url.includes('/positioning-card/')) {
    if (fixture.mode === 'error') throw new Error('草稿读取失败')
    data = card
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/media-students?personId=2']}><MediaStudentsPage permissions={['zsjos:media-student:query-my', 'zsjos:media-student:query-all', 'zsjos:positioning-card:query']} /></MemoryRouter></App></ThemeProvider></ConfigProvider>)
