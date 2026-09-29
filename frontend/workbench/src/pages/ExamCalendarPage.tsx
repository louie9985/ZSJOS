import CalendarNotificationPanel from '../components/CalendarNotificationPanel'
import {
  CalendarOutlined, EditOutlined, EyeOutlined, LeftOutlined, PlusOutlined,
  ReloadOutlined, RightOutlined, SendOutlined, StopOutlined
} from '@ant-design/icons'
import {
  Alert, Button, Calendar, DatePicker, Drawer, Empty, Form, Input, List, Modal,
  Popconfirm, Radio, Select, Space, Spin, Tag, Typography, message
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ApiError, api, type ExamSchedule, type ExamScheduleInput,
  type ExamScheduleType
} from '../services/api'

type EditorValues = {
  scheduleType: ExamScheduleType
  exactDate?: Dayjs
  roughRange?: [Dayjs, Dayjs]
  scheduleName: string
  remark?: string
}

const STATUS_META: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'blue' },
  REVOKED: { label: '已撤销', color: 'default' },
  UPCOMING: { label: '即将开始', color: 'gold' },
  IN_PROGRESS: { label: '正在进行', color: 'green' },
  ENDED: { label: '已结束', color: 'default' }
}

const hasPermission = (permissions: string[], value: string) =>
  permissions.includes('*:*:*') || permissions.includes(value)

export const scheduleStatusLabel = (status: string) => STATUS_META[status]?.label || status

export const scheduleInput = (values: EditorValues): ExamScheduleInput => {
  return values.scheduleType === 'EXACT'
  ? {
      scheduleType: 'EXACT', exactDate: values.exactDate?.format('YYYY-MM-DD'),
      scheduleName: values.scheduleName.trim(),
      remark: values.remark?.trim() || undefined
    }
  : {
      scheduleType: 'ROUGH', roughStartDate: values.roughRange?.[0].format('YYYY-MM-DD'),
      roughEndDate: values.roughRange?.[1].format('YYYY-MM-DD'),
      scheduleName: values.scheduleName.trim(),
      remark: values.remark?.trim() || undefined
    }
}

function ScheduleStatus({ value }: { value: string }) {
  const meta = STATUS_META[value] || { label: value, color: 'default' }
  return <Tag color={meta.color}>{meta.label}</Tag>
}

function ScheduleDetail({ schedule }: { schedule: ExamSchedule }) {
  const period = schedule.scheduleType === 'EXACT'
    ? schedule.exactDate
    : `${schedule.roughStartDate} 至 ${schedule.roughEndDate}`
  return <div className="exam-calendar-detail">
    <div className="wide"><span>考期名称</span><strong>{schedule.scheduleName || '未命名考期'}</strong></div>
    <div><span>时间类型</span><strong>{schedule.scheduleType === 'EXACT' ? '精确时间' : '粗略时间'}</strong></div>
    <div><span>考试时间</span><strong>{period}</strong></div>
    <div><span>当前状态</span><ScheduleStatus value={schedule.displayStatus} /></div>
    <div className="wide"><span>备注</span><strong>{schedule.remark || '无'}</strong></div>
  </div>
}

