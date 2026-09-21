import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Badge, Button, Calendar, DatePicker, Empty, Form, Input, Modal, Popconfirm, Space, Spin, Switch, Typography, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, api, type PersonalCalendarEvent, type PersonalCalendarEventInput } from '../services/api'

import BusinessReadScope, { type BusinessReadScopeValue } from '../components/BusinessReadScope'

type EditorValues = { title: string; description?: string; range: [Dayjs, Dayjs]; allDay: boolean }
type TimelineEvent = PersonalCalendarEvent & { visibleStart: Dayjs; visibleEnd: Dayjs; column: number; columns: number }
const hasPermission = (permissions: string[], value: string) => permissions.includes('*:*:*') || permissions.includes(value)
export const personalCalendarEventTouchesDay = (event: Pick<PersonalCalendarEvent, 'startTime' | 'endTime'>, day: Dayjs) => {
  const dayStart = day.startOf('day')
  const dayEnd = dayStart.add(1, 'day')
  const start = dayjs(event.startTime)
  const end = dayjs(event.endTime)
  return start.isSame(end) ? !start.isBefore(dayStart) && start.isBefore(dayEnd) : start.isBefore(dayEnd) && end.isAfter(dayStart)
}
export const personalCalendarEventPosition = (event: Pick<PersonalCalendarEvent, 'startTime' | 'endTime'>, day: Dayjs) => {
  const dayStart = day.startOf('day'); const dayEnd = dayStart.add(1, 'day')
  const start = dayjs(event.startTime); const end = dayjs(event.endTime)
  const visibleStart = start.isAfter(dayStart) ? start : dayStart
  const visibleEnd = end.isAfter(visibleStart) ? (end.isBefore(dayEnd) ? end : dayEnd) : visibleStart.add(1, 'minute')
  return { top: visibleStart.diff(dayStart, 'minute') / 1440 * 100, height: Math.max(visibleEnd.diff(visibleStart, 'minute'), 1) / 1440 * 100,
    start: visibleStart, end: visibleEnd }
}

const buildTimelineEvents = (events: PersonalCalendarEvent[], day: Dayjs): TimelineEvent[] => {
  const positioned = events.filter(event => personalCalendarEventTouchesDay(event, day)).map(event => ({ event, position: personalCalendarEventPosition(event, day) }))
    .sort((a, b) => a.position.start.valueOf() - b.position.start.valueOf() || a.position.end.valueOf() - b.position.end.valueOf())
  const columns: TimelineEvent[][] = []
  return positioned.map(({ event, position }) => {
    let column = columns.findIndex(items => !items.some(item => item.visibleEnd.isAfter(position.start)))
    if (column < 0) { column = columns.length; columns.push([]) }
    const item = { ...event, visibleStart: position.start, visibleEnd: position.end, column, columns: 1 } as TimelineEvent
    columns[column].push(item)
    const overlapping = columns.flat().filter(other => other.visibleStart.isBefore(position.end) && other.visibleEnd.isAfter(position.start))
    const count = Math.max(...overlapping.map(other => other.column + 1), column + 1)
    overlapping.forEach(other => { other.columns = Math.max(other.columns, count) })
    item.columns = count
    return item
  })
}

function DayTimeline({ events, day, permissions, onEdit, onDelete }: { events: PersonalCalendarEvent[]; day: Dayjs; permissions: string[]; onEdit: (event: PersonalCalendarEvent) => void; onDelete: (id: number) => void }) {
  const rows = buildTimelineEvents(events, day)
  return <div className="personal-calendar-timeline-wrap">{!rows.length ? <Empty description="当天暂无日程" /> : <div className="personal-calendar-timeline"><div className="personal-calendar-hours">{Array.from({ length: 24 }, (_, hour) => <div key={hour}>{`${String(hour).padStart(2, '0')}:00`}</div>)}</div><div className="personal-calendar-track">{Array.from({ length: 24 }, (_, hour) => <div className="personal-calendar-hour-line" key={hour} style={{ top: `${hour / 24 * 100}%` }} />)}{rows.map(event => <div className="personal-calendar-timeline-event" key={event.id} style={{ top: `${personalCalendarEventPosition(event, day).top}%`, height: `${personalCalendarEventPosition(event, day).height}%`, left: `${event.column / event.columns * 100}%`, width: `${100 / event.columns}%` }}><div className="personal-calendar-timeline-card"><strong>{event.title}{event.ownerName && ` · ${event.ownerName}`}</strong><span>{event.allDay ? '全天' : `${event.visibleStart.format('HH:mm')} - ${event.visibleEnd.format('HH:mm')}`}</span>{event.description && <small>{event.description}</small>}<Space size={0}>{hasPermission(permissions, 'zsjos:personal-calendar:update') && <Button type="text" size="small" icon={<EditOutlined />} aria-label="编辑日程" onClick={() => onEdit(event)} />}{hasPermission(permissions, 'zsjos:personal-calendar:delete') && <Popconfirm title="删除这条日程？" onConfirm={() => onDelete(event.id)}><Button danger type="text" size="small" icon={<DeleteOutlined />} aria-label="删除日程" /></Popconfirm>}</Space></div></div>)}</div></div>}</div>
}

