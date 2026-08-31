import request from './request'
import type { ApiDateValue } from '@/utils/format'

export interface WithdrawalSummary {
  availableAmount: number
  minimumAmount: number
  selectableCount: number
  canApply: boolean
}

export interface BankCard {
  id: number
  accountName: string
  maskedCardNumber: string
  bankName: string
  branchName: string
  defaultCard: boolean
}

export interface WithdrawalApplyParams {
  cashbackIds: number[]
  bankCardId?: number
  accountName?: string
  cardNumber?: string
  bankName?: string
  branchName?: string
  saveCard?: boolean
}

export interface WithdrawalItem {
  id: number
  withdrawalNo?: string
  status: 'pending_review' | 'approved' | 'rejected' | 'paid' | 'cancelled'
  applicationAmount: number
  accountNameSnapshot?: string
  approvedAmount?: number
  submittedAt: ApiDateValue
  reviewedAt?: ApiDateValue
  rejectionReason?: string
  paidAt?: ApiDateValue
  bankNameSnapshot: string
  branchNameSnapshot?: string
  maskedCardNumber: string
}

/** 提现汇总 */
export function getWithdrawalSummary() {
  return request.get<never, WithdrawalSummary>('/zsjos/withdrawal/my-summary')
}

/** 查询本人银行卡 */
export function getMyCards() {
  return request.get<never, BankCard[]>('/zsjos/withdrawal/my-cards')
}

/** 新增银行卡 */
export function addCard(data: { accountName: string; cardNumber: string; bankName: string; branchName: string }) {
  return request.post<never, void>('/zsjos/withdrawal/my-cards', data)
}

/** 删除银行卡 */
export function deleteCard(id: number) {
  return request.delete<never, void>(`/zsjos/withdrawal/my-cards/${id}`)
}

/** 设置默认银行卡 */
export function setDefaultCard(id: number) {
  return request.put<never, void>(`/zsjos/withdrawal/my-cards/${id}/default`)
}

/** 受控编辑本人银行卡；cardNumber 仅在换卡号时传递完整值 */
export function updateCard(id: number, data: {
  accountName: string
  bankName: string
  branchName: string
  cardNumber?: string
}) {
  return request.put<never, void>(`/zsjos/withdrawal/my-cards/${id}`, data)
}

/** 提交提现申请 */
export function applyWithdrawal(data: WithdrawalApplyParams) {
  return request.post<never, void>('/zsjos/withdrawal/apply', data)
}

/** 查询本人提现记录 */
export function getWithdrawalPage(params: { pageNo: number; pageSize: number; status?: string }) {
  return request.get<never, { list: WithdrawalItem[]; total: number }>('/zsjos/withdrawal/my-page', { params })
}

/** 查询提现详情 */
export function getWithdrawalDetail(id: number) {
  return request.get<never, WithdrawalItem>(`/zsjos/withdrawal/my/${id}`)
}

/** 取消提现 */
export function cancelWithdrawal(id: number) {
  return request.put<never, void>(`/zsjos/withdrawal/${id}/cancel`)
}
