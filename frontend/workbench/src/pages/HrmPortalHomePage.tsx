import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Col, Empty, Row, Select, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmEmployee, type HrmHomeCalendarItem, type HrmLeaveItem, type HrmPerformanceAssessmentSummary } from '../services/api'
import { LEAVE_APPROVAL_STATUS_COLORS, LEAVE_APPROVAL_STATUS_LABELS, MONTH_OPTIONS, currentYearMonth } from '../services/hrm'
import dayjs from 'dayjs'

function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

function fmtDay(date: string | null | undefined) {
  const day = dayjs(date)
  return day.isValid() ? day.format('MM-DD') : '-'
}

/** 员工端个人工作台：个人概览 + 待办 + 当月日历。 */
export default function HrmPortalHomePage() {
  const initial = currentYearMonth()
  const [year, setYear] = useState(initial.year)
  const [month, setMonth] = useState(initial.month)

  const [employee, setEmployee] = useState<HrmEmployee>()
  const [unreadCount, setUnreadCount] = useState(0)
  const [clocks, setClocks] = useState<number>(0) // 本月打卡次数
  const [leaves, setLeaves] = useState<HrmLeaveItem[]>([])
  const [pendingReviews, setPendingReviews] = useState(0)
  const [calendarItems, setCalendarItems] = useState<HrmHomeCalendarItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const load = useCallback(async (targetYear: number, targetMonth: number) => {
    const current = ++version.current
    setLoading(true); setError('')
    const startDate = dayjs(new Date(targetYear, targetMonth - 1, 1)).format('YYYY-MM-DD')
    const endDate = dayjs(new Date(targetYear, targetMonth, 0)).format('YYYY-MM-DD')
    try {
      const [employeeResult, unread, clockList, leaveList, perfPage, calendar] = await Promise.all([
        api.hrm.portal.employee.getBindStatus().then((ok) => ok ? api.hrm.portal.employee.get() : Promise.resolve(undefined)),
        api.hrm.portal.salary.unreadSummary().catch(() => ({ unreadCount: 0, reminder: undefined })),
        api.hrm.portal.attendance.clockList({ year: targetYear, month: targetMonth }).catch(() => []),
        api.hrm.portal.attendance.leaveList().catch(() => []),
        api.hrm.portal.performance.page({ pageNo: 1, pageSize: 20 }).catch(() => ({ list: [] as HrmPerformanceAssessmentSummary[], total: 0 })),
        api.hrm.portal.home.calendar({ startDate, endDate }).catch(() => [])
      ])
      if (current !== version.current) return
      setEmployee(employeeResult)
      setUnreadCount(unread.unreadCount)
      setClocks(clockList.length)
      setLeaves(leaveList)
      setPendingReviews(perfPage.list.filter((item) => item.status === 1).length)
      setCalendarItems(calendar)
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '工作台加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load(year, month) }, [load, year, month])

  const reviewItems = <div className="hrm-todo-list">
    {leaves.length ? leaves.slice(0, 5).map(item => (
      <div key={item.id} className="hrm-todo-item">
        <span className="hrm-todo-label">请假</span>
        <span className="hrm-todo-main">{item.type} · {fmtDate(item.startTime)} 起</span>
        <Tag color={LEAVE_APPROVAL_STATUS_COLORS[item.approvalStatus ?? 0]}>{LEAVE_APPROVAL_STATUS_LABELS[item.approvalStatus ?? 0] || '-'}</Tag>
      </div>
    )) : <Empty description="暂无请假记录" imageStyle={{ height: 40 }}/>}
  </div>

  const myTimeItems = <div className="hrm-todo-list">
    {clocks > 0 && <div className="hrm-todo-item">
      <span className="hrm-todo-label">打卡</span>
      <span className="hrm-todo-main">本月已打卡 {clocks} 次</span>
    </div>}
    {pendingReviews > 0 && <div className="hrm-todo-item">
      <span className="hrm-todo-label">绩效</span>
      <span className="hrm-todo-main">有 {pendingReviews} 项考核待处理</span>
    </div>}
    {!clocks && !pendingReviews && <Empty description="暂无本月待办" imageStyle={{ height: 40 }}/>}
  </div>

  const calendarMap: Record<string, HrmHomeCalendarItem[]> = {}
  for (const item of calendarItems) {
    if (!calendarMap[item.date]) calendarMap[item.date] = []
    calendarMap[item.date].push(item)
  }

  const calendarTable = <Card size="small" className="hrm-calendar-card">
    <div className="hrm-calendar-head">{year} 年 {month} 月大事记</div>
    {calendarItems.length
      ? Object.entries(calendarMap).sort(([a], [b]) => a.localeCompare(b)).map(([date, items]) => (
        <div key={date} className="hrm-calendar-day">
          <span className="hrm-calendar-date">{fmtDay(date)}</span>
          <div className="hrm-calendar-items">
            {items.map((item, index) => <div key={index} className="hrm-calendar-item">
              <Tag>{item.typeName}</Tag><span>{item.content}</span>
            </div>)}
          </div>
        </div>
      ))
      : <Empty description="本月暂无考勤/生日/合同等事项" imageStyle={{ height: 48 }}/>}
  </Card>

  if (loading && !employee) return <section className="workspace-page hrm-page"><Skeleton active paragraph={{ rows: 10 }}/></section>

  return <section className="workspace-page hrm-page hrm-portal-home-page">
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(year, month)}>重试</Button>}/>}
    <div className="page-heading">
      <h2 className="hrm-profile-name">
        {employee ? `${employee.name || ''}，上午好` : '员工工作台'}
        {employee?.deptName && <span className="hrm-muted"> · {employee.deptName}</span>}
      </h2>
      <Space>
        <Select value={month} onChange={setMonth} options={MONTH_OPTIONS} style={{ width: 80 }}/>
        <Button icon={<ReloadOutlined/>} onClick={() => void load(year, month)}>刷新</Button>
      </Space>
    </div>

    {employee && <Row gutter={[16, 16]} className="hrm-dashboard">
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="我的时间">
          {myTimeItems}
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="我的申请">
          {reviewItems}
        </Card>
      </Col>
      <Col span={24}>{calendarTable}</Col>
    </Row>}
  </section>
}
