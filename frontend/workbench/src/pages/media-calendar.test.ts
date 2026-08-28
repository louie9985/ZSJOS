import { readFileSync } from 'node:fs'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { mediaCalendarTone, mediaCalendarWindow } from './MediaCalendarPage'

describe('media account calendar', () => {
  it('uses Monday through Sunday for the week view and exact natural quarter bounds', () => {
    const week = mediaCalendarWindow(dayjs('2026-08-26'), 'week')
    expect(week.start.format('YYYY-MM-DD')).toBe('2026-08-24')
    expect(week.end.format('YYYY-MM-DD')).toBe('2026-08-30')
    const quarter = mediaCalendarWindow(dayjs('2026-08-26'), 'quarter')
    expect(quarter.start.format('YYYY-MM-DD')).toBe('2026-07-01')
    expect(quarter.end.format('YYYY-MM-DD')).toBe('2026-09-30')
  })

  it('maps the four current-status groups to stable semantic tones', () => {
    expect(['a_active_growth', 'b_active_no_lead', 'c_limited_rescue', 'd_restart_paused']
      .map(value => mediaCalendarTone(value))).toEqual(['success', 'primary', 'warning', 'info'])
    expect(mediaCalendarTone(undefined)).toBe('neutral')
    expect(mediaCalendarTone('a_active_growth', 'warning')).toBe('warning')
  })

  it('registers the server-owned route and keeps the calendar read-only', () => {
    const constants = readFileSync('src/constants.ts', 'utf8')
    const routes = readFileSync('src/layouts/RouteHost.tsx', 'utf8')
    const page = readFileSync('src/pages/MediaCalendarPage.tsx', 'utf8')
    expect(constants).toContain("MEDIA_CALENDAR: '/calendar/overview'")
    expect(constants).toContain("MEDIA_ALL_CALENDAR: '/calendar/all'")
    expect(routes).toContain('APP_ROUTES.MEDIA_CALENDAR')
    expect(routes).toContain('APP_ROUTES.MEDIA_ALL_CALENDAR')
    expect(page).not.toContain('api.mediaAccount.maintain')
    expect(page).not.toContain('api.simpleUsers()')
    expect(page).toContain('api.mediaAccount.calendarCandidates()')
    expect(page).toContain('api.mediaAccount.calendarAll')
    expect(page).toContain("'日历日程'")
    expect(page).toContain('media-schedule-page')
    expect(page).not.toContain('draggable')
  })
})
