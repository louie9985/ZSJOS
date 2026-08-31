import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, type MenuTaskSummary } from '../services/api'
import { buildMenuTaskBadgeMap, type MenuTaskBadgeResolver } from '../services/menuTaskBadge'
import { useRealtime, useRealtimeEvent } from './RealtimeProvider'

type ContextValue = { summary?: MenuTaskSummary; loading: boolean; error: string; refresh: () => Promise<void>; resolve: MenuTaskBadgeResolver }
const Context = createContext<ContextValue | null>(null)

export default function MenuTaskBadgeProvider({ children, enabled = true }: PropsWithChildren<{ enabled?: boolean }>) {
  const { status } = useRealtime()
  const [summary, setSummary] = useState<MenuTaskSummary>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const refresh = useCallback(async () => {
    if (!enabled) return
    setLoading(true)
    try { setSummary(await api.menuTaskSummary()); setError('') } catch (e) { setError(e instanceof Error ? e.message : '菜单待办加载失败') } finally { setLoading(false) }
  }, [enabled])
  useEffect(() => { void refresh() }, [refresh])
  useRealtimeEvent('zsjos-workbench-task-invalidated', () => { void refresh() })
  useRealtimeEvent('notify-message-new', () => { void refresh() })
  useRealtimeEvent('zsjos_lead_assignment', () => { void refresh() })
  useEffect(() => {
    const ms = status === 'open' ? 60_000 : 15_000
    const timer = window.setInterval(() => void refresh(), ms)
    return () => window.clearInterval(timer)
  }, [refresh, status])
  const map = useMemo(() => buildMenuTaskBadgeMap(summary), [summary])
  const value = useMemo(() => ({ summary, loading, error, refresh, resolve: (path: string) => map.get(path) }), [error, loading, map, refresh, summary])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useMenuTaskBadges() { const value = useContext(Context); if (!value) throw new Error('useMenuTaskBadges must be used inside MenuTaskBadgeProvider'); return value }
