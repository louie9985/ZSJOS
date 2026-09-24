import request from '@/config/axios'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'

export interface WithdrawalPageQuery {
  pageNo: number
  pageSize: number
  status?: string
  keyword?: string
  advancedFilter?: AdvancedFilterGroup
  readScope?: 'SELF' | 'ALL' | 'USER'
  targetUserId?: number
}

export interface WithdrawalVO {
  applicantName?: string; partnerName?: string; paidByName?: string; cashbackCount?: number
  availableBalanceSnapshot?: number; approvedAmount?: number
  version?: number
  reviewedByName?: string
  reviewedAt?: string
  reviewReason?: string
  availableActions?: string[]
  reviewUnavailableReason?: string
  id: number
  withdrawalNo: string
  applicantUserId: number
  status: string
  verificationStatus: string
  applicationAmount: number
  accountNameSnapshot: string
  maskedCardNumber: string
  cardNumber?: string
  bankNameSnapshot: string
  branchNameSnapshot?: string
  submittedAt: string
  rejectionReason?: string
  bankTransactionNo?: string
  proofUrl?: string
  payoutRemark?: string
  paidAt?: string
  applicantName?: string
  partnerName?: string
  paidByName?: string
  cashbackCount?: number
  availableBalanceSnapshot?: number
  approvedAmount?: number
}
export const getPage = (params: WithdrawalPageQuery) => request.get({ url: '/zsjos/withdrawal/page', params })
export const getMyPage = (params: WithdrawalPageQuery) => request.get({ url: '/zsjos/withdrawal/my-page', params })
export const getDetail = (id: number) => request.get({ url: `/zsjos/withdrawal/${id}` })
export const getMyDetail = (id: number) => request.get({ url: `/zsjos/withdrawal/my/${id}` })
export const getFinanceDetail = (id: number) =>
  request.get({ url: `/zsjos/withdrawal/${id}/finance-detail` })
export const apply = (data: any) => request.post({ url: '/zsjos/withdrawal/apply', data })
export const cancel = (id: number) => request.put({ url: `/zsjos/withdrawal/${id}/cancel` })
export const rejectApproved = (id: number, reason: string) =>
  request.put({ url: `/zsjos/withdrawal/${id}/reject-approved`, data: { reason } })
export interface WithdrawalPayoutRequest {
  paidAt?: string
  remark?: string
}
export const payout = (id: number, data: WithdrawalPayoutRequest) =>
  request.put({ url: `/zsjos/withdrawal/${id}/payout`, data })
export const batchPayout = (data: WithdrawalPayoutRequest & { ids: number[] }) =>
  request.put({ url: '/zsjos/withdrawal/batch-payout', data })
export const uploadProof = (data: FormData) =>
  request.upload({ url: '/zsjos/withdrawal/proof/upload', data })

export const review = (id: number, approve: boolean, data: { version: number; reason?: string }) =>
  request.put({ url: `/zsjos/withdrawal/${id}/${approve ? 'approve' : 'reject'}`, data })
export const taskTarget = (taskId: string, view: 'todo' | 'done' = 'todo') =>
  request.get({ url: '/zsjos/bpm/business-task-target', params: { taskId, view } })

export const searchPage = (data: WithdrawalPageQuery) => request.post({ url: '/zsjos/withdrawal/search-page', data })
export const searchMyPage = (data: WithdrawalPageQuery) => request.post({ url: '/zsjos/withdrawal/my-search-page', data })

export interface WithdrawalSource { id: number; amount: number; active: boolean; cashback?: import('../cashback').CashbackVO }
export const getSources = (id: number, pageNo = 1): Promise<{ list: WithdrawalSource[]; total: number }> => request.get({ url: `/zsjos/withdrawal/${id}/sources`, params: { pageNo, pageSize: 10 } })
