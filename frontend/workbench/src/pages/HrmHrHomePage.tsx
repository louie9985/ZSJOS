import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Col, Empty, Row, Skeleton, Space, Statistic, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmHrHomeStatistics, type HrmHomeCalendarItem } from '../services/api'
import { fmtAmount } from '../services/hrm'
import dayjs from 'dayjs'

function fmtDay(date: string | null | undefined) {
  const day = dayjs(date)
  return day.isValid() ? day.format('MM-DD') : '-'
}

/** HR 工作台：员工/招聘/薪资/待办概览 + 月度日历。仅 HR 管理员可见。 */
export default function HrmHrHomePage() {
  const [stats, setStats] = useState<HrmHrHomeStatistics>()
  const [calendarItems, setCalendarItems] = useState<HrmHomeCalendarItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    const start = dayjs().startOf('month').format('YYYY-MM-DD')
    const end = dayjs().endOf('month').format('YYYY-MM-DD')
    try {
      const [statResult, calendar] = await Promise.all([
        api.hrm.home.hrStatistics(),
        api.hrm.home.hrCalendar({ startDate: start, endDate: end }).catch(() => [])
      ])
      if (current !== version.current) return
      setStats(statResult); setCalendarItems(calendar)
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : '工作台加载失败') }
    finally { if (current === version.current) setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  const calendarMap: Record<string, HrmHomeCalendarItem[]> = {}
  for (const item of calendarItems) {
    if (!calendarMap[item.date]) calendarMap[item.date] = []
    calendarMap[item.date].push(item)
  }

  if (loading && !stats) return <section className="workspace-page hrm-page"><Skeleton active paragraph={{ rows: 10 }}/></section>

  return <section className="workspace-page hrm-page hrm-hr-home-page">
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    <div className="page-heading">
      <h2 className="hrm-profile-name">HR 工作台</h2>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {stats && <Row gutter={[16, 16]} className="hrm-dashboard">
      <Col span={24}>
        <Card size="small" className="hrm-widget" title="员工概览">
          <Row gutter={16}>
            <Col span={4}><Statistic title="在职员工" value={stats.employeeSurvey.activeCount}/></Col>
            <Col span={4}><Statistic title="本月入职" value={stats.employeeSurvey.entryThisMonthCount}/></Col>
            <Col span={4}><Statistic title="本月待入职" value={stats.employeeSurvey.pendingEntryThisMonthCount}/></Col>
            <Col span={4}><Statistic title="本月离职" value={stats.employeeSurvey.leaveThisMonthCount}/></Col>
            <Col span={4}><Statistic title="本月待离职" value={stats.employeeSurvey.pendingLeaveThisMonthCount}/></Col>
            <Col span={4}><Statistic title="本月转正/调岗" value={stats.employeeSurvey.regularThisMonthCount + stats.employeeSurvey.transferThisMonthCount}/></Col>
          </Row>
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="招聘概览">
          <Row gutter={16}>
            <Col span={6}><Statistic title="招聘中职位" value={stats.recruitSurvey.recruitingPostCount}/></Col>
            <Col span={6}><Statistic title="流程中候选人" value={stats.recruitSurvey.candidateInProcessCount}/></Col>
            <Col span={6}><Statistic title="待入职" value={stats.recruitSurvey.pendingEntryCount}/></Col>
            <Col span={6}><Statistic title="已入职" value={stats.recruitSurvey.joinedCount}/></Col>
          </Row>
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="薪资概览">
          <Row gutter={16}>
            <Col span={12}><Statistic title="计薪员工" value={stats.salarySurvey.employeeCount}/></Col>
            <Col span={12}><Statistic title="实发工资合计" value={stats.salarySurvey.realPaySalary} precision={2} prefix="¥"/></Col>
          </Row>
        </Card>
      </Col>
      <Col span={24}>
        <Card size="small" className="hrm-widget" title="待办">
          <Row gutter={16}>
            <Col span={4}><Statistic title="待入职" value={stats.todoSurvey.toEntryCount}/></Col>
            <Col span={4}><Statistic title="待离职" value={stats.todoSurvey.toLeaveCount}/></Col>
            <Col span={4}><Statistic title="合同待到期" value={stats.todoSurvey.toExpireContractCount}/></Col>
            <Col span={4}><Statistic title="待转正" value={stats.todoSurvey.toRegularCount}/></Col>
            <Col span={4}><Statistic title="工资表待核算" value={stats.todoSurvey.toSalaryComputeCount}/></Col>
            <Col span={4}><Statistic title="本月生日" value={stats.todoSurvey.toBirthdayCount}/></Col>
          </Row>
        </Card>
      </Col>
      <Col span={24}>
        <Card size="small" className="hrm-widget" title="本月大事记">
          {calendarItems.length
            ? Object.entries(calendarMap).sort(([a], [b]) => a.localeCompare(b)).map(([date, items]) => (
              <div key={date} className="hrm-calendar-day">
                <span className="hrm-calendar-date">{fmtDay(date)}</span>
                <div className="hrm-calendar-items">
                  {items.map((item, index) => <span key={index} className="hrm-calendar-item-text">{item.typeName}：{item.content}</span>)}
                </div>
              </div>
            ))
            : <Empty description="本月暂无事项" imageStyle={{ height: 40 }}/>}
        </Card>
      </Col>
    </Row>}
  </section>
}
