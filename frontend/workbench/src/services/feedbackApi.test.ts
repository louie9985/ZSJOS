import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { feedbackApi } from './feedbackApi'

describe('feedback API contract', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uses the three independent creation endpoints with config version and idempotency keys', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: 1 } })

    await feedbackApi.create('REQUIREMENT', 3, { title: '需求' })
    await feedbackApi.create('BUG', 4, { title: '问题' })
    await feedbackApi.create('SUPPORT', 5, { title: '支持' })

    expect(post.mock.calls.map(call => call[0])).toEqual([
      '/zsjos/feedback/requirement/create',
      '/zsjos/feedback/bug/create',
      '/zsjos/feedback/support/create'
    ])
    expect(post.mock.calls.map(call => call[1])).toEqual([
      expect.objectContaining({
        configVersion: 3,
        values: { title: '需求' },
        idempotencyKey: expect.any(String)
      }),
      expect.objectContaining({
        configVersion: 4,
        values: { title: '问题' },
        idempotencyKey: expect.any(String)
      }),
      expect.objectContaining({
        configVersion: 5,
        values: { title: '支持' },
        idempotencyKey: expect.any(String)
      })
    ])
  })

  it('keeps employee paging filters and versioned commands on their public paths', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: { code: 0, data: { list: [], total: 0 } }
    })
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: { code: 0, data: true } })
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: true } })

    await feedbackApi.myPage({
      pageNo: 2,
      pageSize: 10,
      feedbackType: 'BUG',
      status: 'IN_PROGRESS'
    })
    await feedbackApi.markRead(42, 6)
    await feedbackApi.reply(42, 7, '补充复现步骤', [11])
    await feedbackApi.submitSurvey(42, 8, { rating: 5 })

    expect(get).toHaveBeenCalledWith('/zsjos/feedback/my-page', {
      params: { pageNo: 2, pageSize: 10, feedbackType: 'BUG', status: 'IN_PROGRESS' }
    })
    expect(put).toHaveBeenCalledWith(
      '/zsjos/feedback/42/read',
      expect.objectContaining({ version: 6, idempotencyKey: expect.any(String) })
    )
    expect(post).toHaveBeenNthCalledWith(
      1,
      '/zsjos/feedback/42/reply',
      expect.objectContaining({
        version: 7,
        content: '补充复现步骤',
        attachmentIds: [11]
      })
    )
    expect(post).toHaveBeenNthCalledWith(
      2,
      '/zsjos/feedback/42/survey',
      expect.objectContaining({ version: 8, values: { rating: 5 } })
    )
  })
})
