import request from '@/config/axios'

export interface ShippingInfo { carrier: string; trackingNo: string; version: number }
export interface TextbookProduct { itemId: number; name?: string; sku?: string; quantity: number; shipment?: ShippingInfo }

export interface TextbookAddressRow {
  orderId: number
  orderNo: string
  studentName: string
  wechat?: string
  effectiveAt?: string | number
  destination?: { name?: string; mobile?: string; address: string }
  products: TextbookProduct[]
}

export function getTextbookAddresses(params: { pageNo: number; pageSize: number; keyword: string }):
  Promise<{ list: TextbookAddressRow[]; total: number }> {
  return request.get({ url: '/zsjos/textbook-shipping/page', params, preserveBusinessError: true })
}

export function saveShipping(orderId: number, itemId: number, data: ShippingInfo) {
  return request.put({ url: `/zsjos/textbook-shipping/${orderId}/items/${itemId}`, data, preserveBusinessError: true })
}
