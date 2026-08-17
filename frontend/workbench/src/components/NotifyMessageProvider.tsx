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
  const openAction = useCallback(async (detail: NotifyMessage) => {
    if (!detail.readStatus) await api.markNotifyMessagesRead([detail.id])
    await refreshUnreadCount()
    if (detail.actionType === 'none') return
    if (detail.sceneCode === 'zsjos.registration.task_created' && detail.bizId) {
      navigate(APP_ROUTES.REGISTRATION_POOL, { state: { registrationCaseId: detail.bizId } })
      return
    }
    if (detail.sceneCode === 'zsjos.lead.public_pool' || detail.sceneCode === 'zsjos.lead.qualification_released') {
      navigate(APP_ROUTES.LEAD_CLAIM_POOL)
      return
    }
    if (detail.bizType === 'sales_order' && detail.bizId) {
      try {
        if (detail.sceneCode === 'zsjos.sales_order.submitted') {
          await api.salesOrder(detail.bizId)
          navigate(APP_ROUTES.SALES_ORDER_APPROVALS, { state: { orderId: detail.bizId } })
        } else {
          await api.mySalesOrder(detail.bizId)
          navigate(APP_ROUTES.MY_SALES_ORDERS, { state: { orderId: detail.bizId } })
        }
        return
      } catch {
        message.warning('当前账号无权查看该订单，已打开消息详情')
      }
    }
    if (detail.sceneCode === 'zsjos.lead.appeal_submitted' && detail.bizId) {
      try {
        const inbox = await api.leadAppealInboxPage(false, { pageNo: 1, pageSize: 100 })
        if (inbox.list.some(item => item.leadId === detail.bizId)) {
          navigate(APP_ROUTES.LEAD_APPEALS, { state: { leadId: detail.bizId } })
          return
        }
      } catch { /* fall through to relation-based lead access */ }
    }
    if (detail.actionType === 'business_detail' && detail.bizType === 'lead' && detail.bizId) {
      try {
        const pending = await api.myPendingLeads()
        if (pending.some(item => item.id === detail.bizId)) {
          window.dispatchEvent(new CustomEvent('zsjos-open-lead-assignment', { detail: { leadId: detail.bizId } }))
          return
        }
        const lead = await api.managedLead(detail.bizId)
        const relationScope = lead.relationTypes.includes('owner') ? 'owned' : 'submitted'
        const timedFollowUp = detail.sceneCode === 'zsjos.lead.first_follow_up_reminder'
          || detail.sceneCode === 'zsjos.lead.next_follow_up_reminder'
        navigate(APP_ROUTES.LEAD_MANAGEMENT, {
          state: { leadId: detail.bizId, openFollowUp: timedFollowUp, relationScope }
        })
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
