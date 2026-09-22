import { contentReviewApi } from './materialApi'
import { workOrderApi } from './workOrderApi'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, type NotifyMessage } from './api'
import {
  executeNotifyMessageAction,
  isNotifyBusinessActionCandidate,
  isNotifyLeadActionCandidate,
  leadManagementDeepLink,
  leadTabForNotifyScene,
  resolveNotifyLeadAction
} from './notifyMessageAction'

const message = (overrides: Partial<NotifyMessage> = {}): NotifyMessage => ({
  id: 11,
  templateNickname: '系统',
  templateContent: '内容',
  templateType: 1,
  readStatus: true,
  createTime: Date.now(),
  actionType: 'business_detail',
  bizType: 'lead',
  bizId: 29,
  sceneCode: 'zsjos.lead.assigned',
  ...overrides
})

describe('notify message business actions', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('only exposes direct Lead business actions as Lead navigation candidates', () => {
    expect(isNotifyLeadActionCandidate(message())).toBe(true)
    expect(isNotifyLeadActionCandidate(message({ actionType: 'message_detail' }))).toBe(false)
    expect(isNotifyLeadActionCandidate(message({ sceneCode: 'zsjos.lead.public_pool' }))).toBe(false)
    expect(isNotifyLeadActionCandidate(message({ bizType: 'sales_order' }))).toBe(false)
  })

  it('resolves pending Leads to the assignment host', async () => {
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([{ id: 29 } as never])
    await expect(resolveNotifyLeadAction(message())).resolves.toEqual({ kind: 'assignment', leadId: 29 })
  })

  it('resolves visible Leads to management and preserves timed follow-up behavior', async () => {
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([])
    vi.spyOn(api, 'managedLead').mockResolvedValue({ id: 29, visibleTabs: ['overview', 'follow-ups'] } as never)
    await expect(resolveNotifyLeadAction(message({ sceneCode: 'zsjos.lead.next_follow_up_reminder' })))
      .resolves.toEqual({ kind: 'lead_management', leadId: 29, targetTab: 'follow-ups' })
  })

  it('uses the same resolver for realtime-style execution and falls back on denial', async () => {
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([])
    vi.spyOn(api, 'managedLead').mockRejectedValue(new ApiError(403, 'forbidden'))
    const navigate = vi.fn()
    const warn = vi.fn()
    await executeNotifyMessageAction(message(), { navigate, warn, refreshUnreadCount: vi.fn().mockResolvedValue(undefined) })
    expect(warn).toHaveBeenCalledWith('当前账号无权查看该客资，已打开消息详情')
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('messageId=11'))
  })

  it('continues with managed Lead access when the assignment inbox is unavailable', async () => {
    vi.spyOn(api, 'myPendingLeads').mockRejectedValue(new Error('network'))
    vi.spyOn(api, 'managedLead').mockResolvedValue({ id: 29 } as never)

    await expect(resolveNotifyLeadAction(message())).resolves.toEqual({
      kind: 'lead_management', leadId: 29, targetTab: 'overview'
    })
  })

  it('does not block navigation when read synchronization fails', async () => {
    vi.spyOn(api, 'markNotifyMessagesRead').mockRejectedValue(new Error('network'))
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([])
    vi.spyOn(api, 'managedLead').mockResolvedValue({ id: 29 } as never)
    const navigate = vi.fn()
    const warn = vi.fn()

    await executeNotifyMessageAction(message({ readStatus: false }), {
      navigate, warn, refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })

    expect(warn).toHaveBeenCalledWith('消息已打开，但已读状态同步失败')
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('leadId=29&tab=overview'))
  })

  it('distinguishes temporary Lead failures from permission denial', async () => {
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([])
    vi.spyOn(api, 'managedLead').mockRejectedValue(new Error('network'))
    const navigate = vi.fn()
    const warn = vi.fn()

    await executeNotifyMessageAction(message(), {
      navigate, warn, refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })

    expect(warn).toHaveBeenCalledWith('客资详情加载失败，请稍后重试')
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('messageId=11'))
  })

  it('searches every pending appeal page before navigating', async () => {
    vi.spyOn(api, 'leadAppealInboxPage')
      .mockResolvedValueOnce({ list: Array.from({ length: 100 }, (_, index) => ({ leadId: index + 1000 } as never)), total: 101 })
      .mockResolvedValueOnce({ list: [{ leadId: 29 } as never], total: 101 })
    const navigate = vi.fn()

    await executeNotifyMessageAction(message({ sceneCode: 'zsjos.lead.appeal_submitted' }), {
      navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })

    expect(api.leadAppealInboxPage).toHaveBeenCalledTimes(2)
    expect(navigate).toHaveBeenCalledWith(expect.any(String), { state: { leadId: 29 } })
  })

  it('falls back from an unavailable appeal inbox to managed Lead access', async () => {
    vi.spyOn(api, 'leadAppealInboxPage').mockRejectedValue(new Error('network'))
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([])
    vi.spyOn(api, 'managedLead').mockResolvedValue({ id: 29, visibleTabs: ['overview', 'appeals'] } as never)
    const navigate = vi.fn()

    await executeNotifyMessageAction(message({ sceneCode: 'zsjos.lead.appeal_submitted' }), {
      navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })

    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('leadId=29&tab=appeals'))
  })

  it('maps Lead notification scenes to stable detail tabs', () => {
    expect(leadTabForNotifyScene('zsjos.lead.appeal_upheld')).toBe('appeals')
    expect(leadTabForNotifyScene('zsjos.lead.qualified_invalid')).toBe('appeals')
    expect(leadTabForNotifyScene('zsjos.lead.complaint_founded')).toBe('complaints')
    expect(leadTabForNotifyScene('zsjos.lead.complaint_unfounded')).toBe('complaints')
    expect(leadTabForNotifyScene('zsjos.lead.follow_up_recorded')).toBe('follow-ups')
    expect(leadTabForNotifyScene('zsjos.lead.assigned')).toBe('overview')
    expect(leadManagementDeepLink(29, 'complaints')).toContain('leadId=29&tab=complaints')
  })

  it.each(['zsjos.sales_order.submitter_pending', 'zsjos.sales_order.submitter_effective'])(
    'opens %s through Lead authorization without reading the order', async sceneCode => {
      vi.spyOn(api, 'leadOrderNotificationTarget').mockResolvedValue({ id: 73 } as never)
      const orderRead = vi.spyOn(api, 'salesOrder')
      const navigate = vi.fn()
      await executeNotifyMessageAction(message({ bizType: 'sales_order', bizId: 29, sceneCode }), {
        navigate, warn: vi.fn(), refreshUnreadCount: vi.fn()
      })
      expect(api.leadOrderNotificationTarget).toHaveBeenCalledWith(29)
      expect(orderRead).not.toHaveBeenCalled()
      expect(navigate).toHaveBeenCalledWith('/zsjos/leads/manage?leadId=73&tab=overview')
    })

  it.each([
    [1900003013, '当前账号无权查看该客资，已打开消息详情'],
    [1900003009, '关联客资已失效或不存在，已打开消息详情'],
    [500, '客资详情加载失败，请稍后重试']
  ])('handles submitter target failure %s without opening an order', async (code, warning) => {
    vi.spyOn(api, 'leadOrderNotificationTarget').mockRejectedValue(new ApiError(code as number, 'error'))
    const orderRead = vi.spyOn(api, 'salesOrder')
    const navigate = vi.fn()
    const warn = vi.fn()
    await executeNotifyMessageAction(message({ bizType: 'sales_order', sceneCode: 'zsjos.sales_order.submitter_pending' }), {
      navigate, warn, refreshUnreadCount: vi.fn()
    })
    expect(orderRead).not.toHaveBeenCalled()
    expect(warn).toHaveBeenCalledWith(warning)
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('messageId=11'))
  })

  it('resolves supervisor notifications to an exact approval task', async () => {
    vi.spyOn(api, 'salesOrderApprovalNotificationTarget').mockResolvedValue({
      workType: 'supervisor', orderId: 29, taskId: 'task/1', taskDefinitionKey: 'registrationReview',
      center: 'registration', confirmationId: 31, status: 'pending'
    })
    const navigate = vi.fn()

    await executeNotifyMessageAction(message({
      bizType: 'sales_order', bizId: 29, sceneCode: 'zsjos.sales_order.supervisor_requested'
    }), { navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined) })

    expect(navigate).toHaveBeenCalledWith(expect.stringContaining(
      'workType=supervisor&orderId=29&taskId=task%2F1&confirmationId=31'))
  })

  it('opens student contact notifications at the exact service relation', async () => {
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType: 'student_service', bizId: 41,
      sceneCode: 'zsjos.student.first_contact_reminder' }), {
      navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })
    expect(navigate).toHaveBeenCalledWith(expect.any(String), { state: { serviceRelationId: 41 } })
  })

  it('opens planner assignment notifications at the assigned student', async () => {
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType: 'student', bizId: 29,
      sceneCode: 'zsjos.registration.planner_assigned' }), {
      navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })
    expect(isNotifyBusinessActionCandidate(message({ bizType: 'student', bizId: 29 }))).toBe(true)
    expect(navigate).toHaveBeenCalledWith(expect.any(String), { state: { personId: 29 } })
  })

  it('exposes media business detail messages as actionable', () => {
    expect(isNotifyBusinessActionCandidate(message({ bizType: 'production-ticket', bizId: 41,
      sceneCode: 'media.ticket.pending_accept' }))).toBe(true)
    expect(isNotifyBusinessActionCandidate(message({ actionType: 'none', bizType: 'production-ticket', bizId: 41 }))).toBe(false)
  })

  it.each([
    ['production-ticket', '/zsjos/production-tickets?ticketId=41'],
  ])('opens %s notifications at the existing Workbench route', async (bizType, expected) => {
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType, bizId: 41, sceneCode: `media.${bizType}.updated` }), {
      navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })
    expect(navigate).toHaveBeenCalledWith(expected)
  })

  it.each([
    ['media-account', 'accounts', 'accountId'],
    ['content', 'content', 'contentId'],
    ['positioning-card', 'positioning', 'positioningCardId']
  ])('opens %s notifications in the student center', async (bizType, targetTab, recordKey) => {
    vi.spyOn(api.mediaStudents, 'target').mockResolvedValue({ personId: 29, targetTab, recordId: 41 })
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType, bizId: 41, sceneCode: `media.${bizType}.updated` }), {
      navigate, warn: vi.fn(), refreshUnreadCount: vi.fn().mockResolvedValue(undefined)
    })
    expect(navigate).toHaveBeenCalledWith(`/zsjos/media-students?personId=29&tab=${targetTab}&${recordKey}=41`)
  })

  it('falls back to overview when the server does not expose the requested tab', async () => {
    vi.spyOn(api, 'myPendingLeads').mockResolvedValue([])
    vi.spyOn(api, 'managedLead').mockResolvedValue({ id: 29, visibleTabs: ['overview'] } as never)

    await expect(resolveNotifyLeadAction(message({ sceneCode: 'zsjos.lead.complaint_founded' })))
      .resolves.toEqual({ kind: 'lead_management', leadId: 29, targetTab: 'overview' })
  })
  it.each(['content_review', 'work-order'])('checks %s access before navigation and falls back on denial', async bizType => {
    const spy = bizType === 'content_review' ? vi.spyOn(contentReviewApi, 'get') : vi.spyOn(workOrderApi, 'detail')
    spy.mockRejectedValue(new ApiError(403, 'denied'))
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType, bizId: 41 }), { navigate, warn: vi.fn(), refreshUnreadCount: vi.fn() })
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('messageId=11'))
  })
  it('opens a batch outside the first list page by its stable batch ID', async () => {
    vi.spyOn(contentReviewApi, 'get').mockResolvedValue({ id: 41 } as never)
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType: 'content_review', bizId: 41 }), { navigate, warn: vi.fn(), refreshUnreadCount: vi.fn() })
    expect(navigate).toHaveBeenCalledWith('/zsjos/material-library/content-review?batchId=41')
  })
  it.each(['media_account_diagnosis', 'student_delivery_stage'])('resolves %s through authorized server target', async bizType => {
    vi.spyOn(api.mediaStudents, 'target').mockResolvedValue({ personId: 29, targetTab: 'accounts', recordId: 7 })
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType, bizId: 41 }), { navigate, warn: vi.fn(), refreshUnreadCount: vi.fn() })
    expect(api.mediaStudents.target).toHaveBeenCalledWith(bizType, 41)
    expect(navigate).toHaveBeenCalledWith('/zsjos/media-students?personId=29&tab=accounts&accountId=7')
  })
  it('keeps a pre-account positioning card in its course service context', async () => {
    vi.spyOn(api.mediaStudents, 'target').mockResolvedValue({ personId: 29, targetTab: 'positioning', recordId: 41, serviceRelationId: 8 })
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType: 'positioning-card', bizId: 41 }), { navigate, warn: vi.fn(), refreshUnreadCount: vi.fn() })
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('serviceRelationId=8'))
  })
  it('respects the configured message-detail action without fetching the business object', async () => {
    const get = vi.spyOn(contentReviewApi, 'get')
    const navigate = vi.fn()
    await executeNotifyMessageAction(message({ bizType: 'content_review', actionType: 'message_detail' }), { navigate, warn: vi.fn(), refreshUnreadCount: vi.fn() })
    expect(get).not.toHaveBeenCalled()
    expect(navigate).toHaveBeenCalledWith(expect.stringContaining('messageId=11'))
  })
})
