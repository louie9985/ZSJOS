import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { courseCalendarEventPosition, courseCalendarEventTouchesDay } from './CourseCalendarPage'

describe('course calendar day details', () => {
  it('includes courses spanning the selected day and clips the timeline position', () => {
    const event = { startTime: '2026-09-01T23:30:00', endTime: '2026-09-02T01:15:00' } as any
    expect(courseCalendarEventTouchesDay(event, dayjs('2026-09-02'))).toBe(true)
    expect(courseCalendarEventPosition(event, dayjs('2026-09-02'))).toMatchObject({ top: 0 })
  })
  it('calculates minute-level course placement', () => {
    const position = courseCalendarEventPosition({ startTime: '2026-09-01T09:30:00', endTime: '2026-09-01T10:45:00' } as any, dayjs('2026-09-01'))
    expect(position.top).toBeCloseTo(39.5833, 3)
    expect(position.height).toBeCloseTo(5.2083, 3)
  })
})
