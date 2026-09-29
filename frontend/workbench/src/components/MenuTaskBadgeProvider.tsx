import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, AuthenticationError, api, type MenuTaskSummary } from '../services/api'
import { MENU_TASK_INVALIDATED_EVENT } from '../services/menuTaskRefresh'
import { buildMenuTaskBadgeMap, type MenuTaskBadgeResolver } from '../services/menuTaskBadge'
import { useRealtime, useRealtimeEvent } from './RealtimeProvider'

type ContextValue = { summary?: MenuTaskSummary; loading: boolean; error: string; refresh: () => Promise<void>; resolve: MenuTaskBadgeResolver }
const Context = createContext<ContextValue | null>(null)

export default function MenuTaskBadgeProvider({ children, enabled = true }: PropsWithChildren<{ enabled?: boolean }>) {
  const { status } = useRealtime()
  const [summary, setSummary] = useState<MenuTaskSummary>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const generation = useRef(0)
  const refresh = useCallback(async () => {
    if (!enabled) return
    const request = ++generation.current
    setLoading(true)
    try {
      const result = await api.menuTaskSummary()
      if (request !== generation.current) return
      setSummary(result)
      setError('')
    } catch (e) {
      if (request !== generation.current) return
      if (e instanceof AuthenticationError || (e instanceof ApiError && (e.code === 401 || e.code === 403))) setSummary(undefined)
      setError(e instanceof Error ? e.message : '菜单待办加载失败')
    } finally {
      if (request === generation.current) setLoading(false)
    }
  }, [enabled])
  useEffect(() => {
    if (enabled) void refresh()
    else { setSummary(undefined); setLoading(false); setError('') }
    return () => { generation.current++ }
  }, [enabled, refresh])
  useRealtimeEvent('zsjos-workbench-task-invalidated', () => { void refresh() })
  useRealtimeEvent('notify-message-new', () => { void refresh() })
  useRealtimeEvent('zsjos_lead_assignment', () => { void refresh() })
  useEffect(() => {
    if (!enabled) return
    const ms = status === 'open' ? 60_000 : 15_000
    const timer = window.setInterval(() => void refresh(), ms)
    const onRefresh = () => { void refresh() }
    const onVisible = () => { if (document.visibilityState === 'visible') void refresh() }
    window.addEventListener('focus', onRefresh)
    window.addEventListener(MENU_TASK_INVALIDATED_EVENT, onRefresh)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', onRefresh)
      window.removeEventListener(MENU_TASK_INVALIDATED_EVENT, onRefresh)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [enabled, refresh, status])
  const map = useMemo(() => buildMenuTaskBadgeMap(summary), [summary])
  const value = useMemo(() => ({ summary, loading, error, refresh, resolve: (path: string) => map.get(path) }), [error, loading, map, refresh, summary])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useMenuTaskBadges() { const value = useContext(Context); if (!value) throw new Error('useMenuTaskBadges must be used inside MenuTaskBadgeProvider'); return value }
