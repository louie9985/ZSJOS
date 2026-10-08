import axios from 'axios'
export interface PublicNoticeAttachment { id: number; fileName: string; mimeType?: string; fileSize: number }
export interface PublicNotice { title: string; content: string; publishTime: number; attachments: PublicNoticeAttachment[] }
export class NoticeShareError extends Error {
  constructor(message: string, public code?: number) { super(message); this.name = 'NoticeShareError' }
}
const client = axios.create({ baseURL: '/public-api/system/notice-share', timeout: 15000, withCredentials: false })
client.interceptors.request.use(config => {
  config.headers.delete('Authorization'); config.headers.delete('tenant-id'); config.headers.delete('visit-tenant-id')
  return config
})
async function get<T>(path: string, token: string, params?: Record<string, number>): Promise<T> {
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new NoticeShareError('分享内容已失效', 1002008007)
  try {
    const response = await client.get<{ code: number; data: T; msg?: string }>(path, { params, headers: { 'X-Notice-Share-Token': token } })
    if (response.data.code !== 0) throw new NoticeShareError(response.data.msg || '加载失败，请重试', response.data.code)
    return response.data.data
  } catch (e) {
    if (e instanceof NoticeShareError) throw e
    throw new NoticeShareError('网络或服务暂时不可用，请重试')
  }
}
export const getPublicNotice = (token: string) => get<PublicNotice>('/get', token)
export const getPublicNoticeAttachmentUrl = (token: string, attachmentId: number) => get<string>('/attachment-url', token, { attachmentId })
