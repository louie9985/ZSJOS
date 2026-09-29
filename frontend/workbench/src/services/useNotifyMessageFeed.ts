import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, AuthenticationError, type NotifyMessage } from './api'
import { buildNotifyMessageCursorParams, type NotifyMessageView } from './notifyMessage'

/**
 * 消息游标加载的共享实现。
 *
 * 页头弹窗与消息中心列表原先各写了一份几乎相同的逻辑（请求序号防竞态、追加去重、
 * 游标与 hasMore 维护、加载态与错误态），两份已经出现行为差异。这里统一为一份，
 * 调用方只负责展示与本地状态更新。
 */
export function useNotifyMessageFeed({
  view,
  keyword,
  category,
  limit = 20,
  enabled = true
}: {
  view: NotifyMessageView
  keyword?: string
  category?: string
  limit?: number
  /** 为 false 时不自动加载，供「打开才拉取」的弹窗使用。 */
  enabled?: boolean
}) {
  const requestSequence = useRef(0)
  const cursorRef = useRef<string | undefined>(undefined)
  const [messages, setMessages] = useState<NotifyMessage[]>([])
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)

  const load = useCallback(async (append = false) => {
    const requestId = ++requestSequence.current
    if (append) setLoadingMore(true)
    else {
      setLoading(true)
      cursorRef.current = undefined
      setHasMore(true)
    }
    try {
      const data = await api.myNotifyMessageCursor(
        buildNotifyMessageCursorParams(view, append ? cursorRef.current : undefined, limit, keyword, category)
      )
      if (requestId !== requestSequence.current) return
      setMessages(current => append
        ? [...current, ...data.list.filter(item => !current.some(existing => existing.id === item.id))]
        : data.list)
      cursorRef.current = data.nextCursor
      setHasMore(data.hasMore)
      setError('')
      setUnauthorized(false)
    } catch (loadError) {
      if (requestId !== requestSequence.current) return
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(loadError instanceof Error ? loadError.message : '消息加载失败')
    } finally {
      if (requestId === requestSequence.current) {
        setLoading(false)
        setLoadingMore(false)
      }
    }
  }, [category, keyword, limit, view])

  useEffect(() => {
    if (!enabled) return
    void load(false)
  }, [enabled, load])

  /** 全部标记已读等场景需要停止继续追加，避免重复拉取已清空的未读列表。 */
  const stopLoadingMore = useCallback(() => {
    cursorRef.current = undefined
    setHasMore(false)
  }, [])

  const reload = useCallback(() => load(false), [load])
  const loadMore = useCallback(() => load(true), [load])

  // 稳定的返回对象：调用方会把 feed 放进 useCallback/useMemo 依赖，
  // 每次渲染返回新对象会让这些 memo 全部失效。
  return useMemo(() => ({
    messages, setMessages, hasMore, loading, loadingMore, error, unauthorized,
    reload, loadMore, stopLoadingMore
  }), [error, hasMore, loadMore, loading, loadingMore, messages, reload, stopLoadingMore, unauthorized])
}
