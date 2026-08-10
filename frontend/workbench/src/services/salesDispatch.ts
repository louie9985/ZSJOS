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
