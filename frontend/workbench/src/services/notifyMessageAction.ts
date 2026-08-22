import type { NavigateFunction } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, ApiError, AuthenticationError, type NotifyMessage } from './api'
import { detailTabsFromProjection, resolveLeadDetailTab, type LeadDetailTab } from './leadFollowUp'

export type NotifyLeadAction =
  | { kind: 'assignment'; leadId: number }
  | { kind: 'lead_management'; leadId: number; targetTab: LeadDetailTab }

export type NotifyMessageActionDeps = {
  navigate: NavigateFunction
  warn: (content: string) => void
  refreshUnreadCount: () => Promise<void>
}

export type NotifyActionErrorKind = 'authentication' | 'forbidden' | 'missing' | 'temporary'

const isPositiveId = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0

export const isNotifyLeadActionCandidate = (detail: NotifyMessage) =>
  detail.actionType === 'business_detail'
  && detail.bizType === 'lead'
  && isPositiveId(detail.bizId)
  && detail.sceneCode !== 'zsjos.lead.appeal_submitted'
  && detail.sceneCode !== 'zsjos.lead.public_pool'
  && detail.sceneCode !== 'zsjos.lead.qualification_released'

const canFallbackToManagedLead = (detail: NotifyMessage) =>
  detail.actionType === 'business_detail'
  && detail.bizType === 'lead'
  && isPositiveId(detail.bizId)
  && detail.sceneCode !== 'zsjos.lead.public_pool'
  && detail.sceneCode !== 'zsjos.lead.qualification_released'

const APPEAL_TAB_SCENES = new Set([
  'zsjos.lead.qualified_invalid',
  'zsjos.lead.appeal_submitted',
  'zsjos.lead.appeal_overturned',
  'zsjos.lead.appeal_upheld'
])
const COMPLAINT_TAB_SCENES = new Set([
  'zsjos.lead.complaint_founded',
  'zsjos.lead.complaint_unfounded'
])
const FOLLOW_UP_TAB_SCENES = new Set([
  'zsjos.lead.follow_up_recorded',
  'zsjos.lead.first_follow_up_reminder',
  'zsjos.lead.next_follow_up_reminder',
  'zsjos.lead.submitter_urged'
])

export const leadTabForNotifyScene = (sceneCode?: string): LeadDetailTab => {
  if (sceneCode && APPEAL_TAB_SCENES.has(sceneCode)) return 'appeals'
  if (sceneCode && COMPLAINT_TAB_SCENES.has(sceneCode)) return 'complaints'
  if (sceneCode && FOLLOW_UP_TAB_SCENES.has(sceneCode)) return 'follow-ups'
  return 'overview'
}

export const leadManagementDeepLink = (leadId: number, tab: LeadDetailTab) =>
  `${APP_ROUTES.LEAD_MANAGEMENT}?leadId=${leadId}&tab=${tab}`

export const classifyNotifyActionError = (error: unknown): NotifyActionErrorKind => {
  if (error instanceof AuthenticationError) return 'authentication'
  if (error instanceof ApiError && error.code === 403) return 'forbidden'
  if (error instanceof ApiError && error.code === 404) return 'missing'
  return 'temporary'
}

const actionFailureMessage = (error: unknown, target: '订单' | '客资') => {
  const kind = classifyNotifyActionError(error)
  if (kind === 'authentication') return '登录状态已失效，已打开消息详情'
  if (kind === 'forbidden') return `当前账号无权查看该${target}，已打开消息详情`
  if (kind === 'missing') return `关联${target}已失效或不存在，已打开消息详情`
  return `${target}详情加载失败，请稍后重试`
}

const syncReadStatusBestEffort = async (detail: NotifyMessage, deps: NotifyMessageActionDeps) => {
  if (detail.readStatus) return
  try {
    await api.markNotifyMessagesRead([detail.id])
  } catch {
    deps.warn('消息已打开，但已读状态同步失败')
    return
  }
  try {
    await deps.refreshUnreadCount()
  } catch {
    deps.warn('消息已读，未读数量刷新失败')
  }
}

const isStudentMediaBusiness = (detail: NotifyMessage) => isPositiveId(detail.bizId)
  && ['media-account', 'content', 'positioning-card'].includes(detail.bizType || '')
const legacyMediaRoute = (detail: NotifyMessage) => {
  if (!isPositiveId(detail.bizId)) return null
  if (detail.bizType === 'production-ticket') return `${APP_ROUTES.MEDIA_PRODUCTION_TICKETS}?ticketId=${detail.bizId}`
  if (detail.bizType === 'handover') return `${APP_ROUTES.MEDIA_HANDOVERS}?handoverId=${detail.bizId}`
  return null
}

export const isNotifyBusinessActionCandidate = (detail: NotifyMessage) =>
  detail.actionType !== 'none' && (
    isNotifyLeadActionCandidate(detail)
    || detail.sceneCode === 'zsjos.registration.task_created'
    || detail.sceneCode === 'zsjos.lead.public_pool'
    || detail.sceneCode === 'zsjos.lead.qualification_released'
    || (detail.bizType === 'sales_order' && isPositiveId(detail.bizId))
    || (detail.bizType === 'student_service' && isPositiveId(detail.bizId))
    || isStudentMediaBusiness(detail)
    || Boolean(legacyMediaRoute(detail))
  )

const hasPendingAppeal = async (leadId: number) => {
  const pageSize = 100
  for (let pageNo = 1; ; pageNo += 1) {
    const page = await api.leadAppealInboxPage(false, { pageNo, pageSize })
    if (page.list.some(item => item.leadId === leadId)) return true
    if (page.list.length < pageSize || pageNo * pageSize >= page.total) return false
  }
}

