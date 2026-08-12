import { App, Button, Space, Tag, Tooltip } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, type SalesDispatchStatus } from '../services/api'
import { dispatchActionLabel, dispatchModeLabel, isDispatchPageActive } from '../services/salesDispatch'
import { useRealtime } from './RealtimeProvider'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'

const HEARTBEAT_INTERVAL_MS = 30_000

export default function SalesDispatchStatusControl({ canAccept }: { canAccept: boolean }) {
  const { message } = App.useApp()
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

  const load = useCallback(async () => {
    if (!canAccept) return
    setLoading(true)
    try {
      const current = await api.myDispatchStatus()
      setStatus(current)
      setError('')
      if (realtimeStatus === 'open' && navigator.onLine) setStatus(await api.dispatchHeartbeat())
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '接单状态加载失败')
    } finally {
      setLoading(false)
    }
  }, [canAccept, realtimeStatus])

  useEffect(() => { void load() }, [load])

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

  const pageActive = isDispatchPageActive(realtimeStatus, browserOnline, status)
  const eligible = status?.eligible !== false
  const title = useMemo(() => error || (pageActive ? '页面心跳正常' : '页面离线期间不会收到自动派单'), [error, pageActive])

  if (!canAccept || (!loading && !eligible)) return null

  const toggleMode = async () => {
    if (!status || !pageActive) return
    setUpdating(true)
    try {
      setStatus(await api.updateDispatchMode(status.mode !== 'accepting'))
      setError('')
    } catch (updateError) {
      const text = updateError instanceof Error ? updateError.message : '接单状态更新失败'
      setError(text)
      message.error(text)
    } finally {
      setUpdating(false)
    }
  }

  return <Tooltip title={title}>
    <Space size={8} className="sales-dispatch-control" aria-label="销售接单状态">
      <Tag color={status?.mode === 'accepting' ? 'success' : 'default'} className="dispatch-status-tag">
        {loading ? '状态加载' : dispatchModeLabel(status)}
      </Tag>
      <Tag color={pageActive ? 'processing' : 'default'} className="dispatch-status-tag">
        {pageActive ? '页面活跃' : '页面离线'}
      </Tag>
      <IrreversiblePopconfirm action="暂停接单" open={status?.mode === 'accepting' ? undefined : false} onConfirm={toggleMode} disabled={status?.mode !== 'accepting'}><Button className="dispatch-mode-button" disabled={!pageActive || loading} loading={updating}
        onClick={() => error && !status ? void load() : status?.mode === 'accepting' ? undefined : void toggleMode()}>
        {error && !status ? '重试状态' : dispatchActionLabel(status)}
      </Button></IrreversiblePopconfirm>
    </Space>
  </Tooltip>
}
