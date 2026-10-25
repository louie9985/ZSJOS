import { describe, expect, it } from 'vitest'
import { homeRestDates, mergeHomeRestDays } from './homeRestDays'

describe('homepage rest date import', () => {
  it('filters month, unmarked dates and invalid days, sorts and deduplicates', () => {
    const rows = ['2024-02-29', '2024-02-02', '2024-02-30', '2024-03-01', '2024-02-02'].map(date => ({ date, rest: true, version: 1 }))
    rows.push({ date: '2024-02-03', rest: false, version: 1 })
    expect(homeRestDates(rows, 2024, 2)).toEqual([2, 29])
    expect(homeRestDates(rows, 2023, 2)).toEqual([])
  })
  it('preserves both existing categories and imports only missing dates idempotently', () => {
    const next = mergeHomeRestDays([2], [3], [2, 3, 4], 'rest')
    expect(next).toEqual({ restDays: [2, 4], paidRestDays: [3] })
    expect(mergeHomeRestDays(next.restDays, next.paidRestDays, [2, 3, 4], 'paid')).toEqual(next)
    expect(mergeHomeRestDays([2], [3], [2, 3, 4], 'paid')).toEqual({ restDays: [2], paidRestDays: [3, 4] })
    expect(mergeHomeRestDays([2], [3], [], 'rest')).toEqual({ restDays: [2], paidRestDays: [3] })
  })
})
