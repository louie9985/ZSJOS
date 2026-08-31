import type { SalesDispatchStatus } from './api'
import type { RealtimeStatus } from './realtime'

export const isDispatchPageActive = (
  realtimeStatus: RealtimeStatus,
  browserOnline: boolean,
  status?: SalesDispatchStatus
) => realtimeStatus === 'open' && browserOnline && status?.presence === 'online'

export const dispatchModeLabel = (status?: SalesDispatchStatus) =>
  status?.mode === 'accepting' ? '接单开启' : '接单暂停'

export const dispatchActionLabel = (status?: SalesDispatchStatus) =>
  status?.mode === 'accepting' ? '暂停接单' : '开启接单'

export type DispatchWarning = {
  kind: 'error' | 'offline' | 'paused'
  message: string
}

export const resolveDispatchWarning = (
  status: SalesDispatchStatus | undefined,
  loading: boolean,
  error: string,
  pageActive: boolean
): DispatchWarning | undefined => {
  if (error) return { kind: 'error', message: error }
  if (loading || !status?.eligible) return undefined
  if (!pageActive) return { kind: 'offline', message: '当前已【页面离线】，自动派单无法接收' }
  if (status.mode === 'paused') return { kind: 'paused', message: '当前已【暂停接单】，自动派单无法接收' }
  return undefined
}
