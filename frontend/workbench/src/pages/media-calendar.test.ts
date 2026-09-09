import { readFileSync } from 'node:fs'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { calendarWeekdayLabel, mediaCalendarTone, mediaCalendarWindow, mondayOfWeek, parseCalendarDate } from './MediaCalendarPage'
import { personalCalendarEventPosition, personalCalendarEventTouchesDay } from './PersonalCalendarPage'

describe('calendar separation', () => {
  it('keeps account calendar date calculations stable', () => {
    expect(mediaCalendarWindow(dayjs('2026-08-26'), 'week').start.format('YYYY-MM-DD')).toBe('2026-08-24')
    expect(mediaCalendarWindow(dayjs('2026-08-26'), 'quarter').end.format('YYYY-MM-DD')).toBe('2026-09-30')
    expect(mondayOfWeek(dayjs('2026-09-01')).format('YYYY-MM-DD')).toBe('2026-08-31')
    expect(calendarWeekdayLabel(dayjs('2026-09-06'))).toBe('日')
    expect(parseCalendarDate('2026-09-01').format('YYYY-MM-DD')).toBe('2026-09-01')
    expect(mediaCalendarTone('c_limited_rescue')).toBe('warning')
  })

  it('uses separate account and personal routes and APIs', () => {
    const constants = readFileSync('src/constants.ts', 'utf8')
    const account = readFileSync('src/pages/MediaCalendarPage.tsx', 'utf8')
    const personal = readFileSync('src/pages/PersonalCalendarPage.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')
    expect(constants).toContain("PERSONAL_CALENDAR: '/calendar/personal'")
    expect(constants).toContain("MEDIA_ALL_CALENDAR: '/calendar/all'")
    expect(account).toContain('api.mediaAccount.calendar(')
    expect(account).not.toContain('calendarAll')
    expect(personal).toContain('api.personalCalendar.list')
    expect(personal).toContain("'zsjos:personal-calendar:create'")
    expect(api).not.toContain('/zsjos/media-account/calendar/all')
  })

  it('renders personal events on every touched day with an exclusive end boundary', () => {
    const event = { startTime: '2026-09-01T23:00:00', endTime: '2026-09-03T00:00:00' } as any
    expect(personalCalendarEventTouchesDay(event, dayjs('2026-09-01'))).toBe(true)
    expect(personalCalendarEventTouchesDay(event, dayjs('2026-09-02'))).toBe(true)
    expect(personalCalendarEventTouchesDay(event, dayjs('2026-09-03'))).toBe(false)
    expect(personalCalendarEventTouchesDay({ startTime: '2026-09-04T12:00:00', endTime: '2026-09-04T12:00:00' } as any, dayjs('2026-09-04'))).toBe(true)
  })

  it('positions personal events by minute within the selected day', () => {
    const position = personalCalendarEventPosition({ startTime: '2026-09-01T09:30:00', endTime: '2026-09-01T10:45:00' } as any, dayjs('2026-09-01'))
    expect(position.top).toBeCloseTo(39.5833, 3)
    expect(position.height).toBeCloseTo(5.2083, 3)
  })

  it('clips cross-day event positions to the selected day', () => {
    const position = personalCalendarEventPosition({ startTime: '2026-08-31T23:00:00', endTime: '2026-09-01T01:00:00' } as any, dayjs('2026-09-01'))
    expect(position.top).toBe(0)
    expect(position.height).toBeCloseTo(4.1667, 3)
  })
})
