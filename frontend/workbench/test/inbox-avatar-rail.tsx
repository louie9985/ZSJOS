// UTF-8. Real page components with isolated transport; business writes are forbidden.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { MyStudentsPage } from '../src/pages/RegistrationPages'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const permissions: string[] = []
const leads = Array.from({ length: 65 }, (_, i) => ({ id: i + 1, leadNo: `TEST-LEAD-${i + 1}`, submittedName: `测试客资${i + 1}`, availableActions: [{ code: 'REQUEST_SUBMITTER_ASSIST', enabled: true }], visibleTabs: ['overview'], attachments: [], relationTypes: [], status: 'pending', productSelections: [] }))
const students = Array.from({ length: 45 }, (_, i) => ({ personId: i + 1, personNo: `TEST-STUDENT-${i + 1}`, name: `测试学员${i + 1}`, services: [{ serviceRelationId: 1, status: 'active', courseName: '测试课程', orderNo: 'TEST-ORDER' }] }))
const query = new URLSearchParams(location.search)
const state = { mode: query.get('mode') || 'success', delay: Number(query.get('delay')) || 0, calls: 0, pageCalls: [] as number[], lastParams: {} }
Object.assign(window, { railFixture: state })
http.defaults.adapter = async config => {
  const url = config.url || ''
  if (config.method !== 'get' && !url.endsWith('/search-page')) throw new Error('Fixture forbids business writes')
  state.calls++
  let data: unknown = []
  const params = config.method === 'post' ? JSON.parse(config.data) : config.params || {}
  if (url === '/zsjos/lead/page' || url.endsWith('/lead/search-page') || url.endsWith('/student/my-page') || url.endsWith('/student/my/search-page')) {
    state.pageCalls.push(params.pageNo); state.lastParams = params
    if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay))
    if (state.mode === 'error' || (state.mode === 'more-error' && params.pageNo > 1)) throw new Error('测试列表加载失败')
    if (state.mode === 'denied') return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 403, msg: '无权查看列表' } }
    const source = url.includes('/student/') ? students : leads
    const list = state.mode === 'empty' ? [] : source.filter(x => !params.keyword || ('name' in x ? x.name : x.submittedName).includes(params.keyword))
    data = { list: list.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize), total: list.length }
  } else if (url === '/zsjos/lead/get') data = leads.find(x => x.id === params.id)
  else if (/student\/my\/\d+$/.test(url)) data = students.find(x => x.personId === Number(url.split('/').pop()))
  else if (url.includes('contact-context')) data = { availableActions: ['EDIT_BASIC_INFO'], visibleTabs: [], version: 1 }
  else if (url.includes('delivery-class')) data = { list: params.status === 'SERVING' ? [{ id: 1, className: '测试班级' }] : [], total: 1 }
  else if (url.includes('page') || url.includes('records')) data = { list: [], total: 0 }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const student = new URLSearchParams(location.search).get('page') === 'student'
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter initialEntries={[student ? '/zsjos/my-students' : '/zsjos/leads']}><RealtimeProvider platform="PC"><div style={{ height: '100vh', padding: 12 }}>{student ? <MyStudentsPage permissions={permissions} /> : <LeadManagementPage permissions={permissions} />}</div></RealtimeProvider></MemoryRouter></App></ThemeProvider>)
