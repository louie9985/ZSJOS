import { describe, expect, it } from 'vitest'
import { examBackgroundStyle, examScheduleBackgroundStyle } from './examCalendarColor'

describe('exam background colors', () => {
  it.each(['DRAFT', 'PUBLISHED', 'REVOKED'] as const)('prioritizes %s over saved color', recordStatus => {
    for (const backgroundColor of [undefined, null, '', 'red', '#ffcc00', '#001133']) {
      const schedule = Object.freeze({ recordStatus, backgroundColor })
      expect(examScheduleBackgroundStyle(schedule)).toEqual(
        recordStatus === 'PUBLISHED' ? examBackgroundStyle(backgroundColor) : undefined)
      expect(schedule.backgroundColor).toBe(backgroundColor)
    }
  })
  it.each([undefined, null, '', 'red', '#fff', '#00000000', 'url(https://example.com)', '#123456;display:none'])('keeps theme defaults for invalid or absent %s', value => {
    expect(examBackgroundStyle(value)).toBeUndefined()
  })
  it.each([['#ffffff', '#000000'], ['#000000', '#ffffff'], ['#ffcc00', '#000000'], ['#001133', '#ffffff']])('contrasts text against %s', (background, foreground) => {
    expect(examBackgroundStyle(background)).toEqual({ backgroundColor: background, borderColor: background, color: foreground })
  })
})
