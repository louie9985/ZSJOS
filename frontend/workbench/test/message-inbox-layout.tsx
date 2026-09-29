// UTF-8. Test-only transport: every request is answered locally or rejected.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import MessageInboxPage from '../src/pages/MessageInboxPage'
import MessageDetail from '../src/components/MessageDetail'
import { ThemeStateContext, type ThemeContextValue } from '../src/components/Theme/ThemeContext'
import { NotifyMessageProvider } from '../src/components/NotifyMessageProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { DEFAULT_THEME } from '../src/constants'
import { http, AuthenticationError, type NotifyMessage } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const view = params.get('view') === 'unread' ? 'unread' : 'all'
const categories = [{ key: 'all', label: '全部' }, { key: 'system', label: '系统' }, { key: 'lead', label: '客资' }, { key: 'appeal', label: '申诉' }, { key: 'withdrawal', label: '提现' }]
let failNext = params.get('state') === 'error'
const messages: NotifyMessage[] = params.get('state') === 'empty' ? [] : Array.from({ length: 8 }, (_, i) => ({
  id: i + 1, templateNickname: i === 0 ? '中世健消息布局验收专用发送人长名称' : '验收发送人' + (i + 1),
  templateTitle: i === 0 ? '消息布局验收：长标题自动换行、正文与右侧状态保持对应' : '验收消息 ' + (i + 1),
  templateSummary: '第 ' + (i + 1) + ' 条消息的摘要，用于确认当前选中消息。',
  templateContent: i === 0 ? ('这是一条用于隔离验收的消息正文，不对应真实业务记录。内容应完整显示，并在正文区域滚动。'.repeat(24) + 'https://example.com/' + 'long-segment'.repeat(28)) : '第 ' + (i + 1) + ' 条消息的完整正文。',
  templateType: 1, category: i % 2 ? 'appeal' : 'system', readStatus: i > 1,
  createTime: Date.parse('2026-09-27T09:30:00+08:00'), readTime: i > 1 ? Date.parse('2026-09-27T10:30:00+08:00') : undefined, actionType: 'none'
}))

// Even if this origin has a login session, never establish a real socket.
class FixtureSocket {
  static OPEN = 1
  readyState = 1
  onopen: ((event: Event) => void) | null = null
  onclose: (() => void) | null = null
  onmessage = null
  onerror = null
  constructor() { queueMicrotask(() => this.onopen?.(new Event('open'))) }
  send() {}
  close() {}
}
window.WebSocket = FixtureSocket as unknown as typeof WebSocket

http.defaults.adapter = async config => {
  const url = config.url || ''
  const respond = (data: unknown) => ({ config, data: { code: 0, data }, status: 200, statusText: 'OK', headers: {} })
  if (url.endsWith('/my-categories')) return respond(categories)
  if (url.endsWith('/get-unread-count')) return respond(messages.filter(m => !m.readStatus).length)
  if (url.includes('runtime-setting')) return respond({ notificationPopupDurationMinutes: 5 })
  if (url.endsWith('/my-cursor') || url.endsWith('/my-page')) {
    if (params.get('state') === 'unauthorized') throw new AuthenticationError()
    if (params.get('state') === 'loading') await new Promise(() => {})
    if (failNext) { failNext = false; throw new Error('验收：消息加载失败') }
    const p = config.params || {}
    const list = messages.filter(m => (p.readStatus !== false || !m.readStatus) && (!p.category || m.category === p.category) && (!p.keyword || [m.templateTitle, m.templateSummary, m.templateContent].join(' ').includes(p.keyword)))
    return respond({ list: structuredClone(list), total: list.length, hasMore: false })
  }
  if (url.endsWith('/my-get')) return respond(structuredClone(messages.find(m => m.id === Number(config.params.id))))
  if (url.endsWith('/update-read') || url.endsWith('/update-all-read')) {
    const ids = config.params instanceof URLSearchParams ? config.params.getAll('ids').map(Number) : []
    messages.forEach(m => { if (url.endsWith('/update-all-read') || ids.includes(m.id)) { m.readStatus = true; m.readTime = Date.parse('2026-09-27T11:30:00+08:00') } })
    return respond(true)
  }
  throw new Error('Unexpected isolated fixture request: ' + url)
}

const noop = () => {}
const theme: ThemeContextValue = {
  ...DEFAULT_THEME, inboxLayoutMode: params.get('mode') === 'table' ? 'table' : 'split',
  isDark: false, customizable: true,
  setPreset: noop, setColorPrimary: noop, setCompact: noop, setBackground: noop, setGlassOpacity: noop, setGlassBlur: noop, setDensity: noop, setFontScale: noop, setLayoutMode: noop, setBorderRadius: noop, setHeaderFixed: noop, setAnimation: noop, setWatermark: noop, setTabs: noop, setTabStyle: noop, setInboxLayoutMode: noop, reset: noop
}

function Fixture() {
  const [currentView, setView] = useState<'all' | 'unread'>(view)
  return <ThemeStateContext.Provider value={theme}><ConfigProvider locale={zhCN}><App><MemoryRouter><RealtimeProvider platform="PC"><NotifyMessageProvider>
    <div style={{ height: '100dvh', display: 'flex', flexDirection: 'column', padding: 'var(--crm-page-pad)', boxSizing: 'border-box', background: 'var(--crm-bg-layout)' }}>
      <nav aria-label="验收视图" style={{ flex: 'none' }}><button onClick={() => setView('all')}>验收全部消息</button><button onClick={() => setView('unread')}>验收未读消息</button></nav>
      <div style={{ flex: 1, minHeight: 0 }}>
        {params.get('mode') === 'detail' ? <MessageDetail message={messages[0]} categories={categories} businessAction onOpenLead={() => {}}/> : <MessageInboxPage view={currentView}/>}
      </div>
    </div>
  </NotifyMessageProvider></RealtimeProvider></MemoryRouter></App></ConfigProvider></ThemeStateContext.Provider>
}

createRoot(document.getElementById('root')!).render(<Fixture/>)
