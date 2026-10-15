import { describe, expect, it } from 'vitest'
import { classDateRange } from './classDatePresets'

describe('class date quick filters', () => {
  it('uses Shanghai calendar days including today', () => {
    const now = new Date('2026-10-14T16:30:00Z')
    expect(classDateRange('month', now)).toEqual(['2026-10-01', '2026-10-31'])
    expect(classDateRange('next', now)).toEqual(['2026-11-01', '2026-11-30'])
    expect(classDateRange('30', now)).toEqual(['2026-10-15', '2026-11-13'])
    expect(classDateRange('90', now)).toEqual(['2026-10-15', '2027-01-12'])
  })
  it('handles leap February and year rollover', () => {
    expect(classDateRange('next', new Date('2027-12-31T12:00:00Z'))).toEqual(['2028-01-01', '2028-01-31'])
    expect(classDateRange('month', new Date('2028-02-10T12:00:00Z'))).toEqual(['2028-02-01', '2028-02-29'])
    expect(classDateRange('all')).toBeUndefined()
    expect(classDateRange('custom')).toBeUndefined()
  })
})
