import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import CalendarSideNavigation, { moveCalendarMonth } from './CalendarSideNavigation'

describe('calendar side navigation', () => {
  it.each([
    ['2026-12-31', 1, '2027-01-01'], ['2026-01-31', -1, '2025-12-01'],
    ['2028-01-31', 1, '2028-02-01'], ['2028-02-29', 1, '2028-03-01'],
  ] as const)('moves %s by %s month without overflowing', (date, direction, expected) => {
    expect(moveCalendarMonth(dayjs(date), direction).format('YYYY-MM-DD')).toBe(expected)
  })
  it('accumulates consecutive clicks and preserves the input date', () => {
    const initial = dayjs('2026-01-31')
    const next = moveCalendarMonth(moveCalendarMonth(initial, 1), 1)
    expect(next.format('YYYY-MM-DD')).toBe('2026-03-01')
    expect(moveCalendarMonth(moveCalendarMonth(next, -1), -1).format('YYYY-MM')).toBe('2026-01')
    expect(initial.format('YYYY-MM-DD')).toBe('2026-01-31')
  })
  it('places available controls on opposite sides of the content', () => {
    const html = renderToStaticMarkup(createElement(CalendarSideNavigation, {
      onNavigate: () => {}, children: createElement('div', { id: 'calendar-body' }),
    }))
    expect(html.indexOf('aria-label="上一月"')).toBeLessThan(html.indexOf('id="calendar-body"'))
    expect(html.indexOf('aria-label="下一月"')).toBeGreaterThan(html.indexOf('id="calendar-body"'))
    expect(html).not.toContain('disabled=""')
  })
  it('supports account calendar period labels', () => {
    const html = renderToStaticMarkup(createElement(CalendarSideNavigation, {
      onNavigate: () => {}, previousLabel: '上一周', nextLabel: '下一周', children: null,
    }))
    expect(html).toContain('aria-label="上一周"')
    expect(html).toContain('aria-label="下一周"')
  })
})
