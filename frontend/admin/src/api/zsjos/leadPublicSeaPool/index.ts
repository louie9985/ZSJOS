import request from '@/config/axios'
import type { Timestamp } from '../types'
import type { AdvancedFilterGroup } from '../advancedFilter'

export type LeadPublicSeaPoolStatus = 'waiting_assignment' | 'assigned' | 'deal_pending'
export interface LeadPublicSeaPoolVO {
  cycleId: number
  leadId: number
  leadNo: string
  cycleNo: number
  status: LeadPublicSeaPoolStatus
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
export interface LeadPublicSeaPoolPageReqVO extends PageParam {
  keyword?: string
  status?: LeadPublicSeaPoolStatus
  advancedFilter?: AdvancedFilterGroup
}
export const getPage = (params: LeadPublicSeaPoolPageReqVO): Promise<{ list: LeadPublicSeaPoolVO[]; total: number }> =>
  params.advancedFilter
    ? request.post({ url: '/zsjos/lead/public-sea-pool/search-page', data: params })
    : request.get({ url: '/zsjos/lead/public-sea-pool/page', params })
export const getCandidates = (cycleId: number): Promise<Array<{ id: number; nickname: string }>> =>
  request.get({ url: `/zsjos/lead/public-sea-pool/${cycleId}/candidates` })
export const assign = (cycleId: number, salesUserId: number) =>
  request.post({ url: `/zsjos/lead/public-sea-pool/${cycleId}/assign`, data: { salesUserId, idempotencyKey: crypto.randomUUID() } })
export const exit = (cycleId: number, reason: string) =>
  request.post({ url: `/zsjos/lead/public-sea-pool/${cycleId}/exit`, data: { reason, idempotencyKey: crypto.randomUUID() } })
