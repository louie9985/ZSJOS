import { afterEach, describe, expect, it, vi } from 'vitest'
import { isExamVisible, reeditCountdown, withExamClock } from './examReedit'
import type { ExamSchedule } from './api'

const revoked = { id: 1, recordStatus: 'REVOKED', serverTime: 1000000, reeditDeadline: 1300000 } as ExamSchedule
afterEach(() => vi.restoreAllMocks())
describe('server-anchored reedit window', () => {
  it('includes request latency, ignores local wall clock and expires at the boundary', async () => {
    vi.spyOn(performance, 'now').mockReturnValue(1200)
    const page = await withExamClock(async () => ({ list: [revoked], total: 1 }))
    const row = page.list[0]
    expect(row.reeditExpiresAt).toBe(301200)
    expect(isExamVisible(row, 300200)).toBe(true)
    expect(reeditCountdown(row, 300200)).toBe('00:01')
    expect(isExamVisible(row, 301200)).toBe(false)
    expect(reeditCountdown(row, 301200)).toBe('00:00')
    vi.spyOn(Date, 'now').mockReturnValue(9999999999999)
    expect(isExamVisible(row, 300200)).toBe(true)
  })
  it('never invents a window for old revoked records', async () => {
    const page = await withExamClock(async () => ({ list: [{ ...revoked, serverTime: undefined }], total: 1 }))
    expect(isExamVisible(page.list[0], 0)).toBe(false)
  })
  it('retains published and draft records without a deadline', () => {
    for (const recordStatus of ['DRAFT', 'PUBLISHED'] as const)
      expect(isExamVisible({ ...revoked, recordStatus, reeditExpiresAt: undefined }, 999999999)).toBe(true)
  })
  it('uses an independent server anchor for each response', async () => {
    vi.spyOn(performance, 'now').mockReturnValueOnce(1000).mockReturnValueOnce(9000)
    const first = await withExamClock(async () => ({ list: [revoked], total: 1 }))
    const second = await withExamClock(async () => ({ list: [{ ...revoked, serverTime: 1008000 }], total: 1 }))
    expect(second.list[0].reeditExpiresAt).toBe(first.list[0].reeditExpiresAt)
  })
})
