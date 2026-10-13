import CalendarDayTimeline from '../components/CalendarDayTimeline'
import CalendarViewToolbar from '../components/CalendarViewToolbar'
import CalendarTimeGrid from '../components/CalendarTimeGrid'
import { calendarViewRange, type CalendarView } from '../components/calendarViewLayout'
import CalendarColorEditor, { useCalendarPalette } from '../components/CalendarColorEditor'
import { examBackgroundStyle } from './examCalendarColor'
import CalendarSearch from '../components/CalendarSearch'
import { useCalendarHighlight } from '../components/useCalendarHighlight'
import { calendarSearchApi, searchPresentation, locationDate, CalendarResultExpired, calendarWallTime } from '../services/calendarSearch'
import { LinkedText, REMARK_LINK_HINT } from '../components/ResourceLink'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Calendar, DatePicker, Form, Input, Modal, Popconfirm, Space, Spin, Switch, Typography, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, api, type PersonalCalendarEvent, type PersonalCalendarEventInput } from '../services/api'

import BusinessReadScope, { type BusinessReadScopeValue } from '../components/BusinessReadScope'

type EditorValues = { backgroundColor?: string; title: string; description?: string; range: [Dayjs, Dayjs]; allDay: boolean }
const hasPermission = (permissions: string[], value: string) => permissions.includes('*:*:*') || permissions.includes(value)
export const personalCalendarEventTouchesDay = (event: Pick<PersonalCalendarEvent, 'startTime' | 'endTime'>, day: Dayjs) => {
  const dayStart = day.startOf('day')
  const dayEnd = dayStart.add(1, 'day')
  const start = calendarWallTime(event.startTime)
  const end = calendarWallTime(event.endTime)
  return start.isSame(end) ? !start.isBefore(dayStart) && start.isBefore(dayEnd) : start.isBefore(dayEnd) && end.isAfter(dayStart)
}
export const personalCalendarEventPosition = (event: Pick<PersonalCalendarEvent, 'startTime' | 'endTime'>, day: Dayjs) => {
  const dayStart = day.startOf('day'); const dayEnd = dayStart.add(1, 'day')
  const start = calendarWallTime(event.startTime); const end = calendarWallTime(event.endTime)
  const visibleStart = start.isAfter(dayStart) ? start : dayStart
  const visibleEnd = end.isAfter(visibleStart) ? (end.isBefore(dayEnd) ? end : dayEnd) : visibleStart.add(1, 'minute')
  return { top: visibleStart.diff(dayStart, 'minute') / 1440 * 100, height: Math.max(visibleEnd.diff(visibleStart, 'minute'), 1) / 1440 * 100,
    start: visibleStart, end: visibleEnd }
}

function DayTimeline({ events, day, permissions, onEdit, onDelete, highlightedId }: { highlightedId?: number; events: PersonalCalendarEvent[]; day: Dayjs; permissions: string[]; onEdit: (event: PersonalCalendarEvent) => void; onDelete: (id: number) => void }) {
 return <CalendarDayTimeline items={events} day={day} highlightedId={highlightedId} emptyText="当天暂无日程" renderDetails={(id, close) => {
  const event = events.find(item => item.id === id)!
  return <><dl><dt>创建人</dt><dd>{event.creatorName || '未记录或账号不可用'}</dd><dt>所属员工</dt><dd>{event.ownerName || '未知账号'}</dd><dt>说明</dt><dd><LinkedText text={event.description || '无'} mode="remark" /></dd></dl>
   <div className="calendar-day-actions">{hasPermission(permissions, 'zsjos:personal-calendar:update') && <Button aria-label="编辑日程" icon={<EditOutlined />} onClick={() => { close(); onEdit(event) }}>编辑日程</Button>}{hasPermission(permissions, 'zsjos:personal-calendar:delete') && <Popconfirm title="删除这条日程？" onConfirm={() => onDelete(event.id)}><Button aria-label="删除日程" danger icon={<DeleteOutlined />}>删除日程</Button></Popconfirm>}</div></>
 }} />
}

