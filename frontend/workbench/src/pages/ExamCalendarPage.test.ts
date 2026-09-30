import dayjs from 'dayjs'
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { scheduleInput, scheduleStatusLabel } from './ExamCalendarPage'

describe('ExamCalendarPage contracts', () => {
  it('serializes exact schedules without multiDay dates', () => {
    expect(scheduleInput({
      scheduleType: 'EXACT', exactDate: dayjs('2026-10-10'), scheduleName: '任意名称', remark: ' 上午场 '
    })).toEqual({ scheduleType: 'EXACT', exactDate: '2026-10-10', scheduleName: '任意名称', remark: '上午场' })
  })

  it('serializes multiDay schedules as an inclusive date range', () => {
    expect(scheduleInput({
      scheduleType: 'MULTI_DAY', multiDayRange: [dayjs('2026-10-01'), dayjs('2026-10-15')],
      scheduleName: '自由考期'
    })).toEqual({
      scheduleType: 'MULTI_DAY', startDate: '2026-10-01', endDate: '2026-10-15',
      scheduleName: '自由考期', remark: undefined
    })
  })

  it('uses the approved lifecycle labels', () => {
    expect(scheduleStatusLabel('PUBLISHED')).toBe('已发布')
    expect(scheduleStatusLabel('DRAFT')).toBe('草稿')
    expect(scheduleStatusLabel('REVOKED')).toBe('已撤销')
  })

  it('opens day details on date selection instead of creating a schedule', () => {
    const source = readFileSync(new URL('./ExamCalendarPage.tsx', import.meta.url), 'utf8')
    expect(source).toContain('onDay={setDayDetail}')
    expect(source).not.toContain("if (canManage && info.source === 'date') openCreate(date)")
  })

  it('keeps the manual name and never derives it from catalog fields', () => {
    const values = { scheduleType: 'EXACT' as const, exactDate: dayjs('2026-10-10'), scheduleName: '  秋季专场  ', productId: 8, categoryId: 2 }
    expect(scheduleInput(values)).toEqual({ scheduleType: 'EXACT', exactDate: '2026-10-10', scheduleName: '秋季专场', remark: undefined })
  })
})
