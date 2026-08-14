import request from '@/config/axios'

export interface CashbackVO {
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

export const getFinanceCashbackPage = (params: any) =>
  request.get({ url: '/zsjos/cashback/page', params })
export const getMyCashbackPage = (params: any) =>
  request.get({ url: '/zsjos/cashback/my-page', params })
