import { App } from 'antd'
import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type NotifyMessage } from '../services/api'
import { useRealtimeEvent } from './RealtimeProvider'

type NotifyMessageContextValue = {
  unreadCount: number
  loading: boolean
  error: string
  refreshUnreadCount: () => Promise<void>
}

const NotifyMessageContext = createContext<NotifyMessageContextValue | null>(null)

export function NotifyMessageProvider({ children }: PropsWithChildren) {
  const { message, notification } = App.useApp()
  const navigate = useNavigate()
  const displayedIds = useRef(new Set<number>())
  const [unreadCount, setUnreadCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const refreshUnreadCount = useCallback(async () => {
    setLoading(true)
    try {
      setUnreadCount(await api.unreadNotifyCount())
      setError('')
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '未读消息数量加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refreshUnreadCount() }, [refreshUnreadCount])
  const openAction = useCallback(async (detail: NotifyMessage) => {
    if (!detail.readStatus) await api.markNotifyMessagesRead([detail.id])
    await refreshUnreadCount()
    if (detail.actionType === 'none') return
    if (detail.sceneCode?.startsWith('zsjos.lead.appeal_')) {
      navigate(APP_ROUTES.LEAD_APPEALS)
      return
    }
    if (detail.actionType === 'business_detail' && detail.bizType === 'lead' && detail.bizId) {
      try {
        const pending = await api.myPendingLeads()
        if (pending.some(item => item.id === detail.bizId)) {
          window.dispatchEvent(new CustomEvent('zsjos-open-lead-assignment', { detail: { leadId: detail.bizId } }))
          return
        }
        const lead = await api.managedLead(detail.bizId)
        const target = lead.relationTypes.includes('owner') ? APP_ROUTES.OWNED_LEADS : APP_ROUTES.SUBMITTED_LEADS
        navigate(target, { state: { leadId: detail.bizId } })
        return
      } catch {
        message.warning('当前账号无权查看该客资，已打开消息详情')
      }
    }
    navigate(`${APP_ROUTES.ALL_MESSAGES}?messageId=${detail.id}`)
  }, [message, navigate, refreshUnreadCount])

  useRealtimeEvent('notify-message-new', realtime => {
    void refreshUnreadCount()
    const content = realtime.content as { messageId?: unknown } | null
    const messageId = Number(content?.messageId)
    if (!Number.isFinite(messageId) || displayedIds.current.has(messageId)) return
    displayedIds.current.add(messageId)
    void api.myNotifyMessage(messageId).then(detail => {
      notification.info({
        key: `notify-message-${detail.id}`,
        message: detail.templateTitle || detail.templateNickname,
        description: detail.templateSummary,
        placement: 'bottomRight',
        duration: 8,
        onClick: () => {
          notification.destroy(`notify-message-${detail.id}`)
          void openAction(detail)
        }
      })
    }).catch(() => displayedIds.current.delete(messageId))
  })

  const value = useMemo(
    () => ({ unreadCount, loading, error, refreshUnreadCount }),
    [error, loading, refreshUnreadCount, unreadCount]
  )

  return <NotifyMessageContext.Provider value={value}>{children}</NotifyMessageContext.Provider>
}

export function useNotifyMessages() {
  const context = useContext(NotifyMessageContext)
  if (!context) throw new Error('useNotifyMessages must be used inside NotifyMessageProvider')
  return context
}
