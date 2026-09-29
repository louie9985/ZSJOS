import { Button, Tooltip } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import type { ReactNode } from 'react'
import type { ExamSchedule } from '../services/api'
import { calendarWindow, coversExamDay, layoutMultiDayWeek } from './examCalendarLayout'

type Props = {
  anchor: Dayjs
  exactRows: ExamSchedule[]
  multiDayRows: ExamSchedule[]
  onDay: (date: Dayjs) => void
  onDetail: (schedule: ExamSchedule) => void
  renderStatus: (status: string) => ReactNode
}

export default function ExamCalendarMonth({ anchor, exactRows, multiDayRows, onDay, onDetail, renderStatus }: Props) {
  const { start } = calendarWindow(anchor)
  const today = dayjs().format('YYYY-MM-DD')
  const weekday = new Intl.DateTimeFormat('zh-CN', { weekday: 'short' })
  return <div className="exam-month" aria-label={anchor.format('YYYY年M月') + '考期月历'}>
    <div className="exam-month-weekdays" aria-hidden="true">{Array.from({ length: 7 }, (_, i) =>
      <span key={i}>{weekday.format(start.add(i, 'day').toDate())}</span>)}</div>
    {Array.from({ length: 6 }, (_, week) => {
      const weekStart = start.add(week * 7, 'day')
      const { segments, hiddenCounts, laneCount } = layoutMultiDayWeek(multiDayRows, weekStart)
      return <div key={week} className="exam-month-week" style={{ gridTemplateRows: '36px ' + (laneCount ? 'repeat(' + laneCount + ', 30px) ' : '') + 'minmax(74px, auto)' }}>
        {Array.from({ length: 7 }, (_, column) => {
          const date = weekStart.add(column, 'day'), key = date.format('YYYY-MM-DD')
          const rows = exactRows.filter(row => coversExamDay(row, key))
          return <div key={key} className={'exam-month-day' + (date.isSame(anchor, 'month') ? '' : ' is-adjacent') + (key === today ? ' is-today' : '')}
            data-date={key} style={{ gridColumn: column + 1, gridRow: '1 / ' + (laneCount + 3) }}>
            <button type="button" className="exam-month-date" aria-label={date.format('YYYY年M月D日') + '考期安排'}
              aria-current={key === today ? 'date' : undefined} onClick={() => onDay(date)}>{date.date()}</button>
            <div className="exam-calendar-events" style={{ marginTop: laneCount * 30 }}>
              {rows.slice(0, 3).map(item => <button type="button" key={item.id}
                className={'exam-calendar-event tone-' + item.displayStatus.toLowerCase()}
                title={(item.scheduleName || '未命名考期') + ' · ' + item.exactDate} onClick={() => onDetail(item)}>
                <span>{item.scheduleName || '未命名考期'}</span>{renderStatus(item.displayStatus)}
              </button>)}
              {rows.length > 3 && <Button type="link" size="small" className="exam-calendar-overflow" onClick={() => onDay(date)}>另有 {rows.length - 3} 条</Button>}
              {hiddenCounts[column] > 0 && <Button type="link" size="small" className="exam-multiDay-overflow"
                aria-label={date.format('M月D日') + '更多 ' + hiddenCounts[column] + ' 项多日考期'} onClick={() => onDay(date)}>多日 +{hiddenCounts[column]}</Button>}
            </div>
          </div>
        })}
        {segments.map(({ schedule, startColumn, endColumn, lane, continuesBefore, continuesAfter }) => {
          const label = '多日 · ' + (schedule.scheduleName || '未命名考期') + ' · ' + schedule.startDate + ' 至 ' + schedule.endDate + ''
          return <Tooltip key={schedule.id} title={label}>
            <button type="button" className={'exam-multiDay-bar status-' + schedule.displayStatus.toLowerCase()}
              data-schedule-id={schedule.id} aria-label={label}
              style={{ gridColumn: (startColumn + 1) + ' / ' + (endColumn + 2), gridRow: lane + 2 }}
              onClick={() => onDetail(schedule)}>
              {continuesBefore && <span aria-hidden="true">‹</span>}
              <span className="exam-multiDay-bar-label">多日 · {schedule.scheduleName || '未命名考期'}</span>
              {continuesAfter && <span aria-hidden="true">›</span>}
            </button>
          </Tooltip>
        })}
      </div>
    })}
  </div>
}
