import { CalendarOutlined, LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Input, Segmented, Select, Skeleton, Space, Tooltip, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { DICT_TYPE } from '../constants'
import { ApiError, api, type DictData, type MediaAccountCalendarItem, type SimpleUser } from '../services/api'

type ViewMode = 'week' | 'month' | 'quarter' | 'year'
type CalendarScope = 'account' | 'all'
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

type ScheduleCell = {
  date: Dayjs
  inMonth: boolean
  entries: MediaAccountCalendarItem[]
}

const buildScheduleCells = (anchor: Dayjs, rows: MediaAccountCalendarItem[]) => {
  const monthStart = anchor.startOf('month')
  const gridStart = monthStart.startOf('week')
  const gridEnd = monthStart.endOf('month').endOf('week')
  const cells: ScheduleCell[] = []
  for (let day = gridStart; day.isBefore(gridEnd, 'day') || day.isSame(gridEnd, 'day'); day = day.add(1, 'day')) {
    const dayKey = day.format('YYYY-MM-DD')
    cells.push({
      date: day,
      inMonth: day.isSame(monthStart, 'month'),
      entries: rows.filter(row => {
        const start = dayjs(row.startDate)
        const end = dayjs(row.endDate)
        return (day.isAfter(start, 'day') || day.isSame(start, 'day'))
          && (day.isBefore(end, 'day') || day.isSame(end, 'day'))
      }).sort((left, right) => (left.startDate || '').localeCompare(right.startDate || '') || left.id - right.id),
    })
    void dayKey
  }
  const weeks: ScheduleCell[][] = []
  for (let index = 0; index < cells.length; index += 7) weeks.push(cells.slice(index, index + 7))
  return weeks
}

const formatMonthLabel = (anchor: Dayjs) => `${anchor.format('YYYY年M月')}`

const weekdayLabels = ['日', '一', '二', '三', '四', '五', '六']

const itemTitle = (row: MediaAccountCalendarItem) => row.nickname || row.accountNo

const itemMeta = (row: MediaAccountCalendarItem) => [
  row.studentName || '未绑定学员',
  row.platformLabelSnapshot || '平台未记录'
].join(' · ')

function AllScheduleCalendar({
  anchor,
  rows,
  total,
  unscheduled,
  loading,
  error,
  keywordInput,
  keyword,
  statuses,
  stages,
  directors,
  operators,
  status,
  stage,
  director,
  operator,
  onAnchorChange,
  onKeywordInputChange,
  onKeywordSearch,
  onStatusChange,
  onStageChange,
  onDirectorChange,
  onOperatorChange,
  onReload
}: {
  anchor: Dayjs
  rows: MediaAccountCalendarItem[]
  total: number
  unscheduled: number
  loading: boolean
  error: string
  keywordInput: string
  keyword: string
  statuses: DictData[]
  stages: DictData[]
  directors: SimpleUser[]
  operators: SimpleUser[]
  status?: string
  stage?: string
  director?: number
  operator?: number
  onAnchorChange: (next: Dayjs | ((current: Dayjs) => Dayjs)) => void
  onKeywordInputChange: (value: string) => void
  onKeywordSearch: (value: string) => void
  onStatusChange: (value?: string) => void
  onStageChange: (value?: string) => void
  onDirectorChange: (value?: number) => void
  onOperatorChange: (value?: number) => void
  onReload: () => void
}) {
  const weeks = useMemo(() => buildScheduleCells(anchor, rows), [anchor, rows])
  const today = dayjs()
  const monthStart = anchor.startOf('month')
  const miniCells = useMemo(() => {
    const start = monthStart.startOf('week')
    return Array.from({ length: 42 }, (_, index) => start.add(index, 'day'))
  }, [monthStart])
  const activeFilters = [keyword, status, stage, director, operator].filter(value => value !== undefined && value !== '').length

  return <section className="workspace-page media-schedule-page">
    <aside className="media-schedule-sidebar">
      <div className="media-schedule-tabs"><strong>日程</strong><span>会议室</span></div>
      <Input.Search allowClear value={keywordInput} onChange={event => onKeywordInputChange(event.target.value)} onSearch={value => onKeywordSearch(value.trim())} placeholder="搜索账号或日程" />
      <div className="media-schedule-mini-head">
        <span>{formatMonthLabel(anchor)}</span>
        <Space size={2}><Button type="text" size="small" icon={<LeftOutlined />} aria-label="上一月" onClick={() => onAnchorChange(value => value.subtract(1, 'month'))} /><Button type="text" size="small" icon={<RightOutlined />} aria-label="下一月" onClick={() => onAnchorChange(value => value.add(1, 'month'))} /></Space>
      </div>
      <div className="media-schedule-mini-weekdays">{weekdayLabels.map(label => <span key={label}>{label}</span>)}</div>
      <div className="media-schedule-mini-grid">{miniCells.map(day => <button type="button" key={day.format('YYYY-MM-DD')} className={[day.isSame(anchor, 'day') ? 'active' : '', day.isSame(today, 'day') ? 'today' : '', day.isSame(anchor, 'month') ? '' : 'muted'].filter(Boolean).join(' ')} onClick={() => onAnchorChange(day)}>{day.date()}</button>)}</div>
      <div className="media-schedule-calendar-list">
        <div className="media-schedule-list-title">日历</div>
        <label><input type="checkbox" checked readOnly /> 中世健日历日程</label>
      </div>
      <div className="media-schedule-filter-stack">
        <Select allowClear value={status} onChange={onStatusChange} placeholder="当下状态" options={statuses.map(item => ({ value: item.value, label: item.label }))} />
        <Select allowClear value={stage} onChange={onStageChange} placeholder="阶段" options={stages.map(item => ({ value: item.value, label: item.label }))} />
        <Select allowClear showSearch optionFilterProp="label" value={director} onChange={onDirectorChange} placeholder="编导" options={directors.map(user => ({ value: user.id, label: user.nickname }))} />
        <Select allowClear showSearch optionFilterProp="label" value={operator} onChange={onOperatorChange} placeholder="运营" options={operators.map(user => ({ value: user.id, label: user.nickname }))} />
      </div>
    </aside>
    <main className="media-schedule-main">
      <header className="media-schedule-main-head">
        <Space><Button icon={<ReloadOutlined />} onClick={onReload} /><Button onClick={() => onAnchorChange(dayjs())}>今天</Button><Segmented value="month" options={[{ value: 'day', label: '日', disabled: true }, { value: 'week', label: '周', disabled: true }, { value: 'month', label: '月' }]} /></Space>
        <Typography.Title level={4}>{formatMonthLabel(anchor)}</Typography.Title>
        <Space.Compact><Button icon={<LeftOutlined />} aria-label="上一月" onClick={() => onAnchorChange(value => value.subtract(1, 'month'))} /><Button icon={<RightOutlined />} aria-label="下一月" onClick={() => onAnchorChange(value => value.add(1, 'month'))} /></Space.Compact>
      </header>
      <div className="media-schedule-subhead"><span>日历日程</span><span>{total} 项排期 · 未排期 {unscheduled}{activeFilters ? ` · 已筛选 ${activeFilters} 项条件` : ''}</span></div>
      {error ? <Alert type="error" showIcon message={error} action={<Button onClick={onReload}>重试</Button>} /> : <div className="media-schedule-month-shell">
        <div className="media-schedule-weekdays">{weekdayLabels.map(label => <div key={label}>周{label}</div>)}</div>
        {loading && !rows.length ? <Skeleton active paragraph={{ rows: 12 }} /> : <div className="media-schedule-month-grid">{weeks.flat().map(cell => <div key={cell.date.format('YYYY-MM-DD')} className={[cell.inMonth ? '' : 'muted', cell.date.isSame(today, 'day') ? 'today' : '', cell.date.isSame(anchor, 'day') ? 'selected' : ''].filter(Boolean).join(' ')} onClick={() => onAnchorChange(cell.date)}>
          <div className="media-schedule-day-head"><strong>{cell.date.date()}</strong><span>{cell.date.isSame(anchor.startOf('month'), 'month') && cell.date.date() === 1 ? cell.date.format('M月') : ''}</span></div>
          <div className="media-schedule-events">{cell.entries.slice(0, 3).map(row => <Tooltip key={row.id} title={<div><strong>{itemTitle(row)}</strong><div>{itemMeta(row)}</div><div>{row.currentStatusLabelSnapshot || '状态未填写'} · {row.stageLabelSnapshot || '阶段未填写'}</div><div>编导：{row.directorUserName || '未分配'} · 运营：{row.operatorUserName || '未分配'}</div><div>{row.startDate} 至 {row.endDate}</div></div>}><div className={`media-schedule-event tone-${mediaCalendarTone(row.currentStatusValue, statuses.find(item => item.value === row.currentStatusValue)?.colorType)}`}>{itemTitle(row)}</div></Tooltip>)}{cell.entries.length > 3 && <span className="media-schedule-more">+{cell.entries.length - 3} 项</span>}</div>
        </div>)}</div>}
      </div>}
      {!error && !loading && !rows.length && <Empty className="media-schedule-empty" description="当前月份没有日程" />}
    </main>
  </section>
}

export default function MediaCalendarPage({ scope = 'account' }: { scope?: CalendarScope }) {
  const [mode, setMode] = useState<ViewMode>('month')
  const [anchor, setAnchor] = useState(dayjs())
  const [rows, setRows] = useState<MediaAccountCalendarItem[]>([])
  const [total, setTotal] = useState(0), [unscheduled, setUnscheduled] = useState(0), [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [keywordInput, setKeywordInput] = useState(''), [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string>(), [stage, setStage] = useState<string>()
  const [director, setDirector] = useState<number>(), [operator, setOperator] = useState<number>()
  const [statuses, setStatuses] = useState<DictData[]>([]), [stages, setStages] = useState<DictData[]>([])
  const [directors, setDirectors] = useState<SimpleUser[]>([]), [operators, setOperators] = useState<SimpleUser[]>([])
  const range = useMemo(() => mediaCalendarWindow(anchor, mode), [anchor, mode])
  const days = useMemo(() => Array.from({ length: range.end.diff(range.start, 'day') + 1 }, (_, index) => range.start.add(index, 'day')), [range])
  const isAllCalendar = scope === 'all'
  const title = isAllCalendar ? '日历日程' : '账号日历'

  const loadReference = useCallback(async () => {
    const results = await Promise.allSettled([
      api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_CURRENT_STATUS),
      api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_STAGE),
      api.mediaAccount.calendarCandidates()
    ])
    if (results[0].status === 'fulfilled') setStatuses(results[0].value)
    if (results[1].status === 'fulfilled') setStages(results[1].value)
    if (results[2].status === 'fulfilled') {
      setDirectors(results[2].value.directors)
      setOperators(results[2].value.operators)
    }
  }, [])

  const load = useCallback(async (targetPage = 1) => {
    setLoading(true); setError('')
    try {
      const params = {
        rangeStart: range.start.format('YYYY-MM-DD'),
        rangeEnd: range.end.format('YYYY-MM-DD'),
        keyword: keyword || undefined,
        currentStatusValue: status,
        stageValue: stage,
        directorUserId: director,
        operatorUserId: operator,
      }
      const result = isAllCalendar
        ? await api.mediaAccount.calendarAll(params)
        : await api.mediaAccount.calendar({ pageNo: targetPage, pageSize: 50, ...params })
      setRows(result.list); setTotal(result.total); setUnscheduled(result.unscheduledCount); setPage(isAllCalendar ? 1 : targetPage)
    } catch (cause) {
      setRows([]); setTotal(0); setUnscheduled(0)
      setError(cause instanceof ApiError && cause.code === 403 ? `无权查看${title}` : cause instanceof Error ? cause.message : '日历加载失败')
    } finally { setLoading(false) }
  }, [director, isAllCalendar, keyword, operator, range.end, range.start, stage, status, title])

  useEffect(() => { void loadReference() }, [loadReference])
  useEffect(() => { void load(1) }, [load])

  const gridStyle = { gridTemplateColumns: `repeat(${days.length}, ${DAY_WIDTH}px)` }
  const todayIndex = dayjs().startOf('day').diff(range.start, 'day')
  const statusColors = useMemo(() => new Map(statuses.map(item => [item.value, item.colorType])), [statuses])

  if (isAllCalendar) return <AllScheduleCalendar
    anchor={anchor} rows={rows} total={total} unscheduled={unscheduled} loading={loading} error={error}
    keywordInput={keywordInput} keyword={keyword} statuses={statuses} stages={stages} directors={directors} operators={operators}
    status={status} stage={stage} director={director} operator={operator}
    onAnchorChange={setAnchor} onKeywordInputChange={setKeywordInput} onKeywordSearch={setKeyword}
    onStatusChange={setStatus} onStageChange={setStage} onDirectorChange={setDirector} onOperatorChange={setOperator}
    onReload={() => void load(1)}
  />

  return <section className="workspace-page media-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}><CalendarOutlined /> {title}</Typography.Title><Typography.Text type="secondary">{range.start.format('YYYY年M月D日')} 至 {range.end.format('YYYY年M月D日')} · 未排期 {unscheduled}</Typography.Text></div><Space><Button icon={<ReloadOutlined />} onClick={() => void load(page)} /><Segmented value={mode} onChange={value => { setMode(value as ViewMode); setPage(1) }} options={[{ value: 'week', label: '周' }, { value: 'month', label: '月' }, { value: 'quarter', label: '季' }, { value: 'year', label: '年' }]} /></Space></div>
    <div className="media-calendar-toolbar">
      <Space.Compact><Button icon={<LeftOutlined />} aria-label="上一周期" onClick={() => setAnchor(value => moveAnchor(value, mode, -1))} /><Button onClick={() => setAnchor(dayjs())}>今天</Button><Button icon={<RightOutlined />} aria-label="下一周期" onClick={() => setAnchor(value => moveAnchor(value, mode, 1))} /></Space.Compact>
      <Input.Search allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索账号编号或昵称" />
      <Select allowClear value={status} onChange={setStatus} placeholder="当下状态" options={statuses.map(item => ({ value: item.value, label: item.label }))} />
      <Select allowClear value={stage} onChange={setStage} placeholder="阶段" options={stages.map(item => ({ value: item.value, label: item.label }))} />
      <Select allowClear showSearch optionFilterProp="label" value={director} onChange={setDirector} placeholder="编导" options={directors.map(user => ({ value: user.id, label: user.nickname }))} />
      <Select allowClear showSearch optionFilterProp="label" value={operator} onChange={setOperator} placeholder="运营" options={operators.map(user => ({ value: user.id, label: user.nickname }))} />
    </div>
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load(page)}>重试</Button>} /> : loading && !rows.length ? <Skeleton active paragraph={{ rows: 10 }} /> : !rows.length ? <Empty description={isAllCalendar ? '当前范围内没有已排期事项' : '当前范围内没有已排期账号'} /> : <div className="media-calendar-shell">
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
  </section>
}
