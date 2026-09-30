import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import type { ExamSchedule } from '../services/api'
import { calendarWindow, coversExamDay, layoutMultiDayWeek, schedulesForRecordStatus } from './examCalendarLayout'

const multiDay = (id: number, start: string, end: string, status: ExamSchedule['recordStatus'] = 'PUBLISHED'): ExamSchedule => ({
  id, scheduleType: 'MULTI_DAY', scheduleName: '多日考试', startDate: start, endDate: end,
  recordStatus: status, displayStatus: status, categoryPathSnapshot: []
})

describe('exam calendar range layout', () => {
  it('queries all 42 visible days, including adjacent months and year boundaries', () => {
    const { start, end } = calendarWindow(dayjs('2026-12-10'))
    expect(start.format('YYYY-MM-DD')).toBe('2026-11-29')
    expect(end.format('YYYY-MM-DD')).toBe('2027-01-09')
    expect(end.diff(start, 'day')).toBe(41)
  })

  it('includes both boundaries and one-day windows without inventing exact dates', () => {
    const row = multiDay(1, '2026-10-10', '2026-10-20')
    expect(['2026-10-09', '2026-10-10', '2026-10-20', '2026-10-21'].map(day => coversExamDay(row, day)))
      .toEqual([false, true, true, false])
    expect(coversExamDay(multiDay(2, '2026-10-10', '2026-10-10'), '2026-10-10')).toBe(true)
    expect(row.exactDate).toBeUndefined()
    expect(coversExamDay({ ...row, scheduleType: 'EXACT', exactDate: '2026-10-12' }, '2026-10-10')).toBe(false)
  })

  it('clips a multi-week and cross-month window with continuation flags', () => {
    const row = multiDay(1, '2026-09-28', '2026-10-13')
    const first = layoutMultiDayWeek([row], dayjs('2026-09-27')).segments[0]
    const middle = layoutMultiDayWeek([row], dayjs('2026-10-04')).segments[0]
    const last = layoutMultiDayWeek([row], dayjs('2026-10-11')).segments[0]
    expect([first.startColumn, first.endColumn, first.continuesBefore, first.continuesAfter]).toEqual([1, 6, false, true])
    expect([middle.startColumn, middle.endColumn, middle.continuesBefore, middle.continuesAfter]).toEqual([0, 6, true, true])
    expect([last.startColumn, last.endColumn, last.continuesBefore, last.continuesAfter]).toEqual([0, 2, true, false])
    expect([first, middle, last].every(segment => segment.schedule === row)).toBe(true)
  })

  it('separates overlapping inclusive endpoints but reuses a lane the following day', () => {
    const rows = [multiDay(3, '2026-10-07', '2026-10-09'), multiDay(2, '2026-10-06', '2026-10-06'), multiDay(1, '2026-10-04', '2026-10-06')]
    const result = layoutMultiDayWeek(rows, dayjs('2026-10-04'))
    expect(result.segments.map(segment => [segment.schedule.id, segment.lane])).toEqual([[1, 0], [2, 1], [3, 0]])
    expect(rows.map(row => row.id)).toEqual([3, 2, 1])
  })

  it('renders all overlapping records without a lane limit', () => {
    const rows = [multiDay(1, '2026-10-04', '2026-10-10'), multiDay(2, '2026-10-04', '2026-10-10'), multiDay(3, '2026-10-05', '2026-10-06'), multiDay(4, '2026-10-06', '2026-10-08')]
    const result = layoutMultiDayWeek(rows, dayjs('2026-10-04'))
    expect(result.segments).toHaveLength(4)
    expect(result.laneCount).toBe(4)
    expect(result).not.toHaveProperty('hiddenCounts')
    expect(rows.filter(row => coversExamDay(row, '2026-10-06'))).toHaveLength(4)
  })

  it('ignores missing, reversed and non-intersecting ranges', () => {
    const rows = [multiDay(1, '2026-10-01', '2026-10-03'), multiDay(2, '2026-10-11', '2026-10-12'),
      multiDay(3, '2026-10-07', '2026-10-05'), { ...multiDay(4, '2026-10-04', '2026-10-08'), startDate: undefined }]
    expect(layoutMultiDayWeek(rows, dayjs('2026-10-04'))).toEqual({ segments: [], laneCount: 0 })
  })

  it('filters publication status independently of time phases for both exam types', () => {
    const rows = [multiDay(1, '2020-01-01', '2030-01-01'), multiDay(2, '2026-10-01', '2026-10-05', 'DRAFT'), multiDay(3, '2026-10-01', '2026-10-05', 'REVOKED')]
    expect(schedulesForRecordStatus(rows)).toHaveLength(3)
    for (const status of ['PUBLISHED', 'DRAFT', 'REVOKED'] as const) expect(schedulesForRecordStatus(rows, status)).toHaveLength(1)
    for (const status of ['UPCOMING', 'IN_PROGRESS', 'ENDED'] as const) {
      const published = { ...rows[0], displayStatus: status }
      const single = { ...published, scheduleType: 'EXACT' as const, exactDate: '2026-10-10' }
      expect(schedulesForRecordStatus([published, single], 'PUBLISHED')).toEqual([published, single])
      expect(schedulesForRecordStatus([published, single], 'DRAFT')).toHaveLength(0)
    }
  })
})
