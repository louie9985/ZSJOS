import { CalendarOutlined, LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Input, Segmented, Select, Skeleton, Space, Tooltip, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { DICT_TYPE } from '../constants'
import { ApiError, api, type DictData, type MediaAccountCalendarItem, type SimpleUser } from '../services/api'

type ViewMode = 'week' | 'month' | 'quarter' | 'year'
const DAY_WIDTH = 32
const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日'] as const

export const parseCalendarDate = (value: string) => {
  const normalized = value.trim().replace(' ', 'T')
  return dayjs(/^\d{4}-\d{2}-\d{2}$/.test(normalized) ? `${normalized}T00:00:00+08:00` : normalized)
}
export const mondayOfWeek = (date: Dayjs) => date.startOf('day').subtract((date.day() + 6) % 7, 'day')
export const calendarWeekdayLabel = (date: Dayjs) => WEEKDAY_LABELS[(date.day() + 6) % 7]
export const mediaCalendarWindow = (anchor: Dayjs, mode: ViewMode) => {
  if (mode === 'week') { const start = mondayOfWeek(anchor); return { start, end: start.add(6, 'day') } }
  if (mode === 'month') return { start: anchor.startOf('month'), end: anchor.endOf('month') }
  if (mode === 'quarter') { const start = anchor.month(Math.floor(anchor.month() / 3) * 3).startOf('month'); return { start, end: start.add(2, 'month').endOf('month') } }
  return { start: anchor.startOf('year'), end: anchor.endOf('year') }
}
export const mediaCalendarTone = (value?: string, colorType?: string) => {
  if (colorType && ['success', 'primary', 'warning', 'info'].includes(colorType)) return colorType
  return value?.startsWith('a_') ? 'success' : value?.startsWith('b_') ? 'primary' : value?.startsWith('c_') ? 'warning' : value?.startsWith('d_') ? 'info' : 'neutral'
}
const moveAnchor = (anchor: Dayjs, mode: ViewMode, direction: number) => mode === 'quarter'
  ? anchor.add(direction * 3, 'month') : anchor.add(direction, mode === 'week' ? 'week' : mode === 'month' ? 'month' : 'year')

export default function MediaCalendarPage() {
  const [mode, setMode] = useState<ViewMode>('month'), [anchor, setAnchor] = useState(dayjs())
  const [rows, setRows] = useState<MediaAccountCalendarItem[]>([]), [unscheduled, setUnscheduled] = useState(0)
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [keywordInput, setKeywordInput] = useState(''), [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string>(), [stage, setStage] = useState<string>()
  const [director, setDirector] = useState<number>(), [operator, setOperator] = useState<number>()
  const [statuses, setStatuses] = useState<DictData[]>([]), [stages, setStages] = useState<DictData[]>([])
  const [directors, setDirectors] = useState<SimpleUser[]>([]), [operators, setOperators] = useState<SimpleUser[]>([])
  const range = useMemo(() => mediaCalendarWindow(anchor, mode), [anchor, mode])
  const days = useMemo(() => Array.from({ length: range.end.diff(range.start, 'day') + 1 }, (_, i) => range.start.add(i, 'day')), [range])

  useEffect(() => { void Promise.allSettled([api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_CURRENT_STATUS), api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_STAGE), api.mediaAccount.calendarCandidates()]).then(([a, b, c]) => {
    if (a.status === 'fulfilled') setStatuses(a.value); if (b.status === 'fulfilled') setStages(b.value)
    if (c.status === 'fulfilled') { setDirectors(c.value.directors); setOperators(c.value.operators) }
  }) }, [])
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const result = await api.mediaAccount.calendar({ pageNo: 1, pageSize: 200, rangeStart: range.start.format('YYYY-MM-DD'), rangeEnd: range.end.format('YYYY-MM-DD'), keyword: keyword || undefined, currentStatusValue: status, stageValue: stage, directorUserId: director, operatorUserId: operator })
      setRows(result.list); setUnscheduled(result.unscheduledCount)
    } catch (cause) { setRows([]); setUnscheduled(0); setError(cause instanceof ApiError && cause.code === 403 ? '无权查看账号日历' : cause instanceof Error ? cause.message : '账号日历加载失败') }
    finally { setLoading(false) }
  }, [director, keyword, operator, range.end, range.start, stage, status])
  useEffect(() => { void load() }, [load])

  const gridStyle = { gridTemplateColumns: `repeat(${days.length}, ${DAY_WIDTH}px)` }
  const todayIndex = dayjs().startOf('day').diff(range.start, 'day')
  const statusColors = useMemo(() => new Map(statuses.map(item => [item.value, item.colorType])), [statuses])
  return <section className="workspace-page media-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}><CalendarOutlined /> 账号日历</Typography.Title><Typography.Text type="secondary">{range.start.format('YYYY年M月D日')} 至 {range.end.format('YYYY年M月D日')} · 未排期 {unscheduled}</Typography.Text></div><Space><Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void load()} /><Segmented value={mode} onChange={v => setMode(v as ViewMode)} options={['week','month','quarter','year'].map((v, i) => ({ value: v, label: ['周','月','季','年'][i] }))} /></Space></div>
    <div className="media-calendar-toolbar"><Space.Compact><Button icon={<LeftOutlined />} aria-label="上一周期" onClick={() => setAnchor(v => moveAnchor(v, mode, -1))} /><Button onClick={() => setAnchor(dayjs())}>今天</Button><Button icon={<RightOutlined />} aria-label="下一周期" onClick={() => setAnchor(v => moveAnchor(v, mode, 1))} /></Space.Compact><Input.Search allowClear value={keywordInput} onChange={e => setKeywordInput(e.target.value)} onSearch={v => setKeyword(v.trim())} placeholder="搜索账号编号或昵称" /><Select allowClear value={status} onChange={setStatus} placeholder="当下状态" options={statuses.map(v => ({ value: v.value, label: v.label }))} /><Select allowClear value={stage} onChange={setStage} placeholder="阶段" options={stages.map(v => ({ value: v.value, label: v.label }))} /><Select allowClear value={director} onChange={setDirector} placeholder="编导" options={directors.map(v => ({ value: v.id, label: v.nickname }))} /><Select allowClear value={operator} onChange={setOperator} placeholder="运营" options={operators.map(v => ({ value: v.id, label: v.nickname }))} /></div>
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} /> : loading && !rows.length ? <Skeleton active /> : !rows.length ? <Empty description="当前范围内没有已排期账号" /> : <div className="media-calendar-shell"><div className="media-calendar-info-column"><div className="media-calendar-info-head">账号 / 学员</div>{rows.map(r => <div className="media-calendar-info" key={r.id}><strong>{r.nickname || r.accountNo}</strong><span>{r.studentName || '未绑定学员'} · {r.platformLabelSnapshot || '平台未记录'}</span></div>)}</div><div className="media-calendar-scroll"><div className="media-calendar-grid media-calendar-header" style={gridStyle}>{days.map(d => <div key={d.format('YYYY-MM-DD')}><strong>{d.date()}</strong><span>周{calendarWeekdayLabel(d)}</span></div>)}</div><div className="media-calendar-rows">{rows.map(r => { const startDate=parseCalendarDate(r.startDate), endDate=parseCalendarDate(r.endDate); const start=(startDate.isBefore(range.start)?range.start:startDate).diff(range.start,'day'); const end=endDate.isAfter(range.end)?range.end:endDate; return <div className="media-calendar-row" key={r.id}><div className="media-calendar-grid media-calendar-cells" style={gridStyle}>{days.map(d => <div key={d.format('YYYY-MM-DD')} />)}</div><Tooltip title={`${r.startDate} 至 ${r.endDate}`}><div className={`media-calendar-bar tone-${mediaCalendarTone(r.currentStatusValue,statusColors.get(r.currentStatusValue || ''))}`} style={{left:start*DAY_WIDTH+2,width:Math.max((end.diff(startDate.isBefore(range.start)?range.start:startDate,'day')+1)*DAY_WIDTH-4,12)}}>{r.nickname || r.accountNo}</div></Tooltip></div> })}{todayIndex >= 0 && todayIndex < days.length && <div className="media-calendar-today" style={{ left: todayIndex * DAY_WIDTH + DAY_WIDTH / 2 }} />}</div></div></div>}
  </section>
}
