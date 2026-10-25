// UTF-8. Real page and profile panel with synthetic transport; no live business requests.
import { createRoot } from 'react-dom/client'
import { App, Button, ConfigProvider, Space } from 'antd'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import RetainedReviewRoute from '../src/layouts/RetainedReviewRoutes'
import { http, type MediaAccount, type MediaStudentDetail } from '../src/services/api'
import type { AccountProfile } from '../src/services/mediaAccountProfile'
import '../src/styles/index.css'

const options = new URLSearchParams(location.search)
const student = { personId: 900000, personNo: 'XY-DELETE-FIXTURE', name: '删除验收学员', inServicePeriod: true, services: [] }
type FixtureAccount = MediaStudentDetail['accounts'][number] & MediaAccount
const makeAccount = (id: number, nickname: string, runStatus = 'active'): FixtureAccount => ({
  id, accountNo: `MA-DELETE-${id}`, nickname, platformValue: '', platformLabelSnapshot: '',
  runStatus, deleteStatus: runStatus === 'deleted' ? 'approved' : runStatus === 'delete_pending' ? 'pending' : undefined,
  version: 1, availableActions: runStatus !== 'active' || options.has('denied') ? [] : [options.has('operator') ? 'REQUEST_DELETE_ACCOUNT' : 'DELETE_ACCOUNT_DIRECTLY'],
  primaryProblems: [], detailSnapshots: [], taskLine: [],
})
const accounts = [makeAccount(900001, '待删除的测试账号', options.has('pending') ? 'delete_pending' : 'active'),
  ...(options.has('single') ? [] : [makeAccount(900002, '保留的测试账号')]), makeAccount(900003, '已经删除的测试账号', 'deleted')]
const visibleAccounts = () => accounts.filter(account => account.runStatus !== 'deleted').map(account => ({ ...account }))
const fixture = {
  requests: [] as Array<{ url: string; method?: string; body?: unknown }>,
  reads: [] as string[], failNext: options.has('fail'), postDelay: 250,
  profileRefreshStarted: 0, profileRefreshCompleted: 0, detailRefreshStarted: 0, detailRefreshCompleted: 0,
  get accounts() { return accounts },
}
Object.assign(window, { accountDeleteVisibilityFixture: fixture })
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (config.method === 'post' && /\/media-account\/\d+\/(delete|request-delete)$/.test(url)) {
    fixture.requests.push({ url, method: config.method, body: JSON.parse(config.data) })
    if (fixture.postDelay) await new Promise(resolve => setTimeout(resolve, fixture.postDelay))
    if (fixture.failNext) {
      fixture.failNext = false
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 1900011003, msg: '账号已被其他人修改，请刷新后重试' } }
    }
    const account = accounts.find(item => item.id === Number(url.split('/').at(-2)))!
    account.availableActions = []
    account.runStatus = url.endsWith('/request-delete') ? 'delete_pending' : 'deleted'
    account.deleteStatus = url.endsWith('/request-delete') ? 'pending' : 'approved'
    account.version++
    data = url.endsWith('/request-delete') ? 'fixture-delete-process' : true
  } else if (config.method !== 'get') {
    throw new Error(`Unexpected fixture write: ${config.method} ${url}`)
  } else {
    fixture.reads.push(url)
    if (url.endsWith('/media-students/page')) data = { list: [{ ...student, accounts: visibleAccounts() }], total: 1 }
    else if (url.endsWith('/media-students/900000')) {
      if (options.has('retained') && accounts[0].runStatus === 'deleted') {
        fixture.detailRefreshStarted++
        await new Promise(resolve => setTimeout(resolve, 900))
        fixture.detailRefreshCompleted++
      }
      data = { student, canUpdateServicePeriod: false, accounts: visibleAccounts(), contents: [], positioningCards: [], positioningDrafts: [] }
    }
    else if (url.endsWith('/profile')) {
      const account = accounts.find(item => item.id === Number(url.split('/').at(-2)))!
      if (options.has('retained') && account.id === 900001 && account.runStatus === 'deleted') {
        fixture.profileRefreshStarted++
        await new Promise(resolve => setTimeout(resolve, 900))
        fixture.profileRefreshCompleted++
      }
      const profile: AccountProfile = { account: { ...account }, config: { id: 1, versionNo: 1, fields: [] }, values: {},
        snapshots: [], editableFields: [], missingFields: [], missingByOwner: {}, sourceNotes: {}, files: {},
        canViewHistory: false, studentName: student.name, directorName: '测试编导', operatorName: '测试运营' }
      data = profile
    } else if (url.endsWith('/advanced-filter/catalog')) data = { fields: [] }
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function RouteProbe() {
  const route = useLocation()
  return <output id="fixture-route" data-path={route.pathname} hidden>{route.search}</output>
}
const accountId = options.has('invalid') ? 900003 : 900001
const initialRoute = `/zsjos/media-students?personId=900000&accountId=${accountId}&tab=account-${accountId}&contentId=880001&positioningCardId=880002&diagnosisTaskId=880003`
function StudentPage() {
  return <div style={{ height: '100vh' }}><MediaStudentsPage permissions={['zsjos:media-student:query-my', 'zsjos:media-account:query']} /></div>
}
function RetainedHost() {
  const route = useLocation(), navigate = useNavigate()
  const active = route.pathname === '/zsjos/media-students'
  return <><Space><Button onClick={() => navigate('/other')}>切换到其他页签</Button>
    <Button onClick={() => navigate(initialRoute)}>返回原学员页签</Button></Space><RouteProbe />
    <RetainedReviewRoute active={active}><StudentPage /></RetainedReviewRoute>
    {!active && <h1>其他工作台页签</h1>}
  </>
}
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={[initialRoute]}>
  <WorkbenchPageNavigation canOpen={() => true}>{options.has('retained') ? <RetainedHost /> : <><RouteProbe /><StudentPage /></>}</WorkbenchPageNavigation>
</MemoryRouter></App></ThemeProvider></ConfigProvider>)
