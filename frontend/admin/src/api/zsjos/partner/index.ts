import request from '@/config/axios'

export interface PartnerVO {
  id: number
  partnerNo: string
  name: string
  mobile: string
  status: 'enabled' | 'disabled' | 'converted'
  boundSystemUserId: number
  channelId?: string
  enabledAt?: string
  disabledAt?: string
  assignedEmployeeUserId?: number
  assignedEmployeeName?: string
  assignedAt?: string
  assignmentVersion?: number
  assignmentEffective?: boolean
}

export interface PartnerCreateVO {
  partnerNo: string
  name: string
  mobile: string
  password: string
  channelId?: string
}

export const getPartnerList = () => request.get<PartnerVO[]>({ url: '/zsjos/partner/list' })
export const getPartnerPage = (params: {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
}) => request.get<{ list: PartnerVO[]; total: number }>({ url: '/zsjos/partner/page', params })
export const createPartner = (data: PartnerCreateVO) =>
  request.post({ url: '/zsjos/partner/create', data })
export const disablePartner = (id: number, reason: string) =>
  request.put({ url: `/zsjos/partner/${id}/disable`, data: { reason } })
export const enablePartner = (id: number, reason: string) =>
  request.put({ url: `/zsjos/partner/${id}/enable`, data: { reason } })
export const convertPartner = (
  id: number,
  data: {
    targetType: string
    username: string
    password: string
    deptId: number
    migrateHistoricalOrganization: boolean
    reason: string
  }
) => request.post({ url: `/zsjos/partner/${id}/convert`, data })
export const updatePartnerMobile = (id: number, mobile: string) =>
  request.put({ url: `/zsjos/partner/${id}/mobile`, data: { mobile } })
export const resetPartnerPassword = (id: number, password: string) =>
  request.put({ url: `/zsjos/partner/${id}/reset-password`, data: { password } })
export interface AssignmentCandidateVO {
  id: number
  nickname: string
  deptId?: number
  status: number
}
export const getAssignmentCandidates = () =>
  request.get<AssignmentCandidateVO[]>({ url: '/zsjos/partner/assignment-candidates' })
export const updateAssignment = (
  id: number,
  data: { assignedUserId?: number; reason: string; expectedVersion?: number }
) => request.put({ url: `/zsjos/partner/${id}/assignment`, data })
export interface AssignmentLogVO {
  id: number
  previousEmployeeName?: string
  employeeName?: string
  reason: string
  operatorName?: string
  occurredAt: string
}
export const getAssignmentLogPage = (id: number) =>
  request.get<{ list: AssignmentLogVO[]; total: number }>({
    url: `/zsjos/partner/${id}/assignment-log/page`,
    params: { pageNo: 1, pageSize: 100 }
  })

export interface PartnerInvitationVO {
  id: number
  inviteCode: string
  name: string
  mobile: string
  assignedOperatorUserId: number
  assignedOperatorName?: string
  status: 'active' | 'used' | 'voided' | 'expired'
  expiresAt: string
  usedAt?: string
  voidedAt?: string
  partnerId?: number
  createdByUserId?: number
  createdByName?: string
  createTime: string
  version: number
}

export interface PartnerInvitationCreateVO {
  name: string
  mobile: string
  assignedOperatorUserId?: number
}

export const getInvitationPage = (params: {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
  assignedOperatorUserId?: number
}) =>
  request.get<{ list: PartnerInvitationVO[]; total: number }>({
    url: '/zsjos/partner-invitation/page',
    params
  })

export const createInvitation = (data: PartnerInvitationCreateVO) =>
  request.post<PartnerInvitationVO>({ url: '/zsjos/partner-invitation/create', data })

export const voidInvitation = (id: number) =>
  request.put({ url: `/zsjos/partner-invitation/${id}/void` })

export const getInvitationOperatorCandidates = (params?: {
  keyword?: string
  pageNo?: number
  pageSize?: number
}) =>
  request.get<{ list: AssignmentCandidateVO[]; total: number }>({
    url: '/zsjos/partner-invitation/operator-candidates',
    params: { pageNo: 1, pageSize: 100, ...params }
  })