export default function ExamCalendarPage({ permissions }: { permissions: string[] }) {
  const canManage = hasPermission(permissions, 'zsjos:exam-calendar:manage')
  const canNotify = hasPermission(permissions, 'zsjos:exam-calendar:notify')
  const [notifyTarget, setNotifyTarget] = useState<ExamSchedule>()
  const [anchor, setAnchor] = useState(dayjs())
  const [schedules, setSchedules] = useState<ExamSchedule[]>([])
  const [dayDetail, setDayDetail] = useState<Dayjs>()
  const [displayStatus, setDisplayStatus] = useState<string>()
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [roughOpen, setRoughOpen] = useState(false), [roughLoading, setRoughLoading] = useState(false)
  const [roughRows, setRoughRows] = useState<ExamSchedule[]>([]), [roughTotal, setRoughTotal] = useState(0)
  const [roughError, setRoughError] = useState('')
  const [editorOpen, setEditorOpen] = useState(false), [editing, setEditing] = useState<ExamSchedule>()
  const [detail, setDetail] = useState<ExamSchedule>(), [saving, setSaving] = useState(false)
  const [savingAction, setSavingAction] = useState<'DRAFT' | 'PUBLISH'>('DRAFT')
  const [form] = Form.useForm<EditorValues>()
  const requests = useRef({ exact: 0, rough: 0 })
  const scheduleType = Form.useWatch('scheduleType', form)
  const range = useMemo(() => ({ start: anchor.startOf('month'), end: anchor.endOf('month') }), [anchor])

  const load = useCallback(async () => {
    const request = ++requests.current.exact
    setLoading(true); setError(''); setSchedules([]); setDetail(undefined); setDayDetail(undefined)
    try {
      const params = {
        pageNo: 1, pageSize: 100,
        rangeStart: range.start.format('YYYY-MM-DD'), rangeEnd: range.end.format('YYYY-MM-DD'),
        displayStatus
      }
      const first = await api.examCalendar.exactPage(params)
      if (request !== requests.current.exact) return
      const pages = Math.ceil(first.total / params.pageSize)
      const rest = pages > 1 ? await Promise.all(Array.from({ length: pages - 1 }, (_, index) =>
        api.examCalendar.exactPage({ ...params, pageNo: index + 2 }))) : []
      if (request === requests.current.exact) setSchedules([first, ...rest].flatMap(result => result.list))
    } catch (cause) {
      if (request !== requests.current.exact) return
      setSchedules([])
      setError(cause instanceof ApiError && cause.code === 403 ? '无权查看考期日历'
        : cause instanceof Error ? cause.message : '考期日历加载失败')
    } finally { if (request === requests.current.exact) setLoading(false) }
  }, [displayStatus, range.end, range.start])

  const loadRough = useCallback(async () => {
    const request = ++requests.current.rough
    setRoughLoading(true); setRoughError(''); setRoughRows([]); setRoughTotal(0)
    try {
      const params = { pageNo: 1, pageSize: 100 }
      const first = await api.examCalendar.roughPage(params)
      if (request !== requests.current.rough) return
      const pages = Math.ceil(first.total / params.pageSize)
      const rest = pages > 1 ? await Promise.all(Array.from({ length: pages - 1 }, (_, index) =>
        api.examCalendar.roughPage({ ...params, pageNo: index + 2 }))) : []
      if (request === requests.current.rough) { setRoughRows([first, ...rest].flatMap(result => result.list)); setRoughTotal(first.total) }
    } catch (cause) {
      if (request !== requests.current.rough) return
      setRoughError(cause instanceof ApiError && cause.code === 403 ? '无权查看粗略考试时间'
        : cause instanceof Error ? cause.message : '粗略考期加载失败')
      setRoughRows([]); setRoughTotal(0)
    } finally { if (request === requests.current.rough) setRoughLoading(false) }
  }, [])

  const latestReload = useRef({ load, loadRough, roughOpen })
  latestReload.current = { load, loadRough, roughOpen }

  useEffect(() => { void load(); return () => { ++requests.current.exact } }, [load])
  useEffect(() => { if (roughOpen) void loadRough(); return () => { ++requests.current.rough } }, [loadRough, roughOpen])

  const openCreate = (date = anchor, type: ExamScheduleType = 'EXACT') => {
    setEditing(undefined)
    form.resetFields()
    form.setFieldsValue({
      scheduleType: type, exactDate: type === 'EXACT' ? date : undefined,
      roughRange: type === 'ROUGH' ? [date.startOf('month'), date.endOf('month')] : undefined,
      scheduleName: '', remark: ''
    })
    setEditorOpen(true)
  }

  const openEdit = (schedule: ExamSchedule) => {
    setDetail(undefined); setEditing(schedule)
    form.resetFields()
    form.setFieldsValue({
      scheduleType: schedule.scheduleType,
      exactDate: schedule.exactDate ? dayjs(schedule.exactDate) : undefined,
      roughRange: schedule.roughStartDate && schedule.roughEndDate
        ? [dayjs(schedule.roughStartDate), dayjs(schedule.roughEndDate)] : undefined,
      scheduleName: schedule.scheduleName || '', remark: schedule.remark
    })
    setEditorOpen(true)
  }

  const save = async (publishAfterSave = false) => {
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    const input = scheduleInput(values)
    setSaving(true); setSavingAction(publishAfterSave ? 'PUBLISH' : 'DRAFT')
    try {
      const request = input
      const savedId = editing ? (await api.examCalendar.update(editing.id, request), editing.id) : await api.examCalendar.create(request)
      if (publishAfterSave) {
        await api.examCalendar.publish(savedId)
        message.success('考期已保存并发布')
      } else message.success(editing ? '考期已更新' : '考期草稿已创建')
      setEditorOpen(false)
      const current = latestReload.current
      await Promise.all([current.load(), current.roughOpen ? current.loadRough() : Promise.resolve()])
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '考期保存失败')
    } finally { setSaving(false); setSavingAction('DRAFT') }
  }

  const transition = async (schedule: ExamSchedule, action: 'publish' | 'revoke') => {
    try {
      if (action === 'publish') await api.examCalendar.publish(schedule.id)
      else await api.examCalendar.revoke(schedule.id)
      message.success(action === 'publish' ? '考期已发布' : '考期已撤销')
      setDetail(undefined)
      const current = latestReload.current
      await Promise.all([current.load(), current.roughOpen ? current.loadRough() : Promise.resolve()])
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message
        : action === 'publish' ? '考期发布失败' : '考期撤销失败')
    }
  }

  const actions = (schedule: ExamSchedule) => <Space wrap>
    {canManage && schedule.recordStatus === 'DRAFT' && schedule.displayStatus !== 'ENDED' &&
      <Button icon={<EditOutlined />} onClick={() => openEdit(schedule)}>编辑</Button>}
    {canManage && schedule.recordStatus === 'DRAFT' &&
      <Button type="primary" icon={<SendOutlined />} onClick={() => void transition(schedule, 'publish')}>发布</Button>}
    {canNotify && schedule.recordStatus === 'PUBLISHED' &&
      <Button onClick={() => setNotifyTarget(schedule)}>发送通知</Button>}
    {canManage && schedule.recordStatus === 'PUBLISHED' &&
      <Popconfirm title="撤销后不能重新发布，确认撤销？" onConfirm={() => void transition(schedule, 'revoke')}>
        <Button danger icon={<StopOutlined />}>撤销</Button>
      </Popconfirm>}
  </Space>

  return <section className="workspace-page exam-calendar-page">
    <div className="page-heading">
      <div><Typography.Title level={4}><CalendarOutlined /> 考期日历</Typography.Title><Typography.Text type="secondary">{anchor.format('YYYY年M月')}</Typography.Text></div>
      <Space wrap>
        <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void load()} />
        <Button icon={<EyeOutlined />} onClick={() => setRoughOpen(true)}>粗略考试时间</Button>
        {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新增考期</Button>}
      </Space>
    </div>
    <div className="exam-calendar-toolbar">
      <Space.Compact><Button icon={<LeftOutlined />} aria-label="上一月" onClick={() => setAnchor(value => value.subtract(1, 'month'))} /><Button onClick={() => setAnchor(dayjs())}>今天</Button><Button icon={<RightOutlined />} aria-label="下一月" onClick={() => setAnchor(value => value.add(1, 'month'))} /></Space.Compact>
      <Space wrap>
        <Select allowClear placeholder="状态" value={displayStatus} onChange={setDisplayStatus} options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))} className="exam-calendar-filter" />
      </Space>
    </div>
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />
      : <Spin spinning={loading}><Calendar value={anchor} onPanelChange={setAnchor} onSelect={(date, info) => { if (info.source === 'date') setDayDetail(date) }} cellRender={(date, info) => {
        if (info.type !== 'date') return info.originNode
        const dayRows = schedules.filter(item => item.exactDate && dayjs(item.exactDate).isSame(date, 'day'))
        return <div className="exam-calendar-events">{dayRows.slice(0, 3).map(item => <button type="button" key={item.id} className={`exam-calendar-event tone-${item.displayStatus.toLowerCase()}`} onClick={event => { event.stopPropagation(); setDetail(item) }}><span>{item.scheduleName || '未命名考期'}</span><ScheduleStatus value={item.displayStatus} /></button>)}{dayRows.length > 3 && <Button type="link" size="small" className="exam-calendar-overflow" onClick={event => { event.stopPropagation(); setDayDetail(date) }}>另有 {dayRows.length - 3} 条</Button>}</div>
      }} /></Spin>}

    <Drawer title="粗略考试时间" width={520} open={roughOpen} onClose={() => setRoughOpen(false)} extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(anchor, 'ROUGH')}>新增粗略考期</Button>}>
      {roughError && <Alert type="error" showIcon message={roughError} action={<Button onClick={() => void loadRough()}>重试</Button>} />}
      <Spin spinning={roughLoading}>{!roughError && (roughRows.length ? <List dataSource={roughRows} footer={roughTotal > roughRows.length ? `当前展示前 ${roughRows.length} 条，共 ${roughTotal} 条` : undefined} renderItem={item => <List.Item actions={[<Button type="link" key="detail" onClick={() => setDetail(item)}>详情</Button>, ...(canManage && item.recordStatus === 'DRAFT' ? [<Button type="link" key="edit" onClick={() => openEdit(item)}>编辑</Button>] : [])]}><List.Item.Meta title={<Space wrap><strong>{item.scheduleName || '未命名考期'}</strong><ScheduleStatus value={item.recordStatus} /></Space>} description={<><div>{item.roughStartDate} 至 {item.roughEndDate}</div><div>{item.remark || '无备注'}</div></>} /></List.Item>} /> : <Empty description="暂无粗略考试时间" />)}</Spin>
    </Drawer>

    <Modal title="考期详情" open={Boolean(detail)} onCancel={() => setDetail(undefined)} footer={detail ? actions(detail) : null} destroyOnHidden>{detail && <ScheduleDetail schedule={detail} />}</Modal>
    <Modal title={`${dayDetail?.format('YYYY年M月D日')} 考期安排`} open={Boolean(dayDetail)} onCancel={() => setDayDetail(undefined)} footer={null} width="min(720px, calc(100vw - 32px))" destroyOnHidden>
      {dayDetail && (() => { const rows = schedules.filter(row => row.exactDate === dayDetail.format('YYYY-MM-DD')); return rows.length ? <List dataSource={rows} renderItem={row => <List.Item actions={[<Button key="detail" type="link" onClick={() => setDetail(row)}>详情</Button>]}><List.Item.Meta title={<Space wrap><strong>{row.scheduleName || '未命名考期'}</strong><ScheduleStatus value={row.displayStatus} /></Space>} description={<>{row.remark && <div>{row.remark}</div>}</>} /></List.Item>} /> : <Empty description="当天暂无考期安排" /> })()}
    </Modal>
    <Modal title={editing ? '编辑考期' : '新增考期'} open={editorOpen} confirmLoading={saving} onCancel={() => setEditorOpen(false)} footer={<Space><Button onClick={() => setEditorOpen(false)} disabled={saving}>取消</Button><Button loading={saving && savingAction === 'DRAFT'} disabled={saving} onClick={() => void save()}>保存草稿</Button><Button type="primary" loading={saving && savingAction === 'PUBLISH'} disabled={saving} onClick={() => void save(true)}>保存并发布</Button></Space>} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ scheduleType: 'EXACT' }}>
        <Form.Item name="scheduleName" label="考期名称" rules={[{ required: true, whitespace: true, message: '请填写考期名称' }, { max: 100 }]}><Input maxLength={100} placeholder="请填写考期名称" /></Form.Item>
        <Form.Item name="scheduleType" label="时间类型" rules={[{ required: true }]}><Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 'EXACT', label: '精确时间' }, { value: 'ROUGH', label: '粗略时间' }]} /></Form.Item>
        {scheduleType === 'ROUGH' ? <Form.Item name="roughRange" label="考试时间段" rules={[{ required: true, message: '请选择考试时间段' }]}><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item>
          : <Form.Item name="exactDate" label="考试日期" rules={[{ required: true, message: '请选择考试日期' }]}><DatePicker style={{ width: '100%' }} /></Form.Item>}
        <Form.Item name="remark" label="备注" rules={[{ max: 1000 }]}><Input.TextArea rows={4} showCount maxLength={1000} /></Form.Item>
      </Form>
    </Modal>
    {notifyTarget && <CalendarNotificationPanel key={notifyTarget.id} calendarType="EXAM" calendarId={notifyTarget.id} permissions={permissions} onClose={() => setNotifyTarget(undefined)} />}
  </section>
}
