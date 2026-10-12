// UTF-8. Synthetic transport, isolated from business services.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import AnnouncementCenterPage from '../src/pages/AnnouncementCenterPage'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { api, http, ApiError } from '../src/services/api'
import '../src/styles/index.css'

const fixture = { lists: [] as string[], details: [] as number[], reads: [] as number[], failMore: false, failDetail: 0, denied: false, slowMore: false }
Object.assign(window, { noticeCenterFixture: fixture })
const notice = (id: number) => ({ id, title: `验收公告 ${id}`, type: 2, content: '<p>公告正文</p>'.repeat(90), sourceDeptName: id % 2 ? '测试来源' : null, publisherName: id % 2 ? '测试发布人' : null, audienceType: 'ALL', audienceSummary: '全体员工', attachments: [], read: false, publishTime: 1790000000000 })
api.markAnnouncementRead = async id => { fixture.reads.push(id); return true }
http.defaults.adapter = async config => {
  const path = config.url || ''
  const query = config.params || {}
  let data: unknown
  if (path.endsWith('/my-cursor') || path.endsWith('/my-page')) {
    const cursor = String(query.cursor || '0')
    fixture.lists.push(cursor)
    if (cursor !== '0' && fixture.slowMore) await new Promise(resolve => setTimeout(resolve, 800))
    if (cursor !== '0' && fixture.failMore) throw new Error('追加失败')
    const start = Number(cursor)
    const list = query.keyword === 'empty' ? [] : Array.from({ length: 20 }, (_, i) => notice(start + i + 1))
    data = { list, total: 60, hasMore: list.length > 0 && start < 40, ...(start < 40 ? { nextCursor: String(start + 20) } : {}) }
  } else if (path.endsWith('/my-get')) {
    const id = Number(query.id)
    fixture.details.push(id)
    await new Promise(resolve => setTimeout(resolve, id === 3 ? 300 : 40))
    if (fixture.denied) throw new ApiError(403, '无权查看此公告')
    if (fixture.failDetail === id) throw new Error('正文加载失败')
    data = notice(id)
  } else if (path.endsWith('/unread-summary')) data = { unreadCount: 60 }
  else throw new Error('Unexpected fixture request: ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><RealtimeProvider platform="PC"><AnnouncementProvider enabled>
  <div className="crm-shell tabs-enabled"><div className="content-layout" style={{ height: '100dvh' }}><div className="ant-layout-content" style={{ height: '100%' }}><AnnouncementCenterPage permissions={['system:notice:read']} /></div></div></div>
</AnnouncementProvider></RealtimeProvider></BrowserRouter></App></ThemeProvider>)
