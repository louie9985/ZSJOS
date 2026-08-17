import request from './request'

export interface MessageItem {
  id: number
  templateTitle: string
  templateSummary?: string
  templateContent: string
  templateType: number
  bizType?: string
  bizId?: number
  readStatus: boolean
  createTime: string
}

/** 消息列表 */
export function getMessagePage(params: { pageNo: number; pageSize: number }) {
  return request.get<never, { list: MessageItem[]; total: number }>('/zsjos/messages/page', { params })
}

/** 消息详情 */
export function getMessageDetail(id: number) {
  return request.get<never, MessageItem>(`/zsjos/messages/${id}`)
}

/** 批量标记已读 */
export function markRead(ids: number[]) {
  return request.put<never, void>('/zsjos/messages/read', { ids })
}

/** 未读数 */
export function getUnreadCount() {
  return request.get<never, number>('/zsjos/messages/unread-count')
}
