import dayjs, { type Dayjs } from 'dayjs'
import type { ExamSchedule } from '../services/api'
import { calendarWindow, coversExamDay, layoutMultiDayWeek } from './examCalendarLayout'

type Props = {
  anchor: Dayjs
  exactRows: ExamSchedule[]
  multiDayRows: ExamSchedule[]
  onDay: (date: Dayjs) => void
  statusLabel: (status: string) => string
}

export default function ExamCalendarMonth({ anchor, exactRows, multiDayRows, onDay, statusLabel }: Props) {
  const { start } = calendarWindow(anchor)
  const today = dayjs().format('YYYY-MM-DD')
  const weekday = new Intl.DateTimeFormat('zh-CN', { weekday: 'short' })
  return <div className="exam-month" aria-label={anchor.format('YYYY年M月') + '考期月历'}>
    <div className="exam-month-weekdays" aria-hidden="true">{Array.from({ length: 7 }, (_, i) =>
      <span key={i}>{weekday.format(start.add(i, 'day').toDate())}</span>)}</div>
    {Array.from({ length: 6 }, (_, week) => {
      const weekStart = start.add(week * 7, 'day')
      const { segments, laneCount } = layoutMultiDayWeek(multiDayRows, weekStart)
      return <div key={week} className="exam-month-week" style={{ gridTemplateRows: '36px ' + (laneCount ? 'repeat(' + laneCount + ', minmax(30px, auto)) ' : '') + 'minmax(74px, auto)' }}>
        {Array.from({ length: 7 }, (_, column) => {
          const date = weekStart.add(column, 'day'), key = date.format('YYYY-MM-DD')
          const rows = exactRows.filter(row => coversExamDay(row, key))
          return <div key={key} className={'exam-month-day' + (date.isSame(anchor, 'month') ? '' : ' is-adjacent') + (key === today ? ' is-today' : '')}
            data-date={key} onClick={() => onDay(date)} style={{ gridColumn: column + 1, gridRow: '1 / ' + (laneCount + 3) }}>
            <button type="button" className="exam-month-date" aria-label={date.format('YYYY年M月D日') + '考期安排'}
              aria-current={key === today ? 'date' : undefined} onClick={event => { event.stopPropagation(); onDay(date) }}>{date.date()}</button>
            <div className="exam-calendar-events" style={{ gridRow: laneCount + 2 }}>
              {rows.map(item => <button type="button" key={item.id}
                className={'exam-calendar-event exam-status-tone tone-' + item.displayStatus.toLowerCase()}
                aria-label={(item.scheduleName || '未命名考期') + '，' + statusLabel(item.displayStatus) + '，查看' + date.format('M月D日') + '全部考期'}
                onClick={event => { event.stopPropagation(); onDay(date) }}>
                <span>{item.scheduleName || '未命名考期'}</span>
              </button>)}

            </div>
          </div>
        })}
        {segments.map(({ schedule, startColumn, endColumn, lane, continuesBefore, continuesAfter }) => {
          return (
            <div key={schedule.id} className={'exam-multiDay-bar exam-status-tone tone-' + schedule.displayStatus.toLowerCase()}
              data-schedule-id={schedule.id}
              style={{ gridColumn: (startColumn + 1) + ' / ' + (endColumn + 2), gridRow: lane + 2 }}>
              {continuesBefore && <span aria-hidden="true">‹</span>}
              <span className="exam-multiDay-bar-label">{schedule.scheduleName || '未命名考期'}</span>
              {continuesAfter && <span aria-hidden="true">›</span>}
              <div className="exam-multiDay-days" style={{ gridTemplateColumns: `repeat(${endColumn - startColumn + 1}, minmax(0, 1fr))` }}>
                {Array.from({ length: endColumn - startColumn + 1 }, (_, offset) => {
                  const date = weekStart.add(startColumn + offset, 'day')
                  return <button type="button" key={offset} data-date={date.format('YYYY-MM-DD')}
                    aria-label={`${date.format('YYYY年M月D日')} 全部考期安排（${schedule.scheduleName || '未命名考期'}）`}
                    aria-description={'多日考试，' + statusLabel(schedule.displayStatus)}
                    onClick={event => { event.stopPropagation(); onDay(date) }} />
                })}
              </div>
            </div>
          )
        })}
      </div>
    })}
  </div>
}
