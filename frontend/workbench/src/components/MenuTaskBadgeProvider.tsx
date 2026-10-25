import { APP_ROUTES } from '../constants'
import { BUSINESS_APPROVAL_PROCESS_KEYS } from '../services/approvalNavigation'
import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, AuthenticationError, api, type MenuTaskSummary } from '../services/api'
import { MENU_TASK_INVALIDATED_EVENT } from '../services/menuTaskRefresh'
import { buildMenuTaskBadgeMap, type MenuTaskBadgeResolver } from '../services/menuTaskBadge'
import { useRealtime, useRealtimeEvent } from './RealtimeProvider'

type ContextValue = { summary?: MenuTaskSummary; loading: boolean; error: string; refresh: () => Promise<void>; resolve: MenuTaskBadgeResolver }
const Context = createContext<ContextValue | null>(null)

export default function MenuTaskBadgeProvider({ children, enabled = true, bpmEnabled = false }: PropsWithChildren<{ enabled?: boolean; bpmEnabled?: boolean }>) {
  const { status } = useRealtime()
  const [summary, setSummary] = useState<MenuTaskSummary>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [bpmCount, setBpmCount] = useState<number>()
  const bpmGeneration = useRef(0)
  const refreshBpm = useCallback(async () => {
    if (!enabled || !bpmEnabled) return
    const request = ++bpmGeneration.current
    try {
      const result = await api.bpmTaskPage('todo', { pageNo: 1, pageSize: 1, excludeProcessDefinitionKeys: BUSINESS_APPROVAL_PROCESS_KEYS })
      if (request === bpmGeneration.current) setBpmCount(result.total)
    } catch (error) {
      if (request === bpmGeneration.current && (error instanceof AuthenticationError || (error instanceof ApiError && [401, 403].includes(error.code)))) setBpmCount(undefined)
    }
  }, [enabled, bpmEnabled])
  useEffect(() => {
    setBpmCount(undefined)
    return () => { bpmGeneration.current++ }
  }, [enabled, bpmEnabled])
  const generation = useRef(0)
  const refreshBusiness = useCallback(async () => {
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
  const refresh = useCallback(async () => {
    await Promise.all([refreshBusiness(), refreshBpm()])
  }, [refreshBusiness, refreshBpm])
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
  const map = useMemo(() => {
    const result = buildMenuTaskBadgeMap(summary)
    if (enabled && bpmEnabled && bpmCount !== undefined) result.set(APP_ROUTES.BPM_TODO, { count: bpmCount })
    return result
  }, [summary, bpmCount, enabled, bpmEnabled])
  const value = useMemo(() => ({ summary, loading, error, refresh, resolve: (path: string) => map.get(path) }), [error, loading, map, refresh, summary])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useMenuTaskBadges() { const value = useContext(Context); if (!value) throw new Error('useMenuTaskBadges must be used inside MenuTaskBadgeProvider'); return value }
