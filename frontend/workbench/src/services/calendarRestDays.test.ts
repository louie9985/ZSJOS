import { describe, expect, it } from 'vitest'
import dayjs from 'dayjs'
import { restDayRange, stageRestDays } from './calendarRestDays'

describe('home rest day drafts', () => {
  it('keeps cross-month changes and original versions', () => {
    const base = { '2026-10-01': { date: '2026-10-01', rest: true, version: 3 } }
    const first = stageRestDays(base, {}, ['2026-10-01'], false)
    const result = stageRestDays(base, first, ['2026-11-01'], true)
    expect(Object.values(result)).toEqual([{ date: '2026-10-01', rest: false, version: 3 }, { date: '2026-11-01', rest: true, version: 0 }])
    expect(base['2026-10-01'].rest).toBe(true)
  })
  it('undoing the intended state removes the delta without resetting unrelated dates', () => {
    const first = stageRestDays({}, {}, ['2026-10-01','2026-10-02'], true)
    expect(stageRestDays({}, first, ['2026-10-01'], false)).toEqual({ '2026-10-02': { date: '2026-10-02', rest: true, version: 0 } })
  })
  it('queries all 42 visible dates including neighboring months', () => {
    const range = restDayRange(dayjs('2026-02-01'))
    expect(dayjs(range.endDate).diff(dayjs(range.startDate), 'day')).toBe(41)
    expect(range.startDate).toBe('2026-02-01')
    expect(range.endDate).toBe('2026-03-14')
  })
})
