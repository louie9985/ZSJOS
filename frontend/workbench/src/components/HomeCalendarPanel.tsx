import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Calendar, Space, Spin, Typography } from 'antd'
import { CalendarOutlined, EditOutlined, LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import zhCN from 'antd/es/calendar/locale/zh_CN'
import type { Dayjs } from 'dayjs'
import { ApiError } from '../services/api'
import { calendarRestDays, restDayRange, REST_DAY_MANAGE, type RestDay } from '../services/calendarRestDays'
import { todayWallTime } from './calendarViewLayout'
import RestDayManager from './RestDayManager'
import '../styles/pages/home-rest-days.css'

export const homeCalendarLocale = { ...zhCN, lang: { ...zhCN.lang,
  shortMonths: ['1月','2月','3月','4月','5月','6月','7月','8月','9月','10月','11月','12月'],
  shortWeekDays: ['日','一','二','三','四','五','六'] } }

export function RestDate({ date, rest, selected = false }: { date: Dayjs; rest: boolean; selected?: boolean }) {
  const today = date.isSame(todayWallTime(), 'day')
  return <span className={['home-rest-date', rest && 'is-rest', today && 'is-today', selected && 'is-selected'].filter(Boolean).join(' ')}
    title={rest ? '休息日' : undefined} aria-label={date.format('YYYY-MM-DD') + (rest ? ' 休息日' : '') + (today ? ' 今天' : '')}>
    {date.date()}
  </span>
}
export function RestMonthToolbar({ month, onChange, disabled = false }: { month: Dayjs; onChange: (month: Dayjs) => void; disabled?: boolean }) {
  return <div className="home-rest-toolbar">
    <Button size="small" disabled={disabled} aria-label="上个月" icon={<LeftOutlined />} onClick={() => onChange(month.startOf('month').subtract(1, 'month'))} />
    <strong>{month.format('YYYY年M月')}</strong>
    <Button size="small" disabled={disabled} aria-label="下个月" icon={<RightOutlined />} onClick={() => onChange(month.startOf('month').add(1, 'month'))} />
    <Button size="small" disabled={disabled} onClick={() => onChange(todayWallTime())}>今天</Button>
  </div>
}
export function restDayError(error: unknown) {
  return error instanceof ApiError && error.code === 403 ? '无权访问休息日，请联系管理员' :
    error instanceof Error ? error.message : '请求失败，请重试'
}
export default function HomeCalendarPanel({ permissions }: { permissions: readonly string[] }) {
  const [month, setMonth] = useState(todayWallTime)
  const [rows, setRows] = useState<RestDay[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [editing, setEditing] = useState(false)
  const canRead = permissions.includes('zsjos:business-task:query')
  const canManage = permissions.includes(REST_DAY_MANAGE)
  const { startDate, endDate } = restDayRange(month)
  const requestId = useRef(0)
  const refresh = useCallback(() => setRevision(v => v + 1), [])
  useEffect(() => {
    const id = ++requestId.current
    const controller = new AbortController()
    setRows([]); setError(''); setLoading(true)
    if (!canRead) { setError('无权访问休息日，请联系管理员'); setLoading(false); return }
    calendarRestDays.list(startDate, endDate, controller.signal).then(value => {
      if (id === requestId.current && !controller.signal.aborted) setRows(value)
    }).catch(cause => {
      if (id === requestId.current && !controller.signal.aborted) setError('休息日加载失败：' + restDayError(cause))
    }).finally(() => {
      if (id === requestId.current && !controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [startDate, endDate, revision, canRead])
  const rest = new Set(rows.filter(row => row.rest).map(row => row.date))
  return <section className="home-panel home-calendar-panel" aria-label="日历">
    <header className="home-panel-header compact">
      <div className="home-calendar-heading">
        <Typography.Title level={4}><CalendarOutlined /> 日历</Typography.Title>
        <span className="home-rest-legend"><i aria-hidden="true" /> 休息日</span>
      </div>
      <Space className="home-calendar-actions" size={0} wrap>
        {canRead && canManage && <Button className="home-rest-manage" size="small" type="link" icon={<EditOutlined />} onClick={() => setEditing(true)}>管理休息日</Button>}
        <Button type="text" icon={<ReloadOutlined />} aria-label="刷新休息日" onClick={refresh} />
      </Space>
    </header>
    <RestMonthToolbar month={month} onChange={setMonth} />
    {error && <Alert type="error" showIcon title={error} action={<Button size="small" onClick={refresh}>重试</Button>} />}
    <Spin wrapperClassName="home-calendar-body" spinning={loading}>
      <Calendar fullscreen={false} locale={homeCalendarLocale} value={month} headerRender={() => null}
        onSelect={(date, info) => { if (info.source === 'date') setMonth(date) }}
        fullCellRender={(date, info) => info.type === 'date' ? <RestDate date={date} rest={rest.has(date.format('YYYY-MM-DD'))} /> : info.originNode} />
    </Spin>
    {editing && canRead && canManage && <RestDayManager initialMonth={month} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); refresh() }} />}
  </section>
}
