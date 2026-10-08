// UTF-8. Actual component/providers with isolated synthetic API responses; no business writes.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import HomeAnnouncementPanel from '../src/components/HomeAnnouncementPanel'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { api, ApiError, type Announcement } from '../src/services/api'
import '../src/styles/index.css'

const fixture = { total: 23, fail: false, denied: false, delay: 0, overlap: false, calls: [] as { pageNo: number; pageSize: number }[] }
Object.assign(window, { announcementFixture: fixture })
api.announcementUnreadSummary = async () => ({ unreadCount: 3 })
api.announcementPage = async params => {
  fixture.calls.push(params)
  const { total, fail, denied, delay, overlap } = fixture
  await new Promise(resolve => setTimeout(resolve, delay))
  if (denied) throw new ApiError(403, '无权限')
  if (fail) throw new Error('模拟公告网络错误')
  const start = (params.pageNo - 1) * params.pageSize
  const list: Announcement[] = Array.from({ length: Math.max(0, Math.min(params.pageSize, total - start)) }, (_, index) => {
    const id = start + index + 1 - (overlap && params.pageNo > 1 ? 1 : 0)
    return { id, title: `中视健公告 ${id}：员工通知与业务安排`, type: 1, read: id % 2 === 0, highlighted: id === 1, publishTime: '2026-10-01 09:00:00', content: '', attachments: [] }
  })
  return { list, total }
}
function Fixture() {
  const [enabled, setEnabled] = useState(true)
  return <><Button onClick={() => setEnabled(value => !value)}>切换权限</Button>
    <AnnouncementProvider enabled={enabled}><div style={{ padding: 16, maxWidth: 520, height: 560, display: 'flex', flexDirection: 'column' }}>
      <HomeAnnouncementPanel enabled={enabled} />
    </div></AnnouncementProvider></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter><RealtimeProvider platform="PC"><Fixture /></RealtimeProvider></MemoryRouter></App></ThemeProvider>)
