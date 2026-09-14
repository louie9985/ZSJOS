import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { accountProfileApi } from './mediaAccountProfile'

afterEach(() => vi.restoreAllMocks())
describe('account positioning submission contract', () => {
  it('keeps the idempotency key and optimistic versions on formal submission', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: 4 } })
    const data = { version: 3, configVersionId: 8, idempotencyKey: 'retry-same-command', changes: { goal: '计划' } }
    await expect(accountProfileApi.submitPositioning(1, data)).resolves.toBe(4)
    expect(post).toHaveBeenCalledWith('/zsjos/media-account/1/profile/positioning/submit', data)
  })
  it('uses the dedicated submission history instead of the maintenance timeline', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    await expect(accountProfileApi.positioningVersions(1, 2)).resolves.toEqual({ list: [], total: 0 })
    expect(get).toHaveBeenCalledWith('/zsjos/media-account/1/profile/positioning/versions', { params: { pageNo: 2, pageSize: 10 } })
  })
})
