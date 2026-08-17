import request from './request'
import type { ApiDateValue } from '@/utils/format'

export interface CashbackSummary {
  totalAmount: number
  pendingAmount: number
  availableAmount: number
  withdrawingAmount: number
  withdrawnAmount: number
  counts: {
    pending_settlement: number
    available: number
    withdrawn: number
  }
}

export interface CashbackItem {
  id: number
  cashbackNo: string
  type: 'valid' | 'deal'
  status: 'pending_settlement' | 'available' | 'withdrawing' | 'withdrawn' | 'cancelled'
  leadId: number
  leadNo?: string
  orderId?: number
  orderItemId?: number
  productRefSnapshot: string
  productNameSnapshot: string
  baseAmount: number
  rateSnapshot?: number
  amount: number
  observationDaysSnapshot: number
  generatedAt: ApiDateValue
  availableAt?: ApiDateValue
  settledAt?: ApiDateValue
  cancelledAt?: ApiDateValue
  cancelReason?: string
}

/** 返现汇总 */
export function getCashbackSummary() {
  return request.get<never, CashbackSummary>('/zsjos/cashback/my-summary')
}

/** 返现列表 */
export function getCashbackPage(params: { pageNo: number; pageSize: number; type?: string; status?: string }) {
  return request.get<never, { list: CashbackItem[]; total: number }>('/zsjos/cashback/my-page', { params })
}
