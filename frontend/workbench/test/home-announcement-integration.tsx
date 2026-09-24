// Isolated browser fixture: production providers, homepage and reading page; no live API writes.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Space } from 'antd'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import HomeAnnouncementPanel from '../src/components/HomeAnnouncementPanel'
import AnnouncementCenterPage from '../src/pages/AnnouncementCenterPage'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { api, type Announcement } from '../src/services/api'
import { APP_ROUTES } from '../src/constants'
import '../src/styles/index.css'

const rows: Announcement[] = [1, 2, 3, 4].map(id => ({ id, title: `示例公告 ${id}`, type: 1, read: id % 2 === 0, highlighted: id <= 2, publishTime: '2026-09-24 09:00:00', content: '<p>隔离测试内容</p>', attachments: [] }))
let failure = new URLSearchParams(location.search).get('failure') || ''
let listCalls = 0
let socket: FixtureSocket | undefined
class FixtureSocket {
  static OPEN = 1
  readyState = 1
  onopen?: () => void
  onmessage?: (event: { data: string }) => void
  constructor() { socket = this; queueMicrotask(() => this.onopen?.()) }
  send() {}
  close() {}
}
window.WebSocket = FixtureSocket as unknown as typeof WebSocket
api.announcementPage = async () => { listCalls++; if (failure === 'list') throw new Error('列表加载失败（隔离验证）'); return { list: rows.map(x => ({ ...x })), total: rows.length } }
api.announcementCursor = async () => ({ list: rows.map(x => ({ ...x })), hasMore: false, nextCursor: undefined })
api.announcementUnreadSummary = async () => { if (failure === 'summary') throw new Error('汇总加载失败（隔离验证）'); return { unreadCount: rows.filter(x => !x.read).length } }
api.announcement = async id => ({ ...rows.find(x => x.id === id)! })
api.markAnnouncementRead = async id => { if (failure === 'read') throw new Error('阅读确认失败（隔离验证）'); rows.find(x => x.id === id)!.read = true; return true }

function Fixture() {
  const [enabled, setEnabled] = useState(true)
  const navigate = useNavigate()
  return <><Space wrap><Button onClick={() => navigate('/')}>返回首页</Button><Button onClick={() => { rows.push({ ...rows[2], id: 5, title: '实时新公告', read: false }); socket?.onmessage?.({ data: JSON.stringify({ type: 'notice-published', content: {} }) }) }}>发布事件</Button>
    <Button onClick={() => { failure = 'list' }}>列表失败</Button><Button onClick={() => { failure = 'summary' }}>汇总失败</Button><Button onClick={() => { failure = 'read' }}>阅读失败</Button><Button onClick={() => { failure = '' }}>恢复接口</Button><Button onClick={() => setEnabled(x => !x)}>切换权限</Button>
    <Button onClick={() => { document.getElementById('calls')!.textContent = String(listCalls) }}>查询请求次数</Button><span id="calls" /></Space>
    <AnnouncementProvider enabled={enabled}><Routes><Route path="/" element={<div style={{ maxWidth: 720, padding: 16 }}><HomeAnnouncementPanel enabled={enabled} /></div>} /><Route path={APP_ROUTES.ANNOUNCEMENTS} element={<AnnouncementCenterPage permissions={['system:notice:read']} />} /></Routes></AnnouncementProvider></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter><RealtimeProvider platform="PC"><Fixture /></RealtimeProvider></MemoryRouter></App></ThemeProvider>)