export default function PersonalCalendarPage({ permissions, tenantReadAll = false }: { permissions: string[]; tenantReadAll?: boolean }) {
  const palette = useCalendarPalette('PERSONAL')
  const [readScope, setReadScope] = useState<BusinessReadScopeValue>({ readScope: 'SELF' })
  const [highlightedId, setHighlightedId] = useState<number>()
  const requestSequence = useRef(0)
  const readOnly = readScope.readScope !== 'SELF'
  const actionPermissions = readOnly ? [] : permissions
  const [calendarMode, setCalendarMode] = useState<CalendarView>('month'); const [anchor, setAnchor] = useState(dayjs()), [events, setEvents] = useState<PersonalCalendarEvent[]>([])
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PersonalCalendarEvent>(), [saving, setSaving] = useState(false)
  const [selectedDay, setSelectedDay] = useState<Dayjs>()
  useCalendarHighlight(highlightedId, events)
  const [form] = Form.useForm<EditorValues>()
  const range = useMemo(() => calendarViewRange(anchor, calendarMode), [anchor, calendarMode])
  const load = useCallback(async () => {
    const sequence = ++requestSequence.current
    setEvents([]); setError('')
    if (readScope.readScope === 'USER' && !readScope.targetUserId) { setLoading(false); return }
    setLoading(true)
    try {
      const rows = await api.personalCalendar.list({ ...readScope, rangeStart: range.start.format('YYYY-MM-DDTHH:mm:ss'), rangeEnd: range.end.format('YYYY-MM-DDTHH:mm:ss') })
      if (sequence === requestSequence.current) setEvents(rows)
    } catch (cause) {
      if (sequence === requestSequence.current) setError(cause instanceof ApiError && cause.code === 403 ? '无权查看日历' : cause instanceof Error ? cause.message : '个人日程加载失败')
    } finally { if (sequence === requestSequence.current) setLoading(false) }
  }, [range, readScope])
  useEffect(() => { void load() }, [load])

  const startCreate = (date = anchor, exactTime = false) => { palette.reload(); setEditing(undefined); form.setFieldsValue({ title: '', description: '', backgroundColor: '', range: exactTime ? [date, date.add(1, 'hour')] : [date.hour(9).minute(0), date.hour(10).minute(0)], allDay: false }); setOpen(true) }
  const startEdit = (event: PersonalCalendarEvent) => { palette.reload(); setEditing(event); form.setFieldsValue({ backgroundColor: event.backgroundColor || '', title: event.title, description: event.description, range: [calendarWallTime(event.startTime), calendarWallTime(event.endTime)], allDay: event.allDay }); setOpen(true) }
  const save = async () => { const values = await form.validateFields(); const data: PersonalCalendarEventInput = { backgroundColor: values.backgroundColor || '', title: values.title.trim(), description: values.description?.trim() || undefined, startTime: values.range[0].format('YYYY-MM-DDTHH:mm:ss'), endTime: values.range[1].format('YYYY-MM-DDTHH:mm:ss'), allDay: values.allDay }; setSaving(true); try { if (editing) await api.personalCalendar.update(editing.id, data); else await api.personalCalendar.create(data); message.success(editing ? '日程已更新' : '日程已创建'); setOpen(false); await load() } catch (cause) { message.error(cause instanceof Error ? cause.message : '日程保存失败，请重试') } finally { setSaving(false) } }
  const remove = async (id: number) => { try { await api.personalCalendar.delete(id); message.success('日程已删除'); await load() } catch (cause) { message.error(cause instanceof Error ? cause.message : '日程删除失败，请重试'); await load() } }

  return <section className="workspace-page personal-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}>我的日历</Typography.Title><Typography.Text type="secondary">{anchor.format('YYYY年M月')}</Typography.Text></div><Space><CalendarSearch scopeKey={JSON.stringify(readScope)} scopeLabel={readScope.readScope === 'SELF' ? '本人日程' : readScope.readScope === 'ALL' ? '全部人员（只读）' : '当前指定人员（只读）'} disabled={readScope.readScope === 'USER' && !readScope.targetUserId}
      search={(query, signal) => calendarSearchApi.personal(query, readScope, signal)} present={searchPresentation.personal}
      onLocate={async (row, signal) => {
        const item = searchPresentation.personal(row); const date = dayjs(locationDate(item.start, item.locationEnd || item.end))
        const fresh = await api.personalCalendar.list({ ...readScope, rangeStart: date.format('YYYY-MM-DDT00:00:00'), rangeEnd: date.add(1, 'day').format('YYYY-MM-DDT00:00:00') })
        signal.throwIfAborted()
        if (!fresh.some(event => event.id === row.id)) throw new CalendarResultExpired()
        setHighlightedId(row.id); setAnchor(date); setSelectedDay(date); setEvents(fresh)
      }} /><Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => { palette.reload(); void load() }} />{hasPermission(actionPermissions, 'zsjos:personal-calendar:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => startCreate()}>新建日程</Button>}</Space></div>
    {tenantReadAll && <BusinessReadScope value={readScope} onChange={value => { setReadScope(value); setOpen(false); setSelectedDay(undefined) }} />}
    <CalendarViewToolbar view={calendarMode} date={anchor} onView={view => { setSelectedDay(undefined); setCalendarMode(view) }} onDate={date => { setSelectedDay(undefined); setAnchor(date) }} /><div className="calendar-side-navigation-content">
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} /> : <Spin spinning={loading}>{calendarMode !== 'month' ? <CalendarTimeGrid view={calendarMode} date={anchor} events={events.map(event => ({ id:event.id, title:readOnly && event.ownerName ? event.title + ' · ' + event.ownerName : event.title, start:calendarWallTime(event.startTime), end:calendarWallTime(event.endTime), allDay:event.allDay, color:event.backgroundColor }))} onOpen={(id,date) => { setHighlightedId(id); setSelectedDay(date) }} onCreate={hasPermission(actionPermissions, 'zsjos:personal-calendar:create') ? date => startCreate(date,true) : undefined} /> : <Calendar value={anchor} mode="month" headerRender={() => null} onChange={setAnchor} onSelect={(date, info) => { if (info.source === 'date') setSelectedDay(date) }} cellRender={(date, info) => info.type === 'date' ? <div className="personal-calendar-events">{events.filter(event => personalCalendarEventTouchesDay(event, date)).map(event => <div className="personal-calendar-event" key={event.id} data-calendar-colored={!!event.backgroundColor} style={examBackgroundStyle(event.backgroundColor)}><span className="personal-calendar-event-title">{readOnly && event.ownerName ? `${event.title} · ${event.ownerName}` : event.title}</span></div>)}</div> : info.originNode} />}</Spin>}
    </div>
    <Modal title={`${selectedDay?.format('YYYY年M月D日')} 日程`} open={Boolean(selectedDay)} onCancel={() => setSelectedDay(undefined)} footer={null} width="min(1100px, calc(100vw - 32px))" destroyOnHidden>{selectedDay && <DayTimeline highlightedId={highlightedId} events={events} day={selectedDay} permissions={actionPermissions} onEdit={startEdit} onDelete={id => void remove(id)} />}</Modal>
    <Modal title={editing ? '编辑日程' : '新建日程'} open={open} confirmLoading={saving} onCancel={() => setOpen(false)} onOk={() => void save()} destroyOnHidden><Form form={form} layout="vertical"><Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true, max: 100 }]}><Input /></Form.Item><Form.Item name="range" label="时间" rules={[{ required: true }]}><DatePicker.RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} /></Form.Item><Form.Item name="allDay" label="全天" valuePropName="checked"><Switch /></Form.Item><Form.Item name="backgroundColor" label="日程底色"><CalendarColorEditor type="PERSONAL" state={palette} canManage={hasPermission(actionPermissions, 'zsjos:personal-calendar:color-manage')} /></Form.Item><Form.Item name="description" label="说明" className="remark-link-field" extra={REMARK_LINK_HINT} rules={[{ max: 2000 }]}><Input.TextArea rows={4} /></Form.Item></Form></Modal>
  </section>
}
