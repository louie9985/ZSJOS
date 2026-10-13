import { useEffect, useState, type ReactNode } from 'react'
import { Button, Empty, Grid, Modal, Popover } from 'antd'
import { CloseOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import { calendarWallTime } from '../services/calendarSearch'
import { examBackgroundStyle } from '../pages/examCalendarColor'
import '../styles/components/calendar-day-timeline.css'

export type CalendarDayItem = { id: number; title: string; startTime: string | number; endTime: string | number; allDay?: boolean; backgroundColor?: string | null }
export function calendarDayTime(item: CalendarDayItem) {
  const start = calendarWallTime(item.startTime), end = calendarWallTime(item.endTime)
  const range = start.isSame(end, 'day') ? `${start.format('YYYY-MM-DD HH:mm')} - ${end.format('HH:mm')}` : `${start.format('YYYY-MM-DD HH:mm')} - ${end.format('YYYY-MM-DD HH:mm')}`
  return item.allDay ? `全天 · ${start.format('YYYY-MM-DD')} - ${end.format('YYYY-MM-DD')}` : range
}

export function layoutCalendarDay(items: CalendarDayItem[], day: Dayjs) {
  const start = day.startOf('day'), end = start.add(1, 'day')
  const rows = items.filter(item => {
    const from = calendarWallTime(item.startTime), to = calendarWallTime(item.endTime)
    return from.isSame(to) ? !from.isBefore(start) && from.isBefore(end) : from.isBefore(end) && to.isAfter(start)
  }).map(item => {
    const from = Math.max(0, calendarWallTime(item.startTime).diff(start, 'minute'))
    const to = Math.min(1440, calendarWallTime(item.endTime).diff(start, 'minute'))
    return { item, first: Math.floor(from / 15), last: Math.min(96, Math.max(Math.ceil(to / 15), Math.floor(from / 15) + 1)), lane: 0 }
  }).sort((a, b) => a.first - b.first || a.last - b.last || a.item.id - b.item.id)
  const lanes: number[] = []
  for (const row of rows) {
    // Include the visible quarter-hour cell in collision checks, including zero-duration events.
    let lane = lanes.findIndex(until => until <= row.first)
    if (lane < 0) lane = lanes.length
    lanes[lane] = row.last; row.lane = lane
  }
  return { rows, lanes: lanes.length }
}

export default function CalendarDayTimeline({ items, day, highlightedId, initialOpenId, emptyText, renderDetails }: {
  items: CalendarDayItem[]; day: Dayjs; highlightedId?: number; initialOpenId?: number; emptyText: string;
  renderDetails: (id: number, close: () => void) => ReactNode;
}) {
  const [selected, setSelected] = useState<number>()
  useEffect(() => { setSelected(initialOpenId) }, [initialOpenId])
  const desktop = Grid.useBreakpoint().md
  const { rows, lanes } = layoutCalendarDay(items, day)
  const active = rows.find(row => row.item.id === selected)?.item
  const close = () => setSelected(undefined)
  if (!rows.length) return <Empty description={emptyText} />
  const firstHour = Math.floor(rows[0].first / 4)
  const lastHour = Math.min(24, Math.ceil(Math.max(...rows.map(row => row.last)) / 4))
  const offset = firstHour * 4
  const details = active && <section className="calendar-day-detail" aria-label="日程详细信息">
    <header><h3>{active.title}</h3><Button type="text" icon={<CloseOutlined />} aria-label="关闭日程详情" onClick={close} /></header>
    <p>{calendarDayTime(active)}</p>
    {renderDetails(active.id, close)}
  </section>
  return <div className="calendar-day-scroll"><div className="calendar-day-grid" style={{ gridTemplateColumns: `56px repeat(${lanes}, minmax(220px, 1fr))`, gridTemplateRows: `repeat(${(lastHour - firstHour) * 4}, minmax(14px, auto))` }}>
    {Array.from({ length: lastHour - firstHour }, (_, index) => <div key={index} className="calendar-day-hour" style={{ gridRow: `${index * 4 + 1} / span 4`, gridColumn: `1 / span ${lanes + 1}` }}><span>{String(firstHour + index).padStart(2, '0')}:00</span></div>)}
    {rows.map(({ item, first, last, lane }) => <div className="calendar-day-event" key={item.id} style={{ gridRow: `${first - offset + 1} / ${last - offset + 1}`, gridColumn: lane + 2 }}>
      <Popover trigger={[]} placement="bottom" open={!!desktop && selected === item.id} content={selected === item.id ? details : null} destroyOnHidden>
        <button type="button" className="calendar-day-card" style={examBackgroundStyle(item.backgroundColor)} data-calendar-colored={!!item.backgroundColor} data-calendar-located={item.id === highlightedId} aria-expanded={selected === item.id} onClick={() => setSelected(selected === item.id ? undefined : item.id)}>
          <strong>{item.title}</strong><span>{calendarDayTime(item)}</span>
        </button>
      </Popover>
    </div>)}
  </div><Modal open={!desktop && !!active} title="日程详情" footer={null} onCancel={close} destroyOnHidden width="min(560px, calc(100vw - 24px))">{!desktop && details}</Modal></div>
}
