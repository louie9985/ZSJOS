import request from '@/config/axios'

export interface PageResult<T> { list: T[]; total: number }
export interface SubordinatePartnerVO { id: number; partnerNo: string; name: string; mobile?: string; status: string; assignedAt?: string }
export interface SubordinateLeadVO { id: number; leadNo: string; submittedName: string; submittedMobile?: string; status: string; ownerUserName?: string; partnerOwnerNameSnapshot?: string; submittedAt?: string; remark?: string; sourceLabel?: string; leadCategoryLabelSnapshot?: string }

export const getPage = (params: Record<string, unknown>) =>
  request.get<PageResult<SubordinatePartnerVO>>({ url: '/zsjos/subordinate-partners/page', params })
export const getLeadPage = (partnerId: number, params: Record<string, unknown>) =>
  request.get<PageResult<SubordinateLeadVO>>({ url: `/zsjos/subordinate-partners/${partnerId}/leads/page`, params })
export const getLead = (leadId: number) =>
  request.get<SubordinateLeadVO>({ url: `/zsjos/subordinate-partners/leads/${leadId}` })
