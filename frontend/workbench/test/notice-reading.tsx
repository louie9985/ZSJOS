// UTF-8. Isolated synthetic transport: never sends requests to business services.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Space } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import NoticeManagementDetail from '../src/components/NoticeManagementDetail'
import AnnouncementCenterPage, { DisplayedAnnouncement } from '../src/pages/AnnouncementCenterPage'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { api, http, ApiError } from '../src/services/api'
import { noticeReadStatistics, type ManagedNotice } from '../src/services/noticeManagement'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const fixture = { fail: false, denied: false, readFail: false, reads: 0, summaries: 0, queries: [] as unknown[], painted: false }
Object.assign(window, { noticeFixture: fixture })
const notice: ManagedNotice = { id: 1, title: '公告阅读验收', type: 2, content: '<p>真实正文展示测试</p>', audienceType: 'ALL', targetDeptIds: [], targetUserIds: [], attachments: [], publishStatus: params.has('draft') ? 'DRAFT' : 'PUBLISHED' }
noticeReadStatistics.summary = async () => {
  fixture.summaries++
  if (fixture.denied) throw new ApiError(403, '无权查看阅读情况')
  if (fixture.fail) throw new Error('统计加载失败')
  return { published: notice.publishStatus !== 'DRAFT', rosterComplete: !params.has('legacy'), expectedCount: 100, readCount: 80, unreadCount: 20, readRate: 0.8, extraReadCount: 5, actualReadCount: 85, departments: [{ id: 10, name: '发布部门' }], extraDepartments: [{ id: 20, name: '当前部门' }] }
}
noticeReadStatistics.page = async query => {
  fixture.queries.push(query)
  const count = query.scope === 'UNREAD' ? 20 : query.scope === 'EXTRA' ? 5 : query.scope === 'ACTUAL' ? 85 : query.scope === 'READ' ? 80 : 100
  const all = Array.from({ length: count }, (_, i) => ({ userId: i + 1, userName: '员工' + (i + 1), deptId: 10, deptName: query.scope === 'EXTRA' ? '当前部门' : '发布部门', profileSource: query.scope === 'EXTRA' || params.has('legacy') ? 'CURRENT' as const : 'SNAPSHOT' as const, accountStatus: 0, accountDeleted: i === 0, readTime: query.scope === 'UNREAD' ? undefined : 1790000000000 }))
  const filtered = all.filter(row => !query.name || row.userName.includes(query.name))
  return { list: filtered.slice((query.pageNo - 1) * query.pageSize, query.pageNo * query.pageSize), total: filtered.length }
}
api.markAnnouncementRead = async () => {
  fixture.reads++; fixture.painted = [...document.querySelectorAll('.announcement-detail')].some(node => node.getClientRects().length > 0)
  if (fixture.readFail) throw new Error('写入失败')
  return true
}
http.defaults.adapter = async config => {
  const path = config.url || ''
  let data: unknown
  if (path.endsWith('/my-page')) data = { list: [{ ...notice, read: false }], total: 1 }
  else if (path.endsWith('/my-cursor')) data = { list: [{ ...notice, read: false }], hasMore: false }
  else if (path.endsWith('/my-get')) { if (params.has('detailFail')) throw new Error('正文加载失败'); data = { ...notice, read: false } }
  else if (path.endsWith('/unread-summary')) data = { unreadCount: 1 }
  else throw new Error('Unexpected fixture request: ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function Fixture() {
  const [mode, setMode] = useState('manage')
  const [read, setRead] = useState(false)
  if (params.has('center')) return <BrowserRouter><RealtimeProvider enabled={false}><AnnouncementProvider enabled><AnnouncementCenterPage permissions={['system:notice:read']} /></AnnouncementProvider></RealtimeProvider></BrowserRouter>
  return <div style={{ padding: 16, minWidth: 0 }}><Space wrap>
    <Button onClick={() => { fixture.readFail = true; setMode('reader') }}>打开员工正文</Button>
    <Button onClick={() => { fixture.readFail = false }}>恢复写入</Button>
    <Button onClick={() => setMode('hidden')}>隐藏正文</Button>
    <Button onClick={() => setMode('manage')}>管理预览</Button>
  </Space>
  {mode === 'manage' && <NoticeManagementDetail notice={notice} />}
  {mode === 'reader' && <DisplayedAnnouncement item={{ ...notice, read }} onRead={() => setRead(true)} />}
  {mode === 'hidden' && <div style={{ display: 'none' }}><DisplayedAnnouncement item={{ ...notice, read: false }} onRead={() => setRead(true)} /></div>}
  </div>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>)