export async function resolveNotifyLeadAction(
  detail: NotifyMessage,
  options: { allowAppealFallback?: boolean } = {}
): Promise<NotifyLeadAction | null> {
  const candidate = options.allowAppealFallback ? canFallbackToManagedLead(detail) : isNotifyLeadActionCandidate(detail)
  if (!candidate) return null
  const leadId = detail.bizId as number
  try {
    const pending = await api.myPendingLeads()
    if (pending.some(item => item.id === leadId)) return { kind: 'assignment', leadId }
  } catch (error) {
    if (classifyNotifyActionError(error) === 'authentication') throw error
    // Managed Lead access is independent from assignment-inbox availability.
  }
  const lead = await api.managedLead(leadId)
  const targetTab = resolveLeadDetailTab(detailTabsFromProjection(lead.visibleTabs),
    leadTabForNotifyScene(detail.sceneCode))
  return {
    kind: 'lead_management',
    leadId,
    targetTab
  }
}

export async function executeNotifyMessageAction(detail: NotifyMessage, deps: NotifyMessageActionDeps) {
  await syncReadStatusBestEffort(detail, deps)
  if (detail.actionType === 'none') return
  if (detail.sceneCode === 'zsjos.registration.task_created' && isPositiveId(detail.bizId)) {
    deps.navigate(APP_ROUTES.REGISTRATION_POOL, { state: { registrationCaseId: detail.bizId } })
    return
  }
  if (detail.sceneCode === 'zsjos.lead.public_pool' || detail.sceneCode === 'zsjos.lead.qualification_released') {
    deps.navigate(APP_ROUTES.LEAD_CLAIM_POOL)
    return
  }
  if (detail.bizType === 'sales_order' && isPositiveId(detail.bizId)) {
    try {
      if (detail.sceneCode === 'zsjos.sales_order.supervisor_requested') {
        const target = await api.salesOrderApprovalNotificationTarget(detail.bizId, detail.sceneCode, detail.sourceEventKey)
        deps.navigate(`${APP_ROUTES.SALES_ORDER_APPROVALS}?workType=${target.workType}&orderId=${target.orderId}&taskId=${encodeURIComponent(target.taskId)}&confirmationId=${target.confirmationId}`)
      } else if (detail.sceneCode === 'zsjos.sales_order.supervisor_decided') {
        const target = await api.salesOrderApprovalNotificationTarget(detail.bizId, detail.sceneCode, detail.sourceEventKey)
        deps.navigate(`${APP_ROUTES.SALES_ORDER_APPROVALS}?workType=${target.workType}&orderId=${target.orderId}&taskId=${encodeURIComponent(target.taskId)}&confirmationId=${target.confirmationId}`)
      } else if (detail.sceneCode === 'zsjos.sales_order.submitted') {
        await api.salesOrder(detail.bizId)
        deps.navigate(`${APP_ROUTES.SALES_ORDER_APPROVALS}?workType=approval&orderId=${detail.bizId}`)
      } else {
        await api.mySalesOrder(detail.bizId)
        deps.navigate(APP_ROUTES.MY_SALES_ORDERS, { state: { orderId: detail.bizId } })
      }
      return
    } catch (error) {
      deps.warn(actionFailureMessage(error, '订单'))
      deps.navigate(`${APP_ROUTES.ALL_MESSAGES}?messageId=${detail.id}`)
      return
    }
  }
  if (detail.bizType === 'student_service' && isPositiveId(detail.bizId)) {
    deps.navigate(APP_ROUTES.MY_STUDENTS, { state: { serviceRelationId: detail.bizId } })
    return
  }
  if (isStudentMediaBusiness(detail)) {
    try {
      const target = await api.mediaStudents.target(detail.bizType!, detail.bizId!)
      const recordKey = target.targetTab === 'accounts' ? 'accountId' : target.targetTab === 'content' ? 'contentId' : 'positioningCardId'
      deps.navigate(`${APP_ROUTES.MEDIA_STUDENTS}?personId=${target.personId}&tab=${target.targetTab}&${recordKey}=${target.recordId}`)
    } catch {
      deps.warn('关联记录尚未绑定到当前可访问的学员')
    }
    return
  }
  const mediaRoute = legacyMediaRoute(detail)
  if (mediaRoute) {
    deps.navigate(mediaRoute)
    return
  }
  if (detail.sceneCode === 'zsjos.lead.appeal_submitted' && isPositiveId(detail.bizId)) {
    try {
      if (await hasPendingAppeal(detail.bizId)) {
        deps.navigate(APP_ROUTES.LEAD_APPEALS, { state: { leadId: detail.bizId } })
        return
      }
    } catch { /* fall through to relation-based lead access */ }
  }
  if (canFallbackToManagedLead(detail)) {
    try {
      const action = await resolveNotifyLeadAction(detail, { allowAppealFallback: true })
      if (action?.kind === 'assignment') {
        window.dispatchEvent(new CustomEvent('zsjos-open-lead-assignment', { detail: { leadId: action.leadId } }))
        return
      }
      if (action?.kind === 'lead_management') {
        deps.navigate(leadManagementDeepLink(action.leadId, action.targetTab))
        return
      }
    } catch (error) {
      deps.warn(actionFailureMessage(error, '客资'))
    }
  }
  deps.navigate(`${APP_ROUTES.ALL_MESSAGES}?messageId=${detail.id}`)
}
