import request from '@/config/axios'

export interface WithdrawalVO {
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
}
export const getPage = (params: any) => request.get({ url: '/zsjos/withdrawal/page', params })
export const getMyPage = (params: any) => request.get({ url: '/zsjos/withdrawal/my-page', params })
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
