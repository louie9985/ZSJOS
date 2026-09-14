import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { interviewMissingFields, positioningInterviewApi, type InterviewContext } from './positioningInterviewApi'
afterEach(() => vi.restoreAllMocks())
describe('positioning interview contract', () => {
  it('uses the independent versioned command for partial saves and completion', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: { version: 2 } } })
    const command = { version: 1, idempotencyKey: 'retry-key', templateVersionId: 5, items: [], attachmentIds: [] }
    await positioningInterviewApi.save(9, command)
    await positioningInterviewApi.save(9, command, true)
    expect(post).toHaveBeenNthCalledWith(1, '/zsjos/student/service/9/positioning-interview/draft', command)
    expect(post).toHaveBeenNthCalledWith(2, '/zsjos/student/service/9/positioning-interview/complete', command)
  })
  it('accepts refusal and not communicated but rejects absent or foreign statuses', () => {
    const context = { fields: [
      { key: 'required', enabled: true, required: true },
      { key: 'disabled', enabled: false, required: true },
      { key: 'system', enabled: true, required: true, systemField: true },
    ].map((field, sort) => ({ title: field.key, type: 'text', sort, ...field, systemField: field.systemField ?? false })), statusOptions: { NOT_COMMUNICATED: '未沟通', CLIENT_REFUSED: '客户拒绝回答' } } satisfies Pick<InterviewContext, 'fields' | 'statusOptions'>
    expect(interviewMissingFields(context, [])).toHaveLength(1)
    expect(interviewMissingFields(context, [{ fieldKey: 'required', status: 'OTHER' }])).toHaveLength(1)
    for (const status of ['NOT_COMMUNICATED', 'CLIENT_REFUSED']) expect(interviewMissingFields(context, [{ fieldKey: 'required', status }])).toHaveLength(0)
  })
  it('propagates backend conflicts rather than pretending a save succeeded', async () => {
    vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 1900090003, msg: '版本冲突' } })
    await expect(positioningInterviewApi.save(9, { version: 0, idempotencyKey: 'key', templateVersionId: 2, items: [], attachmentIds: [] })).rejects.toThrow('版本冲突')
  })
})
