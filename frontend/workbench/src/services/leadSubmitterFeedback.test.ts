import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { leadFeedbackApi } from './leadSubmitterFeedback'
import { leadTabForNotifyScene } from './notifyMessageAction'
import { resolveLeadDetailTab } from './leadFollowUp'

describe('Lead sales feedback', () => {
  afterEach(() => vi.restoreAllMocks())
  it('preserves version, file references and intent across retries', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: 41 } })
    const command = { feedback: '已联系\n等待资料', attachmentIds: [9], version: 3, idempotencyKey: 'same-intent' }
    await expect(leadFeedbackApi.create(7, command)).resolves.toBe(41)
    await leadFeedbackApi.create(7, command)
    expect(post.mock.calls).toEqual([
      ['/zsjos/lead/7/submitter-feedback', command], ['/zsjos/lead/7/submitter-feedback', command]
    ])
  })
  it('paginates the specific Lead and preserves backend failures', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    await expect(leadFeedbackApi.page(7, 2)).resolves.toEqual({ list: [], total: 0 })
    expect(get).toHaveBeenCalledWith('/zsjos/lead/7/submitter-feedback/page', { params: { pageNo: 2, pageSize: 10 } })
    get.mockRejectedValue(new Error('没有权限查看'))
    await expect(leadFeedbackApi.page(7)).rejects.toThrow('没有权限查看')
  })
  it('opens feedback only when the server exposes its tab', () => {
    const tab = leadTabForNotifyScene('zsjos.lead.submitter_feedback_created')
    expect(tab).toBe('submitter-feedback')
    expect(resolveLeadDetailTab(['overview', 'submitter-feedback'], tab)).toBe(tab)
    expect(resolveLeadDetailTab(['overview'], tab)).toBe('overview')
    expect(leadTabForNotifyScene('zsjos.lead.submitter_urged')).toBe('follow-ups')
  })
})
