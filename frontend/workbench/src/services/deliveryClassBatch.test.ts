import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, http } from './api'

afterEach(() => vi.restoreAllMocks())
describe('delivery class batch transport', () => {
  it('submits all planners once and returns the created identifiers', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: [101, 102] } })
    const payload = { className: '同名班级', startDate: '2026-12-01', homeroomUserIds: [20, 21] }
    expect(await api.deliveryClasses.createBatch(payload)).toEqual([101, 102])
    expect(post).toHaveBeenCalledExactlyOnceWith('/zsjos/delivery-class/batch-create', payload)
  })
  it('propagates a batch failure without retrying individual creates', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 1001, msg: '班主任不可用' } })
    await expect(api.deliveryClasses.createBatch({ className: '同名班级', startDate: '2026-12-01', homeroomUserIds: [20, 21] })).rejects.toThrow('班主任不可用')
    expect(post).toHaveBeenCalledTimes(1)
  })
  it('preserves the existing single-create response and request', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: 101 } })
    const payload = { className: '原单班', startDate: '2026-12-01', homeroomUserId: 20 }
    expect(await api.deliveryClasses.create(payload)).toBe(101)
    expect(post).toHaveBeenCalledExactlyOnceWith('/zsjos/delivery-class/create', payload)
  })
})
