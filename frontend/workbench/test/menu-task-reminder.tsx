import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, Menu } from 'antd'
import MenuTaskBadgeProvider, { useMenuTaskBadges } from '../src/components/MenuTaskBadgeProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { api, ApiError, AuthenticationError, http, writeSharedTenantId, type WorkbenchMenu } from '../src/services/api'
import { buildNavMenuItems, buildHierarchicalSecondaryItems } from '../src/layouts/navItems'
import { buildTwoLevelNavigation } from '../src/services/menu'
import { AUTH_STORAGE_KEYS } from '../src/constants'
import '../src/styles/index.css'

// Isolated synthetic transport; this fixture never writes real business data.
const state = { count: 2, calls: 0, commandCalls: 0, failure: 0, delay: 0, commandFailure: false }
let socket: FixtureSocket | undefined
class FixtureSocket {
  static OPEN = 1
  readyState = 1
  onopen?: () => void
  onmessage?: (event: { data: string }) => void
  onclose?: () => void
  constructor() { socket = this; setTimeout(() => this.onopen?.(), 0) }
  send() {}
  close() { this.readyState = 3; this.onclose?.() }
}
localStorage.setItem(AUTH_STORAGE_KEYS.PC.accessToken, 'isolated-fixture')
writeSharedTenantId('1')
window.WebSocket = FixtureSocket as unknown as typeof WebSocket
http.defaults.adapter = async config => {
  if (config.method === 'post' && ['/follow-ups', '/judge-valid', '/judge-invalid'].some(suffix => config.url?.endsWith(suffix))) {
    state.commandCalls++
    if (state.commandFailure) throw new ApiError(409, 'fixture command rejected')
    state.count = Math.max(0, state.count - 1)
    return { config, headers: {}, status: 200, statusText: 'OK', data: { code: 0, data: true } }
  }
  if (config.url !== '/zsjos/business-task/menu-task-summary') throw new Error('Unexpected fixture request')
  state.calls++
  const { count, failure, delay } = state
  if (delay) await new Promise(resolve => setTimeout(resolve, delay))
  if (failure === 401) throw new AuthenticationError('fixture session expired')
  if (failure) throw new ApiError(failure, 'fixture request failed')
  return { config, headers: {}, status: 200, statusText: 'OK', data: { code: 0, data: {
    generatedAt: Date.now(), total: count,
    items: count ? [{ menuPath: '/zsjos/leads/manage', count, severity: 'normal', sourceTypes: ['lead_first_follow_up'] }] : []
  } } }
}
Object.assign(window, { reminderFixture: { state,
  event: () => socket?.onmessage?.({ data: JSON.stringify({ type: 'zsjos-workbench-task-invalidated', content: {} }) }),
  disconnect: () => { localStorage.removeItem(AUTH_STORAGE_KEYS.PC.accessToken); socket?.close() },
  command: (type: string) => type === 'valid' ? api.judgeLeadValid(42, { remark: 'fixture', idempotencyKey: 'fixture' })
    : type === 'invalid' ? api.judgeLeadInvalid(42, { reasonCode: 'fixture', description: 'fixture', attachments: [], idempotencyKey: 'fixture' })
    : api.createLeadFollowUp(42, {} as Parameters<typeof api.createLeadFollowUp>[1])
} })
const leaf = { id: 2, name: '验收菜单', path: '/zsjos/leads/manage', children: [] } as unknown as WorkbenchMenu
const parent = { id: 1, name: '验收分组', path: '/fixture', children: [leaf] } as unknown as WorkbenchMenu
const navigation = buildTwoLevelNavigation([parent])
function View() {
  const { summary, loading, error, resolve, refresh } = useMenuTaskBadges()
  return <main style={{ padding: 24 }}>
    <h2>菜单任务提醒验收</h2>
    <output data-testid="count">{summary?.total ?? 'none'}</output>
    <output data-testid="loading">{String(loading)}</output>
    <output data-testid="error">{error}</output>
    <button onClick={() => void refresh()}>刷新</button>
    <section data-testid="flat-menu"><Menu items={buildNavMenuItems(navigation, { badgeResolver: resolve })} mode="inline" defaultOpenKeys={['1']} /></section>
    <section data-testid="secondary-menu"><Menu items={buildHierarchicalSecondaryItems(parent, { badgeResolver: resolve })} mode="inline" /></section>
    <section data-testid="top-menu"><Menu items={buildNavMenuItems(navigation, { badgeResolver: resolve })} mode="horizontal" /></section>
    <section data-testid="mini-menu"><Menu items={buildNavMenuItems(navigation, { badgeResolver: resolve, groupChildren: true })} mode="inline" defaultOpenKeys={['1']} /></section>
  </main>
}
function Fixture() {
  const [enabled, setEnabled] = useState(true)
  return <ConfigProvider><RealtimeProvider platform="PC"><button onClick={() => setEnabled(value => !value)}>切换权限</button><MenuTaskBadgeProvider enabled={enabled}><View /></MenuTaskBadgeProvider></RealtimeProvider></ConfigProvider>
}
createRoot(document.getElementById('root')!).render(<Fixture />)