export default function PersonalCalendarPage({ permissions, tenantReadAll = false }: { permissions: string[]; tenantReadAll?: boolean }) {
  const [readScope, setReadScope] = useState<BusinessReadScopeValue>({ readScope: 'SELF' })
  const requestSequence = useRef(0)
  const readOnly = readScope.readScope !== 'SELF'
  const actionPermissions = readOnly ? [] : permissions
  const [anchor, setAnchor] = useState(dayjs()), [events, setEvents] = useState<PersonalCalendarEvent[]>([])
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PersonalCalendarEvent>(), [saving, setSaving] = useState(false)
  const [selectedDay, setSelectedDay] = useState<Dayjs>()
  const [form] = Form.useForm<EditorValues>()
  const range = useMemo(() => ({ start: anchor.startOf('month').startOf('week'), end: anchor.endOf('month').endOf('week') }), [anchor])
  const load = useCallback(async () => {
    const sequence = ++requestSequence.current
    setEvents([]); setError('')
    if (readScope.readScope === 'USER' && !readScope.targetUserId) { setLoading(false); return }
    setLoading(true)
    try {
      const rows = await api.personalCalendar.list({ ...readScope, rangeStart: range.start.format('YYYY-MM-DDTHH:mm:ss'), rangeEnd: range.end.add(1, 'second').format('YYYY-MM-DDTHH:mm:ss') })
      if (sequence === requestSequence.current) setEvents(rows)
    } catch (cause) {
      if (sequence === requestSequence.current) setError(cause instanceof ApiError && cause.code === 403 ? '无权查看日历' : cause instanceof Error ? cause.message : '个人日程加载失败')
    } finally { if (sequence === requestSequence.current) setLoading(false) }
  }, [range, readScope])
  useEffect(() => { void load() }, [load])

  const startCreate = (date = anchor) => { setEditing(undefined); form.setFieldsValue({ title: '', description: '', range: [date.hour(9).minute(0), date.hour(10).minute(0)], allDay: false }); setOpen(true) }
  const startEdit = (event: PersonalCalendarEvent) => { setEditing(event); form.setFieldsValue({ title: event.title, description: event.description, range: [dayjs(event.startTime), dayjs(event.endTime)], allDay: event.allDay }); setOpen(true) }
  const save = async () => { const values = await form.validateFields(); const data: PersonalCalendarEventInput = { title: values.title.trim(), description: values.description?.trim() || undefined, startTime: values.range[0].format('YYYY-MM-DDTHH:mm:ss'), endTime: values.range[1].format('YYYY-MM-DDTHH:mm:ss'), allDay: values.allDay }; setSaving(true); try { if (editing) await api.personalCalendar.update(editing.id, data); else await api.personalCalendar.create(data); message.success(editing ? '日程已更新' : '日程已创建'); setOpen(false); await load() } catch (cause) { message.error(cause instanceof Error ? cause.message : '日程保存失败，请重试') } finally { setSaving(false) } }
  const remove = async (id: number) => { try { await api.personalCalendar.delete(id); message.success('日程已删除'); await load() } catch (cause) { message.error(cause instanceof Error ? cause.message : '日程删除失败，请重试'); await load() } }

  return <section className="workspace-page personal-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}>我的日历</Typography.Title><Typography.Text type="secondary">{anchor.format('YYYY年M月')}</Typography.Text></div><Space><Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void load()} />{hasPermission(actionPermissions, 'zsjos:personal-calendar:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => startCreate()}>新建日程</Button>}</Space></div>
    {tenantReadAll && <BusinessReadScope value={readScope} onChange={value => { setReadScope(value); setOpen(false); setSelectedDay(undefined) }} />}
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} /> : <Spin spinning={loading}><Calendar value={anchor} onChange={setAnchor} onSelect={(date, info) => { if (info.source === 'date') setSelectedDay(date) }} cellRender={(date, info) => info.type === 'date' ? <div className="personal-calendar-events">{events.filter(event => personalCalendarEventTouchesDay(event, date)).slice(0, 3).map(event => <div className="personal-calendar-event" key={event.id}><Badge status="processing" text={readOnly && event.ownerName ? `${event.title} · ${event.ownerName}` : event.title} /><Space size={0}>{hasPermission(actionPermissions, 'zsjos:personal-calendar:update') && <Button type="text" size="small" icon={<EditOutlined />} aria-label="编辑日程" onClick={e => { e.stopPropagation(); startEdit(event) }} />}{hasPermission(actionPermissions, 'zsjos:personal-calendar:delete') && <Popconfirm title="删除这条日程？" onConfirm={() => void remove(event.id)}><Button danger type="text" size="small" icon={<DeleteOutlined />} aria-label="删除日程" onClick={e => e.stopPropagation()} /></Popconfirm>}</Space></div>)}{events.filter(event => personalCalendarEventTouchesDay(event, date)).length > 3 && <Typography.Text type="secondary">另有 {events.filter(event => personalCalendarEventTouchesDay(event, date)).length - 3} 条</Typography.Text>}</div> : info.originNode} /></Spin>}
    <Modal title={`${selectedDay?.format('YYYY年M月D日')} 日程`} open={Boolean(selectedDay)} onCancel={() => setSelectedDay(undefined)} footer={null} width="min(900px, calc(100vw - 32px))" destroyOnHidden>{selectedDay && <DayTimeline events={events} day={selectedDay} permissions={actionPermissions} onEdit={startEdit} onDelete={id => void remove(id)} />}</Modal>
    <Modal title={editing ? '编辑日程' : '新建日程'} open={open} confirmLoading={saving} onCancel={() => setOpen(false)} onOk={() => void save()} destroyOnHidden><Form form={form} layout="vertical"><Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true, max: 100 }]}><Input /></Form.Item><Form.Item name="range" label="时间" rules={[{ required: true }]}><DatePicker.RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} /></Form.Item><Form.Item name="allDay" label="全天" valuePropName="checked"><Switch /></Form.Item><Form.Item name="description" label="说明" rules={[{ max: 2000 }]}><Input.TextArea rows={4} /></Form.Item></Form></Modal>
  </section>
}
