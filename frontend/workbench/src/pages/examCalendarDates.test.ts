import { describe, expect, it } from 'vitest'
import { examToday, examDateError, examHasEnded, initialExamDate } from './examCalendarDates'

describe('exam business dates', () => {
  it('changes the business day at Shanghai midnight regardless of host timezone', () => {
    expect(examToday(new Date('2026-10-08T15:59:59Z'))).toBe('2026-10-08')
    expect(examToday(new Date('2026-10-08T16:00:00Z'))).toBe('2026-10-09')
  })
  it('rejects past dates but permits ongoing ranges and inclusive end dates', () => {
    expect(examDateError('2026-10-07', '2026-10-07', '2026-10-08')).toContain('北京时间今天')
    expect(examDateError('2026-10-01', '2026-10-08', '2026-10-08')).toBeUndefined()
    expect(examDateError('2026-10-08', '2026-10-08', '2026-10-08')).toBeUndefined()
    expect(examDateError('2026-10-09', '2026-10-08', '2026-10-08')).toContain('早于开始')
    expect(examDateError(undefined)).toContain('完整')
  })
  it('recognizes expired drafts without using displayStatus', () => {
    expect(examHasEnded({ scheduleType: 'EXACT', exactDate: '2026-10-07' }, '2026-10-08')).toBe(true)
    expect(examHasEnded({ scheduleType: 'MULTI_DAY', endDate: '2026-10-08' }, '2026-10-08')).toBe(false)
  })
  it('defaults old calendar selections to today without changing future selections', () => {
    expect(initialExamDate('2026-09-01', '2026-10-08')).toBe('2026-10-08')
    expect(initialExamDate('2026-11-01', '2026-10-08')).toBe('2026-11-01')
  })
})
