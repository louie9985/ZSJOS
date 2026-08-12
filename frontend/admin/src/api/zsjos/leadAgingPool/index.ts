import request from '@/config/axios'
import type { Timestamp } from '../types'

export type LeadAgingPoolStatus = 'waiting_assignment' | 'assigned' | 'deal_pending'
export interface LeadAgingPoolVO {
  cycleId: number
  leadId: number
  cycleNo: number
  status: LeadAgingPoolStatus
  originalOwnerUserId: number
  originalOwnerUserName?: string
  collaboratorUserId?: number
  collaboratorUserName?: string
  frozenDeptId: number
  frozenDeptName?: string
  submittedName: string
  submittedMobile?: string
  submittedWechatId?: string
  leadCategory?: string
  sourceChannel?: string
  ownershipStartedAt: Timestamp
  dueAt: Timestamp
  enteredAt: Timestamp
  assignedAt?: Timestamp
  lastFollowUpAt?: Timestamp
  nextFollowUpAt?: Timestamp
  activeSalesOrderId?: number
  activeSalesOrderStatus?: string
  availableActions: string[]
}
export interface LeadAgingPoolPageReqVO extends PageParam {
  keyword?: string
  status?: LeadAgingPoolStatus
}
export const getPage = (params: LeadAgingPoolPageReqVO): Promise<{ list: LeadAgingPoolVO[]; total: number }> =>
  request.get({ url: '/zsjos/lead/aging-pool/page', params })
export const getCandidates = (cycleId: number): Promise<Array<{ id: number; nickname: string }>> =>
  request.get({ url: `/zsjos/lead/aging-pool/${cycleId}/candidates` })
export const assign = (cycleId: number, salesUserId: number) =>
  request.post({ url: `/zsjos/lead/aging-pool/${cycleId}/assign`, data: { salesUserId, idempotencyKey: crypto.randomUUID() } })
export const exit = (cycleId: number, reason: string) =>
  request.post({ url: `/zsjos/lead/aging-pool/${cycleId}/exit`, data: { reason, idempotencyKey: crypto.randomUUID() } })
