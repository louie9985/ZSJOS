import { Button, DatePicker, Segmented, Space } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import { monday, moveCalendarView, todayWallTime, type CalendarView } from './calendarViewLayout'
import '../styles/components/calendar-time-grid.css'
import '../styles/components/calendar-side-navigation.css'
export default function CalendarViewToolbar({ view, date, onView, onDate }: {
  view: CalendarView; date: Dayjs; onView: (view: CalendarView) => void; onDate: (date: Dayjs) => void
}) {
  const unit = view === 'month' ? '月' : view === 'week' ? '周' : '日'
  const title = view === 'week' ? monday(date).format('YYYY年M月D日') + ' — ' + monday(date).add(6, 'day').format('M月D日') : date.format(view === 'day' ? 'YYYY年M月D日' : 'YYYY年M月')
  return <div className="calendar-view-toolbar">
    <Space><Button aria-label={'上一' + unit} icon={<LeftOutlined />} onClick={() => onDate(moveCalendarView(date,view,-1))} />
      <strong>{title}</strong><Button aria-label={'下一' + unit} icon={<RightOutlined />} onClick={() => onDate(moveCalendarView(date,view,1))} /></Space>
    <Space wrap><DatePicker aria-label="跳转日期" value={date} allowClear={false} onChange={value => { if (value) onDate(value) }} />
      <Button aria-label="今天" onClick={() => onDate(todayWallTime())}>今天</Button>
      <Segmented aria-label="日历视图" value={view} options={[{label:'日',value:'day'},{label:'周',value:'week'},{label:'月',value:'month'}]} onChange={onView} />
    </Space>
  </div>
}
