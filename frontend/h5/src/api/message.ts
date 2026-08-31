import request from './request'
import type { ApiDateValue } from '@/utils/format'

export interface MessageItem {
  id: number
  templateTitle: string
  templateSummary?: string
  templateContent: string
  templateType: number
  templateNickname?: string
  sceneCode?: string
  actionType?: 'none' | 'message_detail' | 'business_detail'
  bizType?: string
  bizId?: number
  readStatus: boolean
  readTime?: ApiDateValue
  createTime: ApiDateValue
}

export interface MessageGroup {
  key: string
  label: string
  bizTypes: string[]
}

/** 消息列表 */
export function getMessagePage(params: { pageNo: number; pageSize: number; group?: string; unreadOnly?: boolean }) {
  const { unreadOnly, ...baseParams } = params
  return request.get<never, { list: MessageItem[]; total: number }>('/zsjos/messages/page', {
    params: { ...baseParams, ...(unreadOnly ? { readStatus: false } : {}) }
  })
}

/** 服务端维护的消息分组 */
export function getMessageGroups() {
  return request.get<never, MessageGroup[]>('/zsjos/messages/groups')
}

/** 消息详情 */
export function getMessageDetail(id: number) {
  return request.get<never, MessageItem>(`/zsjos/messages/${id}`)
}

/** 批量标记已读 */
export function markRead(ids: number[]) {
  return request.put<never, void>('/zsjos/messages/read', { ids })
}

/** 使用现有分页和批量已读接口标记当前账号的全部未读消息 */
export async function markAllRead() {
  let updatedCount = 0
  let previousBatch = ''
  while (true) {
    const page = await getMessagePage({ pageNo: 1, pageSize: 100, unreadOnly: true })
    const ids = page.list.map(item => item.id)
    if (!ids.length) return { updatedCount }
    const currentBatch = ids.join(',')
    if (currentBatch === previousBatch) throw new Error('消息已读状态未更新，请重试')
    await markRead(ids)
    updatedCount += ids.length
    previousBatch = currentBatch
  }
}

/** 未读数 */
export function getUnreadCount() {
  return request.get<never, number>('/zsjos/messages/unread-count')
}
