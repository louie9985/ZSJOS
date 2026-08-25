import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Col, Empty, Progress, Row, Skeleton, Statistic, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmTeamHomeStatistics, type HrmHomeCalendarItem } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, SEX_LABELS, EMPLOYEE_STATUS_LABELS } from '../services/hrm'
import dayjs from 'dayjs'

function fmtDay(date: string | null | undefined) {
  const day = dayjs(date)
  return day.isValid() ? day.format('MM-DD') : '-'
}

/** 团队工作台：团队人数/流动概况 + 成员结构占比 + 日历。团队负责人可见。 */
export default function HrmTeamHomePage() {
  const [stats, setStats] = useState<HrmTeamHomeStatistics>()
  const [calendarItems, setCalendarItems] = useState<HrmHomeCalendarItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const employeeStatus = useDict(HRM_DICT.EMPLOYEE_STATUS)

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    const start = dayjs().startOf('month').format('YYYY-MM-DD')
    const end = dayjs().endOf('month').format('YYYY-MM-DD')
    try {
      const [statResult, calendar] = await Promise.all([
        api.hrm.home.teamStatistics(),
        api.hrm.home.teamCalendar({ startDate: start, endDate: end }).catch(() => [])
      ])
      if (current !== version.current) return
      setStats(statResult); setCalendarItems(calendar)
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : '团队工作台加载失败') }
    finally { if (current === version.current) setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  const analysisLabel = (key: 'statusAnalysis' | 'sexAnalysis' | 'ageAnalysis' | 'companyAgeAnalysis', type: number | null) => {
    // 占比项无独立字典时，用已加载的字典尽力映射
    if (key === 'sexAnalysis') return type != null ? (SEX_LABELS[type] || String(type)) : '未填写'
    if (key === 'statusAnalysis') return type != null ? (EMPLOYEE_STATUS_LABELS[type] || String(type)) : '未填写'
    if (type == null) return '未填写'
    return ['', '1年以内', '1-3年', '3-5年', '5-10年', '10年以上'][type] || String(type)
  }

  const renderAnalysis = (key: 'statusAnalysis' | 'sexAnalysis' | 'ageAnalysis' | 'companyAgeAnalysis') => {
    const items = stats?.teamSurvey[key] || []
    const total = items.reduce((sum, item) => sum + item.count, 0) || 1
    if (!items.length) return <Empty description="暂无数据" imageStyle={{ height: 40 }}/>
    return items.map((item, index) => {
      const percent = Math.round((item.count / total) * 100)
      return <div key={index} className="hrm-analysis-row">
        <span className="hrm-analysis-label">{analysisLabel(key, item.type)}</span>
        <Progress percent={percent} size="small" style={{ flex: 1 }}/>
        <span className="hrm-analysis-count">{item.count} 人</span>
      </div>
    })
  }

  const calendarMap: Record<string, HrmHomeCalendarItem[]> = {}
  for (const item of calendarItems) {
    if (!calendarMap[item.date]) calendarMap[item.date] = []
    calendarMap[item.date].push(item)
  }

  if (loading && !stats) return <section className="workspace-page hrm-page"><Skeleton active paragraph={{ rows: 10 }}/></section>

  return <section className="workspace-page hrm-page hrm-team-home-page">
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    <div className="page-heading">
      <h2 className="hrm-profile-name">团队工作台</h2>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {stats && <Row gutter={[16, 16]} className="hrm-dashboard">
      <Col span={24}>
        <Card size="small" className="hrm-widget" title="我的团队">
          <Row gutter={16}>
            <Col span={6}><Statistic title="团队人数" value={stats.teamOverview.employeeCount}/></Col>
            <Col span={6}><Statistic title="本月入职" value={stats.teamOverview.entryThisMonthCount}/></Col>
            <Col span={6}><Statistic title="本月离职" value={stats.teamOverview.leaveThisMonthCount}/></Col>
            <Col span={6}><Statistic title="本月转正" value={stats.teamOverview.regularThisMonthCount}/></Col>
          </Row>
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="成员状态占比">
          {renderAnalysis('statusAnalysis')}
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="性别占比">
          {renderAnalysis('sexAnalysis')}
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="年龄占比">
          {renderAnalysis('ageAnalysis')}
        </Card>
      </Col>
      <Col span={12}>
        <Card size="small" className="hrm-widget" title="司龄占比">
          {renderAnalysis('companyAgeAnalysis')}
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
