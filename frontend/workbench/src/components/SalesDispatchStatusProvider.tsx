import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, type SalesDispatchStatus } from '../services/api'
import { isDispatchPageActive } from '../services/salesDispatch'
import { useRealtime } from './RealtimeProvider'

const HEARTBEAT_INTERVAL_MS = 30_000

type SalesDispatchStatusContextValue = {
  enabled: boolean
  status?: SalesDispatchStatus
  loading: boolean
  updating: boolean
  error: string
  pageActive: boolean
  refresh: () => Promise<void>
  setAccepting: (accepting: boolean) => Promise<void>
}

const SalesDispatchStatusContext = createContext<SalesDispatchStatusContextValue | undefined>(undefined)

export function SalesDispatchStatusProvider({ canAccept, children }: { canAccept: boolean; children: ReactNode }) {
  const { status: realtimeStatus } = useRealtime()
  const [status, setStatus] = useState<SalesDispatchStatus>()
  const [browserOnline, setBrowserOnline] = useState(() => navigator.onLine)
  const [loading, setLoading] = useState(canAccept)
  const [updating, setUpdating] = useState(false)
  const [error, setError] = useState('')

  const heartbeat = useCallback(async () => {
    if (!canAccept || realtimeStatus !== 'open' || !navigator.onLine) return
    try {
      setStatus(await api.dispatchHeartbeat())
      setError('')
    } catch (heartbeatError) {
      setError(heartbeatError instanceof Error ? heartbeatError.message : '接单状态刷新失败')
    } finally {
      setLoading(false)
    }
  }, [canAccept, realtimeStatus])

  const refresh = useCallback(async () => {
    if (!canAccept) return
    setLoading(true)
    try {
      const current = await api.myDispatchStatus()
      setStatus(current)
      setError('')
      if (realtimeStatus === 'open' && navigator.onLine) {
        setStatus(await api.dispatchHeartbeat())
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '接单状态加载失败')
    } finally {
      setLoading(false)
    }
  }, [canAccept, realtimeStatus])

  useEffect(() => { void refresh() }, [refresh])

  useEffect(() => {
    const updateOnline = () => setBrowserOnline(navigator.onLine)
    window.addEventListener('online', updateOnline)
    window.addEventListener('offline', updateOnline)
    return () => {
      window.removeEventListener('online', updateOnline)
      window.removeEventListener('offline', updateOnline)
    }
  }, [])

  useEffect(() => {
    if (!canAccept || realtimeStatus !== 'open' || !browserOnline) {
      if (canAccept && status?.presence === 'online') {
        void api.dispatchOffline().then(setStatus).catch(() => undefined)
      }
      return
    }
    void heartbeat()
    const timer = window.setInterval(() => void heartbeat(), HEARTBEAT_INTERVAL_MS)
    const refreshVisible = () => { if (document.visibilityState === 'visible') void heartbeat() }
    window.addEventListener('focus', refreshVisible)
    document.addEventListener('visibilitychange', refreshVisible)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', refreshVisible)
      document.removeEventListener('visibilitychange', refreshVisible)
    }
  }, [browserOnline, canAccept, heartbeat, realtimeStatus, status?.presence])

  const setAccepting = useCallback(async (accepting: boolean) => {
    if (!status || !isDispatchPageActive(realtimeStatus, browserOnline, status)) return
    setUpdating(true)
    try {
      setStatus(await api.updateDispatchMode(accepting))
      setError('')
    } catch (updateError) {
      const text = updateError instanceof Error ? updateError.message : '接单状态更新失败'
      setError(text)
      throw updateError
    } finally {
      setUpdating(false)
    }
  }, [browserOnline, realtimeStatus, status])

  const value = useMemo<SalesDispatchStatusContextValue>(() => ({
    enabled: canAccept,
    status,
    loading,
    updating,
    error,
    pageActive: isDispatchPageActive(realtimeStatus, browserOnline, status),
    refresh,
    setAccepting
  }), [browserOnline, canAccept, error, loading, realtimeStatus, refresh, setAccepting, status, updating])

  return <SalesDispatchStatusContext.Provider value={value}>{children}</SalesDispatchStatusContext.Provider>
}

export function useSalesDispatchStatus() {
  const context = useContext(SalesDispatchStatusContext)
  if (!context) throw new Error('useSalesDispatchStatus must be used within SalesDispatchStatusProvider')
  return context
}
