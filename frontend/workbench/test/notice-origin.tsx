// UTF-8. Synthetic transport; all production components use their actual API contracts.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import NoticeEditorDialog from '../src/components/NoticeEditorDialog'
import NoticeManagementDetail from '../src/components/NoticeManagementDetail'
import { AnnouncementPanelView } from '../src/components/HomeAnnouncementPanel'
import AnnouncementCenterPage from '../src/pages/AnnouncementCenterPage'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { http } from '../src/services/api'
import type { ManagedNotice } from '../src/services/noticeManagement'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const fixture = { writes: [] as unknown[], queries: [] as unknown[], optionCalls: 0 }
Object.assign(window, { noticeOriginFixture: fixture })
const permissions = ['read', 'query', 'create', 'update', 'publish'].map(action => `system:notice:${action}`)
const notice: ManagedNotice = { id: 1, title: '十月考试工作安排', type: 2, content: '<p>请及时查看考试安排。</p>', audienceType: params.has('target') ? 'TARGET' : 'ALL', targetDeptIds: [20], targetUserIds: [], attachments: [], publishStatus: 'PUBLISHED', publishTime: 1791388800000,
  ...(params.has('legacy') ? {} : { sourceDeptId: 10, sourceDeptName: '考务部', publisherName: '测试发布人', audienceSummary: params.has('target') ? '综合行政部（含子部门）' : '全体员工' }) }
const treeOptions = {
  defaultSourceDeptId: 10,
  departments: [{ id: 10, parentId: 0, name: '测试中心' }, { id: 20, parentId: 10, name: '测试一部' },
    { id: 30, parentId: 10, name: '测试二部' }, { id: 40, parentId: 20, name: '测试小组' }],
  users: [{ id: 101, deptId: 10, nickname: '中心员工', selectable: true },
    { id: 102, deptId: 20, nickname: '一部员工', selectable: true },
    { id: 103, deptId: 30, nickname: '二部员工', selectable: true },
    { id: 104, deptId: 40, nickname: '小组员工', selectable: true },
    { id: 105, deptId: 20, nickname: '禁用员工', selectable: false, disabledReason: '未获得公告阅读权限' },
    { id: 106, nickname: '未分配员工', selectable: true }]
}
if (params.has('recipient-tree')) {
  Object.assign(notice, { publishStatus: 'DRAFT', audienceType: 'TARGET', targetDeptIds: [10], targetUserIds: [], sourceDeptId: 10 })
  if (params.has('saved')) Object.assign(notice, JSON.parse(sessionStorage.getItem('notice-tree-saved') || '{}'))
}
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.includes('/dict-data/')) data = [{ dictType: 'system_notice_type', value: '2', label: '公告' }]
  else if (url.endsWith('/recipient-options')) {
    fixture.optionCalls++
    if (params.has('error') && fixture.optionCalls === 1) throw new Error('部门选项加载失败')
    data = params.has('recipient-tree') ? treeOptions : { defaultSourceDeptId: 10, departments: params.has('empty') ? [] : [{ id: 10, parentId: 0, name: '考务部' }, { id: 20, parentId: 0, name: '综合行政部' }], users: [] }
  } else if (url.endsWith('/create') || url.endsWith('/update') || url.endsWith('/publish')) { fixture.writes.push({ url, data: config.data }); if (params.has('recipient-tree') && config.data) sessionStorage.setItem('notice-tree-saved', config.data); data = 1 }
  else if (url.endsWith('/unread-summary')) data = { unreadCount: 1 }
  else if (url.endsWith('/my-get') || url.endsWith('/get')) data = { ...notice, read: false }
  else if (url.endsWith('/mark-read')) data = true
  else if (url.endsWith('/my-page') || url.endsWith('/page') || url.endsWith('/my-cursor')) {
    fixture.queries.push(config.params)
    const keyword = config.params?.keyword || config.params?.title
    const list = !keyword || ['考务部', '测试发布人', notice.title].some(text => text.includes(keyword)) ? [{ ...notice, read: false }] : []
    data = { list, total: list.length, hasMore: false }
  } else throw new Error(`Unexpected fixture request: ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function Fixture() {
  if (params.has('editor')) return <NoticeEditorDialog initial={params.has('draft') || params.has('saved') ? notice : undefined} permissions={params.has('readonly') ? ['system:notice:query'] : permissions} onClose={() => {}} onChanged={() => {}} />
  if (params.has('center')) return <RealtimeProvider platform="PC"><AnnouncementProvider enabled><AnnouncementCenterPage permissions={params.has('denied') ? [] : permissions} /></AnnouncementProvider></RealtimeProvider>
  if (params.has('detail')) return <NoticeManagementDetail notice={notice} />
  return <AnnouncementPanelView enabled items={[{ ...notice, publishTime: 1791388800000, read: false }]} loading={false} error="" unreadCount={1} summaryLoading={false} summaryError="" hasSummary incoming={false} onRefresh={() => {}} onRefreshSummary={() => {}} onOpen={() => {}} onAll={() => {}} />
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><div style={{ padding: 16 }}><Fixture /></div></BrowserRouter></App></ThemeProvider>)
