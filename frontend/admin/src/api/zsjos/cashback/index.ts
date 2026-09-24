import request from '@/config/axios'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'

export interface CashbackPageQuery {
  pageNo: number
  pageSize: number
  status?: string
  keyword?: string
  advancedFilter?: AdvancedFilterGroup
  type?: string
}

export interface FinanceSource {
  leadAccess: string; leadId?: number; leadNo?: string; customerName?: string
  orderAccess: string; orderId?: number; orderNo?: string; studentName?: string; salesName?: string
  orderStatusLabel?: string; orderTypeLabel?: string; orderTotalAmount?: number; orderPayableAmount?: number
  customerPaidAt?: string; productName?: string; skuName?: string; quantity?: number; unitPrice?: number
  discountAmount?: number; itemPayableAmount?: number
}
export interface CashbackVO {
  beneficiaryName?: string; partnerName?: string; source?: FinanceSource
  baseAmount?: number; rateSnapshot?: number; observationDaysSnapshot?: number
  settledAt?: string; cancelledAt?: string; cancelReason?: string
  id: number
  cashbackNo: string
  type: 'valid' | 'deal'
  status: 'pending_settlement' | 'available' | 'withdrawing' | 'withdrawn' | 'cancelled'
  beneficiaryUserId: number
  productNameSnapshot: string
  amount: number
  generatedAt: string
  availableAt: string
}

export const getFinanceCashbackPage = (params: CashbackPageQuery) =>
  request.get({ url: '/zsjos/cashback/page', params })
export const getMyCashbackPage = (params: CashbackPageQuery) =>
  request.get({ url: '/zsjos/cashback/my-page', params })

export const searchFinanceCashbackPage = (data: CashbackPageQuery) => request.post({ url: '/zsjos/cashback/search-page', data })
export const searchMyCashbackPage = (data: CashbackPageQuery) => request.post({ url: '/zsjos/cashback/my-search-page', data })

export interface CashbackWithdrawal { id: number; withdrawalNo: string; amount: number; active: boolean; status: string; submittedAt: string }
export const getDetail = (id: number): Promise<CashbackVO> => request.get({ url: `/zsjos/cashback/${id}` })
export const getWithdrawals = (id: number, pageNo = 1): Promise<{ list: CashbackWithdrawal[]; total: number }> => request.get({ url: `/zsjos/cashback/${id}/withdrawals`, params: { pageNo, pageSize: 10 } })
