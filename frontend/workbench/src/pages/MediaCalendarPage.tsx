import { CalendarOutlined, LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Input, Pagination, Segmented, Select, Skeleton, Space, Tooltip, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { DICT_TYPE } from '../constants'
import { ApiError, api, type DictData, type MediaAccountCalendarItem, type SimpleUser } from '../services/api'

type ViewMode = 'week' | 'month' | 'quarter' | 'year'
const DAY_WIDTH = 32

export const mediaCalendarWindow = (anchor: Dayjs, mode: ViewMode) => {
  if (mode === 'week') {
    const start = anchor.startOf('day').subtract((anchor.day() + 6) % 7, 'day')
    return { start, end: start.add(6, 'day') }
  }
  if (mode === 'month') return { start: anchor.startOf('month'), end: anchor.endOf('month') }
  if (mode === 'quarter') {
    const start = anchor.month(Math.floor(anchor.month() / 3) * 3).startOf('month')
    return { start, end: start.add(2, 'month').endOf('month') }
  }
  return { start: anchor.startOf('year'), end: anchor.endOf('year') }
}

const moveAnchor = (anchor: Dayjs, mode: ViewMode, direction: number) => mode === 'quarter'
  ? anchor.add(direction * 3, 'month') : anchor.add(direction, mode === 'week' ? 'week' : mode === 'month' ? 'month' : 'year')

export const mediaCalendarTone = (value?: string, colorType?: string) => {
  if (colorType && ['success', 'primary', 'warning', 'info'].includes(colorType)) return colorType
  return value?.startsWith('a_') ? 'success' : value?.startsWith('b_') ? 'primary'
    : value?.startsWith('c_') ? 'warning' : value?.startsWith('d_') ? 'info' : 'neutral'
}

export default function MediaCalendarPage() {
  const [mode, setMode] = useState<ViewMode>('month')
  const [anchor, setAnchor] = useState(dayjs())
  const [rows, setRows] = useState<MediaAccountCalendarItem[]>([])
  const [total, setTotal] = useState(0), [unscheduled, setUnscheduled] = useState(0), [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [keywordInput, setKeywordInput] = useState(''), [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string>(), [stage, setStage] = useState<string>()
  const [director, setDirector] = useState<number>(), [operator, setOperator] = useState<number>()
  const [statuses, setStatuses] = useState<DictData[]>([]), [stages, setStages] = useState<DictData[]>([])
  const [users, setUsers] = useState<SimpleUser[]>([])
  const range = useMemo(() => mediaCalendarWindow(anchor, mode), [anchor, mode])
  const days = useMemo(() => Array.from({ length: range.end.diff(range.start, 'day') + 1 }, (_, index) => range.start.add(index, 'day')), [range])

  const loadReference = useCallback(async () => {
    const results = await Promise.allSettled([
      api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_CURRENT_STATUS),
      api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_STAGE),
      api.simpleUsers()
    ])
    if (results[0].status === 'fulfilled') setStatuses(results[0].value)
    if (results[1].status === 'fulfilled') setStages(results[1].value)
    if (results[2].status === 'fulfilled') setUsers(results[2].value.filter(user => user.status === undefined || user.status === 0))
  }, [])

  const load = useCallback(async (targetPage = 1) => {
    setLoading(true); setError('')
    try {
      const result = await api.mediaAccount.calendar({
        pageNo: targetPage, pageSize: 50, rangeStart: range.start.format('YYYY-MM-DD'), rangeEnd: range.end.format('YYYY-MM-DD'),
        keyword: keyword || undefined, currentStatusValue: status, stageValue: stage,
        directorUserId: director, operatorUserId: operator
      })
      setRows(result.list); setTotal(result.total); setUnscheduled(result.unscheduledCount); setPage(targetPage)
    } catch (cause) {
      setRows([]); setTotal(0); setUnscheduled(0)
      setError(cause instanceof ApiError && cause.code === 403 ? '无权查看账号日历' : cause instanceof Error ? cause.message : '日历加载失败')
    } finally { setLoading(false) }
  }, [director, keyword, operator, range.end, range.start, stage, status])

  useEffect(() => { void loadReference() }, [loadReference])
  useEffect(() => { void load(1) }, [load])

  const gridStyle = { gridTemplateColumns: `repeat(${days.length}, ${DAY_WIDTH}px)` }
  const todayIndex = dayjs().startOf('day').diff(range.start, 'day')
  const statusColors = useMemo(() => new Map(statuses.map(item => [item.value, item.colorType])), [statuses])

  return <section className="workspace-page media-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}><CalendarOutlined /> 账号日历</Typography.Title><Typography.Text type="secondary">{range.start.format('YYYY年M月D日')} 至 {range.end.format('YYYY年M月D日')} · 未排期 {unscheduled}</Typography.Text></div><Space><Button icon={<ReloadOutlined />} onClick={() => void load(page)} /><Segmented value={mode} onChange={value => { setMode(value as ViewMode); setPage(1) }} options={[{ value: 'week', label: '周' }, { value: 'month', label: '月' }, { value: 'quarter', label: '季' }, { value: 'year', label: '年' }]} /></Space></div>
    <div className="media-calendar-toolbar">
      <Space.Compact><Button icon={<LeftOutlined />} aria-label="上一周期" onClick={() => setAnchor(value => moveAnchor(value, mode, -1))} /><Button onClick={() => setAnchor(dayjs())}>今天</Button><Button icon={<RightOutlined />} aria-label="下一周期" onClick={() => setAnchor(value => moveAnchor(value, mode, 1))} /></Space.Compact>
      <Input.Search allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索账号编号或昵称" />
      <Select allowClear value={status} onChange={setStatus} placeholder="当下状态" options={statuses.map(item => ({ value: item.value, label: item.label }))} />
      <Select allowClear value={stage} onChange={setStage} placeholder="阶段" options={stages.map(item => ({ value: item.value, label: item.label }))} />
      <Select allowClear showSearch optionFilterProp="label" value={director} onChange={setDirector} placeholder="编导" options={users.map(user => ({ value: user.id, label: user.nickname }))} />
      <Select allowClear showSearch optionFilterProp="label" value={operator} onChange={setOperator} placeholder="运营" options={users.map(user => ({ value: user.id, label: user.nickname }))} />
    </div>
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load(page)}>重试</Button>} /> : loading && !rows.length ? <Skeleton active paragraph={{ rows: 10 }} /> : !rows.length ? <Empty description="当前范围内没有已排期账号" /> : <div className="media-calendar-shell">
      <div className="media-calendar-info-column"><div className="media-calendar-info-head">账号 / 学员</div>{rows.map(row => <div className="media-calendar-info" key={row.id}><strong title={row.nickname || row.accountNo}>{row.nickname || row.accountNo}</strong><span>{row.studentName || '未绑定学员'} · {row.platformLabelSnapshot || '平台未记录'}</span></div>)}</div>
      <div className="media-calendar-scroll"><div className="media-calendar-grid media-calendar-header" style={gridStyle}>{days.map(day => <div className={day.day() === 0 || day.day() === 6 ? 'weekend' : ''} key={day.format('YYYY-MM-DD')}><strong>{day.date()}</strong><span>{['日', '一', '二', '三', '四', '五', '六'][day.day()]}</span></div>)}</div>
        <div className="media-calendar-rows">{rows.map(row => {
          const clippedStart = dayjs(row.startDate).isBefore(range.start, 'day') ? range.start : dayjs(row.startDate)
          const clippedEnd = dayjs(row.endDate).isAfter(range.end, 'day') ? range.end : dayjs(row.endDate)
          const start = clippedStart.diff(range.start, 'day')
          const length = clippedEnd.diff(clippedStart, 'day') + 1
          return <div className="media-calendar-row" key={row.id}><div className="media-calendar-grid media-calendar-cells" style={gridStyle}>{days.map(day => <div className={day.day() === 0 || day.day() === 6 ? 'weekend' : ''} key={day.format('YYYY-MM-DD')} />)}</div>
            <Tooltip title={<div><strong>{row.nickname || row.accountNo}</strong><div>{row.studentName || '未绑定学员'} · {row.platformLabelSnapshot || '平台未记录'}</div><div>{row.currentStatusLabelSnapshot || '状态未填写'} · {row.stageLabelSnapshot || '阶段未填写'}</div><div>编导：{row.directorUserName || '未分配'} · 运营：{row.operatorUserName || '未分配'}</div><div>{row.startDate} 至 {row.endDate}</div></div>}><div className={`media-calendar-bar tone-${mediaCalendarTone(row.currentStatusValue, statusColors.get(row.currentStatusValue || ''))}`} style={{ left: start * DAY_WIDTH + 2, width: Math.max(length * DAY_WIDTH - 4, 12) }}><span>{dayjs(row.startDate).isBefore(range.start, 'day') && '‹ '}{row.nickname || row.accountNo}{dayjs(row.endDate).isAfter(range.end, 'day') && ' ›'}</span></div></Tooltip>
          </div>
        })}{todayIndex >= 0 && todayIndex < days.length && <div className="media-calendar-today" style={{ left: todayIndex * DAY_WIDTH + DAY_WIDTH / 2 }} />}</div>
      </div>
    </div>}
    {total > 50 && <Pagination current={page} pageSize={50} total={total} showSizeChanger={false} onChange={value => void load(value)} />}
  </section>
}
