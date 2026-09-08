import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { scheduleInput, scheduleStatusLabel } from './ExamCalendarPage'

describe('ExamCalendarPage contracts', () => {
  it('submits only selected exam conditions without leaking the navigation category', () => {
    expect(scheduleInput({ scheduleType: 'EXACT', exactDate: dayjs('2026-10-10'), categoryId: 2,
      productId: 8, selectedAttrs: { level: '2', place: '' } })).toMatchObject({
      productId: 8, categoryId: undefined, selectedAttrs: { level: '2' }
    })
  })
  it('serializes exact schedules without rough dates', () => {
    expect(scheduleInput({
      scheduleType: 'EXACT', exactDate: dayjs('2026-10-10'), categoryId: 2, remark: ' 上午场 '
    })).toEqual({ scheduleType: 'EXACT', exactDate: '2026-10-10', categoryId: 2, remark: '上午场' })
  })

  it('serializes rough schedules as an inclusive date range', () => {
    expect(scheduleInput({
      scheduleType: 'ROUGH', roughRange: [dayjs('2026-10-01'), dayjs('2026-10-15')],
      categoryId: 3
    })).toEqual({
      scheduleType: 'ROUGH', roughStartDate: '2026-10-01', roughEndDate: '2026-10-15',
      categoryId: 3, remark: undefined
    })
  })

  it('uses the approved lifecycle labels', () => {
    expect(scheduleStatusLabel('UPCOMING')).toBe('即将开始')
    expect(scheduleStatusLabel('IN_PROGRESS')).toBe('正在进行')
    expect(scheduleStatusLabel('ENDED')).toBe('已结束')
  })
})
