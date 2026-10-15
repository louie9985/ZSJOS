import { describe, expect, it } from 'vitest'
import { examOptionDates } from './DeliveryClassExamCalendar'

describe('class calendar reference dates', () => {
  it('copies single-day and full multi-day dates without deriving dates from labels', () => {
    expect(examOptionDates({ id: 1, scheduleType: 'EXACT', displayName: '参考', exactDate: '2026-11-02' }))
      .toEqual({ start: '2026-11-02', end: '2026-11-02' })
    expect(examOptionDates({ id: 2, scheduleType: 'MULTI_DAY', displayName: '参考', startDate: '2026-10-30', endDate: '2026-11-03' }))
      .toEqual({ start: '2026-10-30', end: '2026-11-03' })
    expect(examOptionDates({ id: 3, scheduleType: 'EXACT', displayName: '2026-11-02' })).toBeUndefined()
  })
})
