import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { Button, Tooltip } from 'antd'
import type { Dayjs } from 'dayjs'
import type { ReactNode } from 'react'
import '../styles/components/calendar-side-navigation.css'

// Month navigation is anchored to the first day, so January 31 -> February -> March cannot drift.
export const moveCalendarMonth = (value: Dayjs, direction: -1 | 1) => value.startOf('month').add(direction, 'month')

type Props = {
  children: ReactNode
  onNavigate: (direction: -1 | 1) => void
  previousLabel?: string
  nextLabel?: string
}

export default function CalendarSideNavigation({ children, onNavigate, previousLabel = '上一月', nextLabel = '下一月' }: Props) {
  return <div className="calendar-side-navigation">
    <div className="calendar-side-navigation-rail">
      <Tooltip title={previousLabel}><Button className="calendar-side-navigation-arrow" aria-label={previousLabel}
        icon={<LeftOutlined />} onClick={() => onNavigate(-1)} /></Tooltip>
    </div>
    <div className="calendar-side-navigation-content">{children}</div>
    <div className="calendar-side-navigation-rail">
      <Tooltip title={nextLabel}><Button className="calendar-side-navigation-arrow" aria-label={nextLabel}
        icon={<RightOutlined />} onClick={() => onNavigate(1)} /></Tooltip>
    </div>
    <span className="calendar-mobile-hint">左右滑动查看完整日历</span>
  </div>
}
