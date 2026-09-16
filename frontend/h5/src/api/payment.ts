import axios from 'axios'
import type { ProductSpec } from '@/utils/productSpecs'

export interface PublicPaymentItem {
  productName?: string | null
  skuName?: string | null
  actualAmount: number
  specs?: ProductSpec[] | null
}

export interface PublicPaymentDetail {
  paymentIntentNo: string
  amount: number
  currency: string
  description: string
  status: 'created' | 'waiting' | 'paid' | 'expired' | 'closed'
  expiresAt?: number
  items?: PublicPaymentItem[]
}

const publicRequest = axios.create({ baseURL: '/public-api', timeout: 15000 })
const unwrap = <T>(response: { data?: { code?: number; data?: T; msg?: string } }): T => {
  if (response.data?.code !== 0) throw new Error(response.data?.msg || '请求失败')
  return response.data.data as T
}

export const getPublicPayment = async (no: string, token: string) => unwrap<PublicPaymentDetail>(
  await publicRequest.get(`/zsjos/payment/${encodeURIComponent(no)}`, { params: { token } }))

export const createAlipayOrder = async (no: string, token: string) => unwrap<string>(
  await publicRequest.post(`/zsjos/payment/${encodeURIComponent(no)}/order`, { token, channel: 'alipay' }))

export const queryPublicPayment = async (no: string, token: string) => unwrap<boolean>(
  await publicRequest.post(`/zsjos/payment/${encodeURIComponent(no)}/status`, { token }))

export const wechatOrderAction = (no: string) => `/public-api/zsjos/payment/${encodeURIComponent(no)}/order`
