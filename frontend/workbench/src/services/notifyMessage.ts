import type { NotifyMessage, NotifyMessagePageParams } from './api'

export type NotifyMessageView = 'all' | 'unread'

export const buildNotifyMessagePageParams = (
  view: NotifyMessageView,
  pageNo: number,
  pageSize: number
): NotifyMessagePageParams => ({
  pageNo,
  pageSize,
  ...(view === 'unread' ? { readStatus: false } : {})
})

export const applyReadStatus = (
  messages: NotifyMessage[],
  ids: number[],
  view: NotifyMessageView,
  readTime: NotifyMessage['readTime']
) => {
  const selectedIds = new Set(ids)
  const updated = messages.map(item => selectedIds.has(item.id)
    ? { ...item, readStatus: true, readTime }
    : item)
  return view === 'unread' ? updated.filter(item => !selectedIds.has(item.id)) : updated
}
