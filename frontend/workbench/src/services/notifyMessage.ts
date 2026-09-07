import type { NotifyMessage, NotifyMessagePageParams, NotifyMessageCursorParams } from './api'

export type NotifyMessageView = 'all' | 'unread'

export const buildNotifyMessagePageParams = (
  view: NotifyMessageView,
  pageNo: number,
  pageSize: number,
  keyword?: string,
  category?: string
): NotifyMessagePageParams => ({
  pageNo,
  pageSize,
  ...(view === 'unread' ? { readStatus: false } : {})
  ,...(keyword?.trim() ? { keyword: keyword.trim() } : {})
  ,...(category && category !== 'all' ? { category } : {})
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

export const buildNotifyMessageCursorParams = (view: NotifyMessageView, cursor?: string, limit = 20, keyword?: string, category?: string): NotifyMessageCursorParams => ({
  limit,
  ...(cursor ? { cursor } : {}),
  ...(view === 'unread' ? { readStatus: false } : {})
  ,...(keyword?.trim() ? { keyword: keyword.trim() } : {})
  ,...(category && category !== 'all' ? { category } : {})
})
