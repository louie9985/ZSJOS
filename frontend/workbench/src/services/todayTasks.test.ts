import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'
import { canOpenAllCalendar, canQueryBpmTasks, canReadAnnouncements } from '../pages/TodayTasksPage'

const source = readFileSync(new URL('../pages/TodayTasksPage.tsx', import.meta.url), 'utf8')
const styles = readFileSync(new URL('../styles/pages/today-tasks.css', import.meta.url), 'utf8')

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

  it('opens submitter-assistance tasks in the Lead detail route', () => {
    expect(source).toContain("task.actionCode === 'OPEN_LEAD_SUBMITTER_ASSIST'")
    expect(source).toContain('state: { leadId: task.bizId }')
  })

  it('keeps the home calendar month and weekday labels in Chinese', () => {
    const calendarPanel = source.split('function HomeCalendarPanel')[1] ?? ''
    expect(source).toContain("import zhCNCalendarLocale from 'antd/es/calendar/locale/zh_CN'")
    expect(source).toContain('const homeCalendarLocale = {')
    expect(source).toContain("shortMonths: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']")
    expect(source).toContain("shortWeekDays: ['日', '一', '二', '三', '四', '五', '六']")
    expect(calendarPanel).toContain('locale={homeCalendarLocale}')
  })

  it('uses the home calendar only as a current-month preview', () => {
    const calendarPanel = source.split('function HomeCalendarPanel')[1] ?? ''
    expect(calendarPanel).toContain('headerRender={() => null}')
    expect(calendarPanel).toContain('APP_ROUTES.MEDIA_ALL_CALENDAR')
    expect(calendarPanel).toContain("if (info.source === 'date') openCalendar()")
    expect(calendarPanel).not.toContain('onPanelChange')
  })

  it('keeps the compact home calendar spacing inside the fixed dashboard cell', () => {
    expect(styles).toContain('--home-calendar-cell-h: var(--crm-sp-6)')
    expect(styles).toContain('--home-calendar-weekday-h: var(--crm-sp-5)')
    expect(styles).toContain('.home-calendar-panel .home-panel-header.compact')
    expect(styles).toContain('.home-calendar-panel .ant-picker-calendar .ant-picker-panel')
    expect(styles).toContain('.home-calendar-panel .ant-picker-calendar .ant-picker-date-panel')
    expect(styles).toContain('.home-calendar-panel .ant-picker-calendar .ant-picker-body')
    expect(styles).toContain('.home-calendar-panel .ant-picker-calendar-mini .ant-picker-content')
    expect(styles).toContain('height: 100%')
    expect(styles).toContain('table-layout: fixed')
    expect(styles).toContain('height: var(--home-calendar-cell-h)')
    expect(styles).toContain('line-height: var(--home-calendar-weekday-h)')
    expect(styles).not.toContain('.home-calendar-panel .ant-picker-cell:not(.ant-picker-cell-in-view)')
  })

  it('labels highlighted home announcements as pinned entries', () => {
    const announcementPanel = source.split('function AnnouncementPanel')[1] ?? ''
    expect(announcementPanel).toContain("item.highlighted ? ' highlighted' : ''")
    expect(announcementPanel).toContain('<Tag color="gold">置顶</Tag>')
    expect(announcementPanel).toContain('home-announcement-title-text')
    expect(announcementPanel).not.toContain('<Tag color="gold">高亮</Tag>')
  })
})
