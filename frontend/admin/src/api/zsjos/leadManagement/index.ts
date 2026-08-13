import request from '@/config/axios'
import type { Timestamp } from '../types'
import type { AdvancedFilterGroup } from '../advancedFilter'

export interface LeadProductVO {
  id: number
  spuRef?: string
  spuName?: string
  skuRef?: string
  skuName?: string
  selectedAttrValues?: string
  price?: number
  categoryName?: string
  primary: boolean
}

export interface LeadAttachmentVO {
  id: number
  fileUrl: string
  originalName: string
  contentType: string
  fileSize: number
}

export interface LeadManagementVO {
  id: number
  personId: number
  submittedName: string
  submittedMobile?: string
  submittedWechatId?: string
  sourceType: string
  sourceUserId?: number
  sourceUserName?: string
  sourceChannel?: string
  provinceCode?: string
  provinceName?: string
  cityCode?: string
  cityName?: string
  leadCategory?: string
  remark?: string
  status: string
  assignmentStatus: string
  handlingStage: string
  dispatchMode?: string
  ownerUserId?: number
  ownerUserName?: string
  pendingAssigneeUserId?: number
  pendingAssigneeUserName?: string
  pendingExpiresAt?: Timestamp
  assignmentAttemptCount?: number
  publicPoolAt?: Timestamp
  submittedAt: Timestamp
  currentAssignmentFirstFollowUpAt?: Timestamp
  currentAssignmentFirstFollowUpDeadlineAt?: Timestamp
  qualificationStartedAt?: Timestamp
  qualificationDeadlineAt?: Timestamp
  suspendedAt?: Timestamp
  qualifiedByUserId?: number
  qualifiedByUserName?: string
  convertedAt?: Timestamp
  invalidReason?: string
  invalidReasonLabelSnapshot?: string
  invalidDescription?: string
  recycleSourceOwnerUserId?: number
  recycleSourceOwnerUserName?: string
  appealDeadlineAt?: Timestamp
  closedAt?: Timestamp
  closeReason?: string
  createTime: Timestamp
  updateTime: Timestamp
  relationTypes: Array<'submitter' | 'owner'>
  primaryProduct?: LeadProductVO
  intendedProducts?: LeadProductVO[]
  attachments?: LeadAttachmentVO[]
}

export interface LeadManagementPageReqVO extends PageParam {
  keyword?: string
  status?: string
  assignmentStatus?: string
  sourceChannel?: string
  leadCategory?: string
  sourceUserId?: number
  ownerUserId?: number
  submittedAt?: string[]
  advancedFilter?: AdvancedFilterGroup
}

export interface LeadQualificationExceptionVO {
  id: number
  submittedName: string
  submittedMobile?: string
  status: string
  assignmentStatus: string
  handlingStage: string
  ownerUserId?: number
  ownerUserName?: string
  recycleSourceOwnerUserId?: number
  recycleSourceOwnerUserName?: string
  qualificationDeadlineAt?: Timestamp
  suspendedAt?: Timestamp
}

export interface LeadTransferCandidateVO {
  id: number
  nickname: string
  deptName?: string
}

export interface VisibleUserVO {
  id: number
  nickname: string
  deptId?: number
  deptName?: string
}

export const LEAD_STATUS_OPTIONS = [
  { label: '已提交', value: 'submitted' }, { label: '已挂起', value: 'suspended' },
  { label: '有效', value: 'valid' }, { label: '无效', value: 'invalid' },
  { label: '已转换', value: 'converted' }, { label: '已关闭', value: 'closed' }
]

export const ASSIGNMENT_STATUS_OPTIONS = [
  { label: '未分配', value: 'unassigned' },
  { label: '待接单', value: 'pending_acceptance' },
  { label: '已归属', value: 'owned' },
  { label: '抢单池', value: 'public_pool' }, { label: '回收待处理', value: 'recycle_pending' }
]

export const DISPATCH_MODE_LABELS: Record<string, string> = {
  auto: '自动分配',
  specified: '指定销售'
}

export const getLeadPage = (params: LeadManagementPageReqVO) =>
  params.advancedFilter ? request.post({ url: '/zsjos/lead/search-page', data: params }) : request.get({ url: '/zsjos/lead/page', params })

export const getLead = (id: number): Promise<LeadManagementVO> =>
  request.get({ url: '/zsjos/lead/get', params: { id } })

export const getVisibleUsers = (): Promise<VisibleUserVO[]> =>
  request.get({ url: '/zsjos/lead/visible-users' })

export const judgeValid = (id: number, idempotencyKey: string) => request.post({ url: `/zsjos/lead/${id}/judge-valid`, data: { idempotencyKey } })
export const judgeInvalid = (id: number, data: { reasonCode: string; description: string; idempotencyKey: string }) => request.post({ url: `/zsjos/lead/${id}/judge-invalid`, data })
export const getTransferCandidates = (id: number): Promise<LeadTransferCandidateVO[]> => request.get({ url: `/zsjos/lead/${id}/transfer-candidates` })
export const restoreLead = (id: number, data: { reason: string; idempotencyKey: string }) => request.post({ url: `/zsjos/lead/${id}/restore`, data })
export const transferLead = (id: number, data: { salesUserId: number; reason: string; idempotencyKey: string }) => request.post({ url: `/zsjos/lead/${id}/transfer`, data })
export const recycleLead = (id: number, data: { reason: string; idempotencyKey: string }) => request.post({ url: `/zsjos/lead/${id}/recycle`, data })
export const releaseLead = (id: number, data: { reason: string; idempotencyKey: string }) => request.post({ url: `/zsjos/lead/${id}/release-to-claim-pool`, data })
export const getQualificationExceptionPage = (params: PageParam & { type: 'suspended' | 'recycle_pending' }): Promise<PageResult<LeadQualificationExceptionVO[]>> => request.get({ url: '/zsjos/lead/qualification-exception/page', params })
