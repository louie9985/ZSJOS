import request from '@/config/axios'

export interface TextbookAddressRow {
  orderNo: string
  studentName: string
  wechat?: string
  effectiveAt?: string | number
  destination?: { name?: string; mobile?: string; address: string }
  products: { name?: string; sku?: string; quantity: number }[]
}

export function getTextbookAddresses(params: { pageNo: number; pageSize: number; keyword: string }):
  Promise<{ list: TextbookAddressRow[]; total: number }> {
  return request.get({ url: '/zsjos/gift-purchase/textbook-address/page', params, preserveBusinessError: true })
}
