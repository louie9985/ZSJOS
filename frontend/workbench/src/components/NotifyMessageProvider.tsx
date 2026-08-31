import { App } from 'antd'
import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type NotifyMessage } from '../services/api'
import { executeNotifyMessageAction } from '../services/notifyMessageAction'
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
  const [popupDurationSeconds, setPopupDurationSeconds] = useState(300)

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
  useEffect(() => {
    let active = true
    void api.leadRuntimeSetting().then(setting => {
      const minutes = Number(setting.notificationPopupDurationMinutes)
      if (active && Number.isFinite(minutes) && minutes >= 1 && minutes <= 30) {
        setPopupDurationSeconds(minutes * 60)
      }
    }).catch(() => undefined)
    return () => { active = false }
  }, [])
  const openAction = useCallback((detail: NotifyMessage) => executeNotifyMessageAction(detail, {
    navigate,
    warn: message.warning,
    refreshUnreadCount
  }), [message.warning, navigate, refreshUnreadCount])

  useRealtimeEvent('notify-message-new', realtime => {
    void refreshUnreadCount()
    const content = realtime.content as { messageId?: unknown } | null
    const messageId = Number(content?.messageId)
    if (!Number.isFinite(messageId) || displayedIds.current.has(messageId)) return
    displayedIds.current.add(messageId)
    void api.myNotifyMessage(messageId).then(detail => {
      if (detail.sceneCode === 'zsjos.registration.task_created' && detail.bizId) {
        window.dispatchEvent(new CustomEvent('zsjos-registration-task-created', {
          detail: { registrationCaseId: detail.bizId }
        }))
      }
      notification.info({
        key: `notify-message-${detail.id}`,
        message: detail.templateTitle || detail.templateNickname,
        description: detail.templateSummary,
        placement: 'bottomRight',
        duration: popupDurationSeconds,
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
