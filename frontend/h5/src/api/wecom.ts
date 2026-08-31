import axios from 'axios'
import { getTenantId } from '@/utils/storage'

export interface WecomClickTarget {
  audience: 'ADMIN' | 'PARTNER'
  actionType?: 'none' | 'message_detail' | 'business_detail'
  targetPath?: string
  fallbackPath?: string
}

interface ApiResponse<T> {
  code: number
  data: T
  msg: string
}

const publicApiBaseUrl = () =>
  (import.meta.env.VITE_APP_BASE_API as string).replace(/\/part-api\/?$/, '/public-api')

export async function resolveWecomClickTicket(ticket: string) {
  const response = await axios.get<ApiResponse<WecomClickTarget>>(
    `${publicApiBaseUrl()}/zsjos/wecom-click/resolve`,
    {
      params: { ticket },
      headers: { 'tenant-id': getTenantId() },
      timeout: 15000
    }
  )
  const payload = response.data
  if (payload.code !== 0) throw new Error(payload.msg || '企业微信消息链接解析失败')
  return payload.data
}
