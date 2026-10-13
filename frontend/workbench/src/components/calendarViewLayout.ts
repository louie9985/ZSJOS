import dayjs, { type Dayjs } from 'dayjs'
export type CalendarView = 'day' | 'week' | 'month'
export const monday = (date: Dayjs) => date.startOf('day').subtract((date.day() + 6) % 7, 'day')
export function calendarViewRange(date: Dayjs, view: CalendarView) {
  const start = view === 'month' ? date.startOf('month').startOf('week') : view === 'week' ? monday(date) : date.startOf('day')
  const end = view === 'month' ? date.endOf('month').endOf('week').add(1, 'millisecond') : start.add(view === 'week' ? 7 : 1, 'day')
  return { start, end }
}
export const moveCalendarView = (date: Dayjs, view: CalendarView, direction: -1 | 1) =>
  view === 'month' ? date.startOf('month').add(direction, 'month') : date.add(direction * (view === 'week' ? 7 : 1), 'day')
export type TimeGridEvent = { id: number; title: string; start: Dayjs; end: Dayjs; allDay?: boolean; color?: string | null }
export type TimeGridPosition = { event: TimeGridEvent; start: number; end: number; column: number; columns: number }
export const touchesGridDay = (event: TimeGridEvent, date: Dayjs) => event.start.isSame(event.end)
  ? !event.start.isBefore(date.startOf('day')) && event.start.isBefore(date.startOf('day').add(1, 'day'))
  : event.start.isBefore(date.startOf('day').add(1, 'day')) && event.end.isAfter(date.startOf('day'))
export function layoutGridDay(events: TimeGridEvent[], date: Dayjs): TimeGridPosition[] {
  const base = date.startOf('day')
  const rows = events.filter(e => !e.allDay && touchesGridDay(e, base)).map(event => {
    const start = Math.max(0, event.start.diff(base, 'minute', true))
    return { event, start, end: Math.min(1440, Math.max(start + 1, event.end.diff(base, 'minute', true))), column: 0, columns: 1 }
  }).sort((a,b) => a.start - b.start || b.end - a.end || a.event.id - b.event.id)
  let group: TimeGridPosition[] = [], groupEnd = -1
  const finish = () => {
    const ends: number[] = []
    for (const row of group) {
      let column = ends.findIndex(end => end <= row.start)
      if (column < 0) column = ends.length
      ends[column] = row.end; row.column = column
    }
    group.forEach(row => { row.columns = ends.length })
    group = []
  }
  for (const row of rows) {
    if (row.start >= groupEnd) { finish(); groupEnd = -1 }
    group.push(row); groupEnd = Math.max(groupEnd, row.end)
  }
  finish()
  return rows
}
export const todayWallTime = () => dayjs(new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
}).format(new Date()).replace(' ', 'T'))
