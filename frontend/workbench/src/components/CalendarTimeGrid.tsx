import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import type { Dayjs } from 'dayjs'
import { examBackgroundStyle } from '../pages/examCalendarColor'
import { layoutGridDay, monday, todayWallTime, touchesGridDay, type TimeGridEvent } from './calendarViewLayout'
import '../styles/components/calendar-time-grid.css'
export default function CalendarTimeGrid({ view, date, events, onOpen, onCreate }: {
  view: 'day' | 'week'; date: Dayjs; events: TimeGridEvent[];
  onOpen: (id: number, date: Dayjs) => void; onCreate?: (date: Dayjs) => void
}) {
  const start = view === 'week' ? monday(date) : date.startOf('day')
  const key = start.format('YYYY-MM-DD')
  const days = useMemo(() => Array.from({length: view === 'week' ? 7 : 1}, (_, i) => start.add(i,'day')), [key, view])
  const [now, setNow] = useState(todayWallTime)
  const scroller = useRef<HTMLDivElement>(null)
  useEffect(() => { const timer = window.setInterval(() => setNow(todayWallTime()), 60000); return () => clearInterval(timer) }, [])
  useEffect(() => {
    if (scroller.current) scroller.current.scrollTop = 54 + 8 * 60
  }, [key,view])
  return <div ref={scroller} className={'calendar-time-scroll calendar-time-' + view} aria-label={view === 'week' ? '周时间表' : '日时间表'}>
    <div className="calendar-time-grid" style={{'--calendar-days':days.length} as CSSProperties}>
      <div className="calendar-time-heading calendar-time-corner">时间</div>
      {days.map(day => <div className="calendar-time-heading" data-today={day.isSame(now,'day')} key={day.format('YYYY-MM-DD')}>
        {day.format('ddd')}<strong>{day.format('M月D日')}</strong></div>)}
      <div className="calendar-all-day-label">全天</div>
      {days.map(day => <div className="calendar-all-day" key={day.format('YYYY-MM-DD')}>
        {events.filter(e => e.allDay && touchesGridDay(e,day)).map(e => <button type="button" key={e.id} style={examBackgroundStyle(e.color)}
          onClick={() => onOpen(e.id,day)}>{e.title}</button>)}
      </div>)}
      <div className="calendar-time-hours">{Array.from({length:24},(_,h) => <div key={h}>{String(h).padStart(2,'0')}:00</div>)}</div>
      {days.map(day => <div className="calendar-time-column" key={day.format('YYYY-MM-DD')} data-date={day.format('YYYY-MM-DD')}>
        {Array.from({length:48},(_,slot) => <button key={slot} type="button" className="calendar-time-slot" disabled={!onCreate}
          aria-label={day.format('M月D日') + ' ' + day.add(slot * 30,'minute').format('HH:mm') + ' 新建'}
          onClick={() => onCreate?.(day.add(slot * 30,'minute'))} style={{top:slot*30}} />)}
        {layoutGridDay(events,day).map(p => <button type="button" key={p.event.id} className="calendar-time-event"
          title={p.event.title + ' ' + p.event.start.format('MM-DD HH:mm') + ' — ' + p.event.end.format('MM-DD HH:mm')}
          aria-label={p.event.title + ' ' + p.event.start.format('HH:mm') + '至' + p.event.end.format('HH:mm')}
          style={{...examBackgroundStyle(p.event.color),top:p.start,height:p.end-p.start,left:p.column/p.columns*100+'%',width:100/p.columns+'%'}}
          onClick={() => onOpen(p.event.id,day)}><strong>{p.event.title}</strong><span>{p.event.start.format('HH:mm')} — {p.event.end.format('HH:mm')}</span></button>)}
        {day.isSame(now,'day') && <div className="calendar-now-line" aria-label="当前时间" style={{top:now.hour()*60+now.minute()}} />}
      </div>)}
    </div>
  </div>
}
