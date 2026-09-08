import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Badge, Button, Calendar, DatePicker, Empty, Form, Input, Modal, Popconfirm, Space, Spin, Switch, Typography, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, api, type PersonalCalendarEvent, type PersonalCalendarEventInput } from '../services/api'

type EditorValues = { title: string; description?: string; range: [Dayjs, Dayjs]; allDay: boolean }
const hasPermission = (permissions: string[], value: string) => permissions.includes('*:*:*') || permissions.includes(value)
export const personalCalendarEventTouchesDay = (event: Pick<PersonalCalendarEvent, 'startTime' | 'endTime'>, day: Dayjs) => {
  const dayStart = day.startOf('day')
  const dayEnd = dayStart.add(1, 'day')
  const start = dayjs(event.startTime)
  const end = dayjs(event.endTime)
  return start.isSame(end) ? !start.isBefore(dayStart) && start.isBefore(dayEnd) : start.isBefore(dayEnd) && end.isAfter(dayStart)
}

export default function PersonalCalendarPage({ permissions }: { permissions: string[] }) {
  const [anchor, setAnchor] = useState(dayjs()), [events, setEvents] = useState<PersonalCalendarEvent[]>([])
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PersonalCalendarEvent>(), [saving, setSaving] = useState(false)
  const [form] = Form.useForm<EditorValues>()
  const range = useMemo(() => ({ start: anchor.startOf('month').startOf('week'), end: anchor.endOf('month').endOf('week') }), [anchor])
  const load = useCallback(async () => { setLoading(true); setError(''); try { setEvents(await api.personalCalendar.list({ rangeStart: range.start.format('YYYY-MM-DDTHH:mm:ss'), rangeEnd: range.end.add(1, 'second').format('YYYY-MM-DDTHH:mm:ss') })) } catch (cause) { setEvents([]); setError(cause instanceof ApiError && cause.code === 403 ? '无权查看我的日历' : cause instanceof Error ? cause.message : '个人日程加载失败') } finally { setLoading(false) } }, [range])
  useEffect(() => { void load() }, [load])

  const startCreate = (date = anchor) => { setEditing(undefined); form.setFieldsValue({ title: '', description: '', range: [date.hour(9).minute(0), date.hour(10).minute(0)], allDay: false }); setOpen(true) }
  const startEdit = (event: PersonalCalendarEvent) => { setEditing(event); form.setFieldsValue({ title: event.title, description: event.description, range: [dayjs(event.startTime), dayjs(event.endTime)], allDay: event.allDay }); setOpen(true) }
  const save = async () => { const values = await form.validateFields(); const data: PersonalCalendarEventInput = { title: values.title.trim(), description: values.description?.trim() || undefined, startTime: values.range[0].format('YYYY-MM-DDTHH:mm:ss'), endTime: values.range[1].format('YYYY-MM-DDTHH:mm:ss'), allDay: values.allDay }; setSaving(true); try { if (editing) await api.personalCalendar.update(editing.id, data); else await api.personalCalendar.create(data); message.success(editing ? '日程已更新' : '日程已创建'); setOpen(false); await load() } catch (cause) { message.error(cause instanceof Error ? cause.message : '日程保存失败，请重试') } finally { setSaving(false) } }
  const remove = async (id: number) => { try { await api.personalCalendar.delete(id); message.success('日程已删除'); await load() } catch (cause) { message.error(cause instanceof Error ? cause.message : '日程删除失败，请重试'); await load() } }

  return <section className="workspace-page personal-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}>我的日历</Typography.Title><Typography.Text type="secondary">{anchor.format('YYYY年M月')}</Typography.Text></div><Space><Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void load()} />{hasPermission(permissions, 'zsjos:personal-calendar:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => startCreate()}>新建日程</Button>}</Space></div>
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} /> : <Spin spinning={loading}><Calendar value={anchor} onChange={setAnchor} cellRender={(date, info) => info.type === 'date' ? <div className="personal-calendar-events">{events.filter(event => personalCalendarEventTouchesDay(event, date)).slice(0, 3).map(event => <div className="personal-calendar-event" key={event.id}><Badge status="processing" text={event.title} /><Space size={0}>{hasPermission(permissions, 'zsjos:personal-calendar:update') && <Button type="text" size="small" icon={<EditOutlined />} aria-label="编辑日程" onClick={e => { e.stopPropagation(); startEdit(event) }} />}{hasPermission(permissions, 'zsjos:personal-calendar:delete') && <Popconfirm title="删除这条日程？" onConfirm={() => void remove(event.id)}><Button danger type="text" size="small" icon={<DeleteOutlined />} aria-label="删除日程" onClick={e => e.stopPropagation()} /></Popconfirm>}</Space></div>)}{!events.length && date.isSame(anchor, 'day') ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={false} /> : null}</div> : info.originNode} /></Spin>}
    <Modal title={editing ? '编辑日程' : '新建日程'} open={open} confirmLoading={saving} onCancel={() => setOpen(false)} onOk={() => void save()} destroyOnHidden><Form form={form} layout="vertical"><Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true, max: 100 }]}><Input /></Form.Item><Form.Item name="range" label="时间" rules={[{ required: true }]}><DatePicker.RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} /></Form.Item><Form.Item name="allDay" label="全天" valuePropName="checked"><Switch /></Form.Item><Form.Item name="description" label="说明" rules={[{ max: 2000 }]}><Input.TextArea rows={4} /></Form.Item></Form></Modal>
  </section>
}
