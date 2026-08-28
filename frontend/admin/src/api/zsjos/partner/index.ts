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
