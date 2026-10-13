import { describe, expect, it } from 'vitest'
import dayjs from 'dayjs'
import { calendarDayTime, layoutCalendarDay } from './CalendarDayTimeline'
const day = dayjs('2026-10-13')
const item = (id: number, startTime: string, endTime: string) => ({ id, title: '完整课程名称', startTime, endTime })
describe('calendar day layout', () => {
  it('separates short, overlapping and zero-length cards without covering their title', () => {
    const result = layoutCalendarDay([item(1,'2026-10-13T09:01:00','2026-10-13T09:02:00'), item(2,'2026-10-13T09:03:00','2026-10-13T09:03:00'),item(3,'2026-10-13T09:15:00','2026-10-13T10:00:00')],day)
    expect(result.lanes).toBe(2)
    expect(result.rows.map(row => row.lane)).toEqual([0,1,0])
    expect(result.rows.every(row => row.last > row.first)).toBe(true)
  })
  it('keeps full cross-day times and excludes a course ending at this midnight', () => {
    const crossing=item(1,'2026-10-12T23:00:00','2026-10-14T01:00:00')
    const result=layoutCalendarDay([crossing,item(2,'2026-10-12T20:00:00','2026-10-13T00:00:00')],day)
    expect(result.rows).toHaveLength(1)
    expect(result.rows[0]).toMatchObject({first:0,last:96})
    expect(calendarDayTime(crossing)).toBe('2026-10-12 23:00 - 2026-10-14 01:00')
  })
})
