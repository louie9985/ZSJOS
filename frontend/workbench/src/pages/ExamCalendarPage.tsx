import CalendarNotificationPanel from '../components/CalendarNotificationPanel'
import ExamCalendarMonth from './ExamCalendarMonth'
import { calendarWindow, coversExamDay, multiDaySchedulesForStatus } from './examCalendarLayout'
import {
  CalendarOutlined, EditOutlined, EyeOutlined, LeftOutlined, PlusOutlined,
  ReloadOutlined, RightOutlined, SendOutlined, StopOutlined
} from '@ant-design/icons'
import {
  Alert, Button, DatePicker, Drawer, Empty, Form, Input, List, Modal,
  Popconfirm, Radio, Select, Space, Spin, Switch, Tag, Typography, message
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
  multiDayRange?: [Dayjs, Dayjs]
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
      scheduleType: 'MULTI_DAY', startDate: values.multiDayRange?.[0].format('YYYY-MM-DD'),
      endDate: values.multiDayRange?.[1].format('YYYY-MM-DD'),
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
    : `${schedule.startDate} 至 ${schedule.endDate}`
  return <div className="exam-calendar-detail">
    <div className="wide"><span>考期名称</span><strong>{schedule.scheduleName || '未命名考期'}</strong></div>
    <div className="wide"><span>时间</span><strong>{period}</strong></div>
    {schedule.scheduleType === 'MULTI_DAY' && <div className="wide"><Alert type="info" showIcon message="开始日至结束日均属于本次考试安排（含首尾日期）。" /></div>}
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
  const [multiDayOpen, setMultiDayOpen] = useState(false), [multiDayLoading, setMultiDayLoading] = useState(false)
  const [multiDayRows, setMultiDayRows] = useState<ExamSchedule[]>([]), [multiDayTotal, setMultiDayTotal] = useState(0)
  const [multiDayError, setMultiDayError] = useState('')
  const [showMultiDay, setShowMultiDay] = useState(true)
  const [calendarMultiDayRows, setCalendarMultiDayRows] = useState<ExamSchedule[]>([])
  const [calendarMultiDayLoading, setCalendarMultiDayLoading] = useState(false)
  const [calendarMultiDayError, setCalendarMultiDayError] = useState('')
  const [editorOpen, setEditorOpen] = useState(false), [editing, setEditing] = useState<ExamSchedule>()
  const [detail, setDetail] = useState<ExamSchedule>(), [saving, setSaving] = useState(false)
  const [savingAction, setSavingAction] = useState<'DRAFT' | 'PUBLISH'>('DRAFT')
  const [form] = Form.useForm<EditorValues>()
  const requests = useRef({ exact: 0, multiDay: 0, calendarMultiDay: 0 })
  const scheduleType = Form.useWatch('scheduleType', form)
  const range = useMemo(() => calendarWindow(anchor), [anchor])
  const visibleMultiDay = useMemo(() => showMultiDay ? multiDaySchedulesForStatus(calendarMultiDayRows, displayStatus) : [],
    [calendarMultiDayRows, displayStatus, showMultiDay])

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

  const loadMultiDay = useCallback(async () => {
    const request = ++requests.current.multiDay
    setMultiDayLoading(true); setMultiDayError(''); setMultiDayRows([]); setMultiDayTotal(0)
    try {
      const params = { pageNo: 1, pageSize: 100 }
      const first = await api.examCalendar.multiDayPage(params)
      if (request !== requests.current.multiDay) return
      const pages = Math.ceil(first.total / params.pageSize)
      const rest = pages > 1 ? await Promise.all(Array.from({ length: pages - 1 }, (_, index) =>
        api.examCalendar.multiDayPage({ ...params, pageNo: index + 2 }))) : []
      if (request === requests.current.multiDay) { setMultiDayRows([first, ...rest].flatMap(result => result.list)); setMultiDayTotal(first.total) }
    } catch (cause) {
      if (request !== requests.current.multiDay) return
      setMultiDayError(cause instanceof ApiError && cause.code === 403 ? '无权查看多日考试安排'
        : cause instanceof Error ? cause.message : '多日考期加载失败')
      setMultiDayRows([]); setMultiDayTotal(0)
    } finally { if (request === requests.current.multiDay) setMultiDayLoading(false) }
  }, [])

  const loadCalendarMultiDay = useCallback(async () => {
    const request = ++requests.current.calendarMultiDay
    setCalendarMultiDayLoading(true); setCalendarMultiDayError(''); setCalendarMultiDayRows([])
    try {
      const params = { pageNo: 1, pageSize: 100,
        rangeStart: range.start.format('YYYY-MM-DD'), rangeEnd: range.end.format('YYYY-MM-DD') }
      const first = await api.examCalendar.multiDayPage(params)
      if (request !== requests.current.calendarMultiDay) return
      const pages = Math.ceil(first.total / params.pageSize)
      const rest = pages > 1 ? await Promise.all(Array.from({ length: pages - 1 }, (_, index) =>
        api.examCalendar.multiDayPage({ ...params, pageNo: index + 2 }))) : []
      if (request === requests.current.calendarMultiDay) setCalendarMultiDayRows([first, ...rest].flatMap(result => result.list))
    } catch (cause) {
      if (request !== requests.current.calendarMultiDay) return
      setCalendarMultiDayRows([])
      setCalendarMultiDayError(cause instanceof ApiError && cause.code === 403 ? '无权查看多日考期'
        : cause instanceof Error ? cause.message : '多日考期加载失败')
    } finally { if (request === requests.current.calendarMultiDay) setCalendarMultiDayLoading(false) }
  }, [range.end, range.start])

  const latestReload = useRef({ load, loadMultiDay, multiDayOpen, loadCalendarMultiDay, showMultiDay })
  latestReload.current = { load, loadMultiDay, multiDayOpen, loadCalendarMultiDay, showMultiDay }
  const reloadVisible = () => {
    const current = latestReload.current
    return Promise.all([current.load(), current.multiDayOpen ? current.loadMultiDay() : Promise.resolve(),
      current.showMultiDay ? current.loadCalendarMultiDay() : Promise.resolve()])
  }

  useEffect(() => { void load(); return () => { ++requests.current.exact } }, [load])
  useEffect(() => { if (multiDayOpen) void loadMultiDay(); return () => { ++requests.current.multiDay } }, [loadMultiDay, multiDayOpen])
  useEffect(() => {
    if (showMultiDay) void loadCalendarMultiDay()
    return () => { ++requests.current.calendarMultiDay }
  }, [loadCalendarMultiDay, showMultiDay])

  const openCreate = (date = anchor, type: ExamScheduleType = 'EXACT') => {
    setEditing(undefined)
    form.resetFields()
    form.setFieldsValue({
      scheduleType: type, exactDate: type === 'EXACT' ? date : undefined,
      multiDayRange: undefined,
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
      multiDayRange: schedule.startDate && schedule.endDate
        ? [dayjs(schedule.startDate), dayjs(schedule.endDate)] : undefined,
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
      await reloadVisible()
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
      await reloadVisible()
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
        <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void reloadVisible()} />
        <Button icon={<EyeOutlined />} onClick={() => setMultiDayOpen(true)}>多日考试安排</Button>
        {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新增考期</Button>}
      </Space>
    </div>
    <div className="exam-calendar-toolbar">
      <Space wrap>
        <Space.Compact><Button icon={<LeftOutlined />} aria-label="上一月" onClick={() => setAnchor(value => value.subtract(1, 'month'))} /><Button onClick={() => setAnchor(dayjs())}>今天</Button><Button icon={<RightOutlined />} aria-label="下一月" onClick={() => setAnchor(value => value.add(1, 'month'))} /></Space.Compact>
        <DatePicker picker="month" aria-label="选择考期月份" allowClear={false} value={anchor} onChange={value => { if (value) setAnchor(value) }} />
      </Space>
      <Space wrap>
        <Space><Switch aria-label="显示多日考期" checked={showMultiDay} onChange={setShowMultiDay} /><span>显示多日考期</span></Space>
        <Select allowClear placeholder="状态" value={displayStatus} onChange={setDisplayStatus} options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))} className="exam-calendar-filter" />
      </Space>
    </div>
    <div className="exam-calendar-legend"><Tag>多日考试</Tag>连续日期条表示多日考试安排，包含开始日和结束日。</div>
    {showMultiDay && calendarMultiDayError && <Alert type="error" showIcon message={calendarMultiDayError}
      action={<Button onClick={() => void loadCalendarMultiDay()}>重试多日考期</Button>} />}
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />
      : <Spin spinning={loading || (showMultiDay && calendarMultiDayLoading)}>
        <ExamCalendarMonth anchor={anchor} exactRows={schedules} multiDayRows={visibleMultiDay}
          onDay={setDayDetail} onDetail={setDetail} renderStatus={status => <ScheduleStatus value={status} />} />
        {!loading && (!showMultiDay || (!calendarMultiDayLoading && !calendarMultiDayError))
          && !schedules.length && !visibleMultiDay.length && <Empty description="当前日历范围暂无符合条件的考期" />}
      </Spin>}

    <Drawer title="多日考试安排" width={520} open={multiDayOpen} onClose={() => setMultiDayOpen(false)} extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(anchor, 'MULTI_DAY')}>新增多日考期</Button>}>
      {multiDayError && <Alert type="error" showIcon message={multiDayError} action={<Button onClick={() => void loadMultiDay()}>重试</Button>} />}
      <Spin spinning={multiDayLoading}>{!multiDayError && (multiDayRows.length ? <List dataSource={multiDayRows} footer={multiDayTotal > multiDayRows.length ? `当前展示前 ${multiDayRows.length} 条，共 ${multiDayTotal} 条` : undefined} renderItem={item => <List.Item actions={[<Button type="link" key="detail" onClick={() => setDetail(item)}>详情</Button>, ...(canManage && item.recordStatus === 'DRAFT' ? [<Button type="link" key="edit" onClick={() => openEdit(item)}>编辑</Button>] : [])]}><List.Item.Meta title={<Space wrap><strong>{item.scheduleName || '未命名考期'}</strong><ScheduleStatus value={item.displayStatus} /></Space>} description={<><div>{item.startDate} 至 {item.endDate}</div><div>{item.remark || '无备注'}</div></>} /></List.Item>} /> : <Empty description="暂无多日考试安排" />)}</Spin>
    </Drawer>

    <Modal title={<Space wrap><span>考期详情</span>{detail && <ScheduleStatus value={detail.displayStatus} />}</Space>} open={Boolean(detail)} onCancel={() => setDetail(undefined)} footer={detail ? actions(detail) : null} destroyOnHidden>{detail && <ScheduleDetail schedule={detail} />}</Modal>
    <Modal title={`${dayDetail?.format('YYYY年M月D日')} 考期安排`} open={Boolean(dayDetail)} onCancel={() => setDayDetail(undefined)} footer={null} width="min(720px, calc(100vw - 32px))" destroyOnHidden>
      {dayDetail && <div className="exam-day-detail">
        <Typography.Title level={5}>当天单日考试</Typography.Title>
        {(() => {
          const rows = schedules.filter(row => coversExamDay(row, dayDetail.format('YYYY-MM-DD')))
          return rows.length ? <List dataSource={rows} renderItem={row => <List.Item actions={[<Button key="detail" type="link" onClick={() => setDetail(row)}>详情</Button>]}>
            <List.Item.Meta title={<Space wrap><strong>{row.scheduleName || '未命名考期'}</strong><ScheduleStatus value={row.displayStatus} /></Space>}
              description={row.remark} />
          </List.Item>} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当天暂无单日考试" />
        })()}
        {showMultiDay && <>
          <Typography.Title level={5}>当天多日考试</Typography.Title>
          <Typography.Paragraph type="secondary">以下考试安排包含当天。</Typography.Paragraph>
          {calendarMultiDayError ? <Alert type="error" showIcon message={calendarMultiDayError}
            action={<Button onClick={() => void loadCalendarMultiDay()}>重试多日考期</Button>} />
            : <Spin spinning={calendarMultiDayLoading}>{!calendarMultiDayLoading && (() => {
              const rows = visibleMultiDay.filter(row => coversExamDay(row, dayDetail.format('YYYY-MM-DD')))
              return rows.length ? <List dataSource={rows} renderItem={row => <List.Item actions={[<Button key="detail" type="link" onClick={() => setDetail(row)}>详情</Button>]}>
                <List.Item.Meta title={<Space wrap><strong>{row.scheduleName || '未命名考期'}</strong><Tag>多日</Tag><ScheduleStatus value={row.displayStatus} /></Space>}
                  description={<>{row.startDate} 至 {row.endDate}{row.remark && <div>{row.remark}</div>}</>} />
              </List.Item>} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当天暂无多日考期" />
            })()}</Spin>}
        </>}
      </div>}
    </Modal>
    <Modal title={editing ? '编辑考期' : '新增考期'} open={editorOpen} confirmLoading={saving} onCancel={() => setEditorOpen(false)} footer={<Space><Button onClick={() => setEditorOpen(false)} disabled={saving}>取消</Button><Button loading={saving && savingAction === 'DRAFT'} disabled={saving} onClick={() => void save()}>保存草稿</Button><Button type="primary" loading={saving && savingAction === 'PUBLISH'} disabled={saving} onClick={() => void save(true)}>保存并发布</Button></Space>} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ scheduleType: 'EXACT' }}>
        <Form.Item name="scheduleName" label="考期名称" rules={[{ required: true, whitespace: true, message: '请填写考期名称' }, { max: 100 }]}><Input maxLength={100} placeholder="请填写考期名称" /></Form.Item>
        <Form.Item name="scheduleType" label="时间类型" rules={[{ required: true }]}><Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 'EXACT', label: '单日' }, { value: 'MULTI_DAY', label: '多日' }]} /></Form.Item>
        {scheduleType === 'MULTI_DAY' ? <Form.Item name="multiDayRange" label="考试时间段" rules={[{ required: true, message: '请选择考试时间段' }]}><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item>
          : <Form.Item name="exactDate" label="日期" rules={[{ required: true, message: '请选择日期' }]}><DatePicker style={{ width: '100%' }} /></Form.Item>}
        <Form.Item name="remark" label="备注" rules={[{ max: 1000 }]}><Input.TextArea rows={4} showCount maxLength={1000} /></Form.Item>
      </Form>
    </Modal>
    {notifyTarget && <CalendarNotificationPanel key={notifyTarget.id} calendarType="EXAM" calendarId={notifyTarget.id} permissions={permissions} onClose={() => setNotifyTarget(undefined)} />}
  </section>
}
