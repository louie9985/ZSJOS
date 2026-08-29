import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'
import { canOpenAllCalendar, canQueryBpmTasks, canReadAnnouncements } from '../pages/TodayTasksPage'

const source = readFileSync(new URL('../pages/TodayTasksPage.tsx', import.meta.url), 'utf8')

describe('today task permissions', () => {
  it('does not load BPM tasks for a business-task-only user', () => {
    expect(canQueryBpmTasks(['zsjos:business-task:query'])).toBe(false)
  })

  it('shows the BPM todo summary only when the backend grants BPM task query', () => {
    expect(canQueryBpmTasks(['zsjos:business-task:query', 'bpm:task:query'])).toBe(true)
  })

  it('keeps BPM approval handling in the workflow todo route', () => {
    expect(source).toContain('审批待办')
    expect(source).toContain('APP_ROUTES.BPM_TODO')
    expect(source).not.toContain('approveBpmTask')
    expect(source).not.toContain('rejectBpmTask')
    expect(RENDERABLE_APP_ROUTES.has(APP_ROUTES.BPM_TODO)).toBe(true)
  })

  it('loads optional home panels only when their backend permissions are granted', () => {
    expect(canReadAnnouncements(['system:notice:read'])).toBe(true)
    expect(canReadAnnouncements(['zsjos:business-task:query'])).toBe(false)
    expect(canOpenAllCalendar(['zsjos:media-calendar:all-query'])).toBe(true)
    expect(canOpenAllCalendar(['zsjos:media-calendar:query'])).toBe(false)
  })

  it('keeps the four dashboard regions and server-owned destinations', () => {
    expect(source).toContain('home-summary-region')
    expect(source).toContain('home-calendar-panel')
    expect(source).toContain('home-business-panel')
    expect(source).toContain('home-announcement-panel')
    expect(source).toContain('APP_ROUTES.MEDIA_ALL_CALENDAR')
    expect(source).toContain('APP_ROUTES.ANNOUNCEMENTS')
    expect(source).toContain('const bucketOrder: BusinessTaskBucket[]')
    expect(source).toContain("'overdue'")
    expect(source).toContain("'unscheduled'")
    expect(source).toContain('Array.from({ length: 10 }')
    expect(source).toContain('home-stat-card placeholder')
  })

  it('opens lead submitter assist tasks through the supplement flow', () => {
    expect(source).toContain('OPEN_LEAD_SUBMITTER_SUPPLEMENT')
    expect(source).toContain('openSubmitterSupplement: true')
  })
})
