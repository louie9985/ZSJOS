import request from '@/config/axios'
export { getStoredImpersonation, storeImpersonation } from '@/utils/impersonation'

export interface ImpersonationSessionVO {
  id: number
  administratorUserId: number
  administratorNameSnapshot: string
  targetUserId: number
  targetNameSnapshot: string
  reason: string
  status: 'active' | 'ended' | 'expired'
  startedAt: string
  lastActiveAt: string
}

export const startImpersonation = (targetUserId: number, reason: string) =>
  request.post<ImpersonationSessionVO>({
    url: '/zsjos/impersonation/start',
    data: { targetUserId, reason }
  })

export const endImpersonation = (id: number, reason = 'manual') =>
  request.post({ url: `/zsjos/impersonation/${id}/end`, params: { reason } })
