import CalendarSearch from '../components/CalendarSearch'
import { useCalendarHighlight } from '../components/useCalendarHighlight'
import { calendarSearchApi, searchPresentation, locationDate, CalendarResultExpired, findCalendarPage } from '../services/calendarSearch'
import { LinkedText, REMARK_LINK_HINT } from '../components/ResourceLink'
import CalendarSideNavigation, { moveCalendarMonth } from '../components/CalendarSideNavigation'
import CalendarNotificationPanel from '../components/CalendarNotificationPanel'
import ExamCalendarMonth from './ExamCalendarMonth'
import { examBackgroundStyle } from './examCalendarColor'
import { examToday, examHasEnded, examDateError, initialExamDate } from './examCalendarDates'
import ExamCalendarNotePanel from '../components/ExamCalendarNotePanel'
import ExamScheduleAttachments, { ExamAttachmentPicker } from '../components/ExamScheduleAttachments'
import { EXAM_ATTACHMENT_HINT, examAttachmentItems, examScheduleAttachments, type ExamAttachmentItem } from '../services/examScheduleAttachments'
import { uploadDeferredFiles } from '../services/deferredUpload'
import { isExamVisible, reeditCountdown } from '../services/examReedit'
import { createIdempotencyKey } from '../services/idempotency'
import { calendarWindow, coversExamDay, schedulesForRecordStatus } from './examCalendarLayout'
import {
  CalendarOutlined, EditOutlined, EyeOutlined, PlusOutlined,
  ReloadOutlined, SendOutlined, StopOutlined
} from '@ant-design/icons'
import {
  Alert, Button, ColorPicker, DatePicker, Drawer, Empty, Form, Input, List, Modal,
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
  backgroundColor?: string
}

const STATUS_META: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'blue' },
  REVOKED: { label: '已撤销', color: 'red' }
}

const hasPermission = (permissions: string[], value: string) =>
  permissions.includes('*:*:*') || permissions.includes(value)

export const scheduleStatusLabel = (status: string) => STATUS_META[status]?.label || status

export const scheduleInput = (values: EditorValues): ExamScheduleInput => {
  return values.scheduleType === 'EXACT'
  ? {
      scheduleType: 'EXACT', exactDate: values.exactDate?.format('YYYY-MM-DD'),
      scheduleName: values.scheduleName.trim(),
      ...(values.backgroundColor !== undefined ? { backgroundColor: values.backgroundColor } : {}),
      remark: values.remark?.trim() || undefined
    }
  : {
      scheduleType: 'MULTI_DAY', startDate: values.multiDayRange?.[0].format('YYYY-MM-DD'),
      endDate: values.multiDayRange?.[1].format('YYYY-MM-DD'),
      scheduleName: values.scheduleName.trim(),
      ...(values.backgroundColor !== undefined ? { backgroundColor: values.backgroundColor } : {}),
      remark: values.remark?.trim() || undefined
    }
}

export function ExamCalendarLegend() {
  return <div className="exam-calendar-legend" aria-label="考期颜色和类型说明">
    {['PUBLISHED', 'REVOKED', 'DRAFT'].map(status =>
      <span className="exam-calendar-legend-item" key={status}>
        <span aria-hidden="true" className={'exam-calendar-swatch exam-status-tone tone-' + status.toLowerCase()} />
        {STATUS_META[status].label}
      </span>)}
    <span className="exam-calendar-legend-item"><span aria-hidden="true" className="exam-calendar-swatch is-multi-day" />多日考试</span>
    <span>以上为默认底色，可在新增或编辑考期时自选。发布状态请查看考期安排；连续日期条包含开始日和结束日。</span>
  </div>
}

function ScheduleStatus({ value, countdown }: { value: string; countdown?: string }) {
  const meta = STATUS_META[value] || { label: value, color: 'default' }
  return <Tag color={meta.color} className={countdown ? 'exam-revoked-status' : undefined}
    title={meta.label + (countdown ? ' · ' + countdown : '')}>
    <span className="exam-status-label">{meta.label}{countdown && ' · '}</span>
    {countdown && <span className="exam-countdown">{countdown}</span>}
  </Tag>
}

export default function ExamCalendarPage({ permissions }: { permissions: string[] }) {
  const canManage = hasPermission(permissions, 'zsjos:exam-calendar:manage')
  const canNotify = hasPermission(permissions, 'zsjos:exam-calendar:notify')
  const [notifyTarget, setNotifyTarget] = useState<ExamSchedule>()
  const [anchor, setAnchor] = useState(() => dayjs(examToday()))
  const [schedules, setSchedules] = useState<ExamSchedule[]>([])
  const [dayDetail, setDayDetail] = useState<Dayjs>()
  const [recordStatus, setRecordStatus] = useState<ExamSchedule['recordStatus']>()
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [multiDayOpen, setMultiDayOpen] = useState(false), [multiDayLoading, setMultiDayLoading] = useState(false)
  const [multiDayRows, setMultiDayRows] = useState<ExamSchedule[]>([]), [multiDayTotal, setMultiDayTotal] = useState(0)
  const [multiDayError, setMultiDayError] = useState('')
  const [showMultiDay, setShowMultiDay] = useState(true)
  const [calendarMultiDayRows, setCalendarMultiDayRows] = useState<ExamSchedule[]>([])
  const [calendarMultiDayLoading, setCalendarMultiDayLoading] = useState(false)
  const [calendarMultiDayError, setCalendarMultiDayError] = useState('')
  const [editorOpen, setEditorOpen] = useState(false), [editing, setEditing] = useState<ExamSchedule>()
  const [saving, setSaving] = useState(false)
  const [attachments, setAttachments] = useState<ExamAttachmentItem[]>([])
  const attachmentsRef = useRef(attachments)
  attachmentsRef.current = attachments
  useEffect(() => () => { attachmentsRef.current.forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) }) }, [])
  useEffect(() => {
    if (!editorOpen) setAttachments(current => {
      current.forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) })
      return []
    })
  }, [editorOpen])
  const [now, setNow] = useState(() => performance.now())
  const [claimedIds, setClaimedIds] = useState<Set<number>>(() => new Set())
  const [reediting, setReediting] = useState(false)
  const [claiming, setClaiming] = useState(false)
  const [claimError, setClaimError] = useState('')
  const pendingClaim = useRef<{ id: number; key: string } | undefined>(undefined)
  const claimLock = useRef(false), saveLock = useRef(false)
  const savedEditorId = useRef<number | undefined>(undefined)
  const [savingAction, setSavingAction] = useState<'DRAFT' | 'PUBLISH'>('DRAFT')
  const [highlightedId, setHighlightedId] = useState<number>()
  const locatingDay = useRef<Dayjs | undefined>(undefined)
  useCalendarHighlight(highlightedId, schedules)
  const [form] = Form.useForm<EditorValues>()
  const requests = useRef({ exact: 0, multiDay: 0, calendarMultiDay: 0 })
  const scheduleType = Form.useWatch('scheduleType', form)
  const backgroundColor = Form.useWatch('backgroundColor', form)
  const range = useMemo(() => calendarWindow(anchor), [anchor])
  const isVisible = (row: ExamSchedule) => !claimedIds.has(row.id) && isExamVisible(row, now)
  const visibleSchedules = schedulesForRecordStatus(schedules.filter(isVisible), recordStatus)
  const visibleDrawerRows = multiDayRows.filter(isVisible)
  const visibleMultiDay = showMultiDay ? schedulesForRecordStatus(calendarMultiDayRows.filter(isVisible), recordStatus) : []
  const renderStatus = (row: ExamSchedule) => <ScheduleStatus value={row.recordStatus}
    countdown={row.recordStatus === 'REVOKED' ? reeditCountdown(row, now) : undefined} />

  const load = useCallback(async () => {
    const request = ++requests.current.exact
    setLoading(true); setError(''); setSchedules([]); if (!locatingDay.current) setDayDetail(undefined)
    try {
      const params = {
        pageNo: 1, pageSize: 100,
        rangeStart: range.start.format('YYYY-MM-DD'), rangeEnd: range.end.format('YYYY-MM-DD')
      }
      const first = await api.examCalendar.exactPage(params)
      if (request !== requests.current.exact) return
      const pages = Math.ceil(first.total / params.pageSize)
      const rest = pages > 1 ? await Promise.all(Array.from({ length: pages - 1 }, (_, index) =>
        api.examCalendar.exactPage({ ...params, pageNo: index + 2 }))) : []
      if (request === requests.current.exact) { setSchedules([first, ...rest].flatMap(result => result.list)); if (locatingDay.current) { setDayDetail(locatingDay.current); locatingDay.current = undefined } }
    } catch (cause) {
      if (request !== requests.current.exact) return
      setSchedules([])
      setError(cause instanceof ApiError && cause.code === 403 ? '无权查看考期日历'
        : cause instanceof Error ? cause.message : '考期日历加载失败')
    } finally { if (request === requests.current.exact) setLoading(false) }
  }, [range.end, range.start])

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
    savedEditorId.current = undefined
    setReediting(false)
    setEditing(undefined)
    form.resetFields()
    form.setFieldsValue({
      scheduleType: type, exactDate: type === 'EXACT' ? dayjs(initialExamDate(date.format('YYYY-MM-DD'))) : undefined,
      multiDayRange: undefined,
      scheduleName: '', remark: '', backgroundColor: ''
    })
    setEditorOpen(true)
  }

  const openEdit = (schedule: ExamSchedule) => {
    setAttachments(examAttachmentItems(schedule.attachments))
    savedEditorId.current = schedule.id
    setReediting(false)
    setEditing(schedule)
    form.resetFields()
    form.setFieldsValue({
      scheduleType: schedule.scheduleType,
      exactDate: schedule.exactDate ? dayjs(schedule.exactDate) : undefined,
      multiDayRange: schedule.startDate && schedule.endDate
        ? [dayjs(schedule.startDate), dayjs(schedule.endDate)] : undefined,
      scheduleName: schedule.scheduleName || '', remark: schedule.remark, backgroundColor: schedule.backgroundColor || ''
    })
    setEditorOpen(true)
  }

  const save = async (publishAfterSave = false) => {
    if (saveLock.current) return
    saveLock.current = true
    const values = await form.validateFields().catch(() => undefined)
    if (!values) { saveLock.current = false; return }
    const input = scheduleInput(values)
    const dateError = input.scheduleType === 'EXACT'
      ? examDateError(input.exactDate) : examDateError(input.startDate, input.endDate)
    if (dateError) { message.error(dateError); saveLock.current = false; return }
    setSaving(true); setSavingAction(publishAfterSave ? 'PUBLISH' : 'DRAFT')
    let draftSaved = false
    try {
      const uploaded = await uploadDeferredFiles(attachments, examScheduleAttachments.upload, setAttachments)
      if (uploaded.failed) { message.error('有附件上传失败，请移除失败项或再次保存重试'); return }
      const request = { ...input, attachmentIds: uploaded.items.map(item => item.uploaded!.fileId) }
      const savedId = savedEditorId.current != null
        ? (await api.examCalendar.update(savedEditorId.current, request), savedEditorId.current)
        : await api.examCalendar.create(request)
      // The committed draft must survive a failed subsequent publish request.
      savedEditorId.current = savedId
      draftSaved = true
      setReediting(false)
      if (publishAfterSave) {
        await api.examCalendar.publish(savedId)
        message.success('考期已保存并发布')
      } else message.success(editing ? '考期已更新' : '考期草稿已创建')
      setEditorOpen(false)
      await reloadVisible()
    } catch (cause) {
      const reason = cause instanceof Error ? cause.message : '请重试'
      message.error(draftSaved && publishAfterSave ? `草稿已保存，发布未成功：${reason}` : reason)
    } finally { saveLock.current = false; setSaving(false); setSavingAction('DRAFT') }
  }

  const closeEditor = () => {
    if (saveLock.current) return
    if (reediting) Modal.confirm({ title: '放弃本次编辑？原考期不会恢复显示',
      content: '尚未保存的内容将被放弃。', okText: '放弃编辑', cancelText: '继续编辑',
      onOk: () => { setEditorOpen(false); setReediting(false) } })
    else setEditorOpen(false)
  }

  const reedit = async (id: number) => {
    if (claimLock.current) return
    claimLock.current = true; setClaiming(true); setClaimError('')
    if (pendingClaim.current?.id !== id) pendingClaim.current = { id, key: createIdempotencyKey() }
    try {
      const content = await api.examCalendar.reedit(id, pendingClaim.current.key)
      setClaimedIds(previous => new Set([...previous, id]))
      setEditing(undefined); savedEditorId.current = undefined; setReediting(true)
      form.resetFields()
      form.setFieldsValue({ scheduleType: content.scheduleType, scheduleName: content.scheduleName,
        exactDate: content.exactDate ? dayjs(content.exactDate) : undefined,
        multiDayRange: content.startDate && content.endDate ? [dayjs(content.startDate), dayjs(content.endDate)] : undefined,
        remark: content.remark, backgroundColor: content.backgroundColor || '' })
      setAttachments(examAttachmentItems(content.attachments))
      setDayDetail(undefined); setEditorOpen(true); pendingClaim.current = undefined
    } catch (cause) {
      const text = cause instanceof Error ? cause.message : '重新编辑失败'
      if (cause instanceof ApiError && [403, 1900018001, 1900018005, 1900018006, 1900018011, 1900018012, 1900018013].includes(cause.code)) {
        pendingClaim.current = undefined
        message.error(text)
        void reloadVisible()
      } else setClaimError(text + '。可重试取回内容，不会重复领取。')
    } finally { claimLock.current = false; setClaiming(false) }
  }

  useEffect(() => {
    const timer = window.setInterval(() => setNow(performance.now()), 250)
    const resume = () => { if (document.visibilityState === 'visible') { setNow(performance.now()); void reloadVisible() } }
    const leaving = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    document.addEventListener('visibilitychange', resume)
    if (reediting && editorOpen) window.addEventListener('beforeunload', leaving)
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', resume); window.removeEventListener('beforeunload', leaving) }
  }, [reediting, editorOpen])

  const transition = async (schedule: ExamSchedule, action: 'publish' | 'revoke') => {
    try {
      if (action === 'publish') await api.examCalendar.publish(schedule.id)
      else await api.examCalendar.revoke(schedule.id)
      message.success(action === 'publish' ? '考期已发布' : '考期已撤销')
      await reloadVisible()
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message
        : action === 'publish' ? '考期发布失败' : '考期撤销失败')
    }
  }

  const actions = (schedule: ExamSchedule) => <Space wrap>
    {canManage && schedule.canReedit && schedule.recordStatus === 'REVOKED' && isVisible(schedule) &&
      <Button icon={<EditOutlined />} loading={claiming && pendingClaim.current?.id === schedule.id}
        disabled={claiming || Boolean(claimError)} onClick={() => void reedit(schedule.id)}>重新编辑</Button>}
    {canManage && schedule.recordStatus === 'DRAFT' &&
      <Button icon={<EditOutlined />} onClick={() => openEdit(schedule)}>编辑</Button>}
    {canManage && schedule.recordStatus === 'DRAFT' &&
      <Button type="primary" disabled={examHasEnded(schedule)} icon={<SendOutlined />} onClick={() => void transition(schedule, 'publish')}>发布</Button>}
    {canManage && schedule.recordStatus === 'DRAFT' && examHasEnded(schedule) &&
      <span>考期已过期，请先编辑日期</span>}
    {canNotify && schedule.recordStatus === 'PUBLISHED' &&
      <Button onClick={() => setNotifyTarget(schedule)}>发送通知</Button>}
    {canManage && schedule.recordStatus === 'PUBLISHED' &&
      <Popconfirm title="撤销后五分钟内可重新编辑，超时将从日历移除，确认撤销？" onConfirm={() => void transition(schedule, 'revoke')}>
        <Button danger icon={<StopOutlined />}>撤销</Button>
      </Popconfirm>}
  </Space>

  return <section className="workspace-page exam-calendar-page">
    <div className="page-heading">
      <div><Typography.Title level={4}><CalendarOutlined /> 考期日历</Typography.Title><Typography.Text type="secondary">{anchor.format('YYYY年M月')}</Typography.Text></div>
      <Space wrap>
        <CalendarSearch scopeKey={recordStatus || ''} scopeLabel={recordStatus ? `发布状态：${STATUS_META[recordStatus].label}；包含单日和多日考期` : '当前可见的单日和多日考期'}
          search={(query, signal) => calendarSearchApi.exam(query, recordStatus, signal)} present={searchPresentation.exam}
          onLocate={async (row, signal) => {
            const item = searchPresentation.exam(row); const date = dayjs(locationDate(item.start, item.end))
            const params = { rangeStart: date.format('YYYY-MM-DD'), rangeEnd: date.format('YYYY-MM-DD'), pageSize: 100 }
            const fresh = await findCalendarPage(pageNo => row.scheduleType === 'MULTI_DAY' ? api.examCalendar.multiDayPage({ ...params, pageNo }) : api.examCalendar.exactPage({ ...params, pageNo }), item => item.id === row.id, signal)
            const target = fresh.list.find(item => item.id === row.id)!
            if (recordStatus && target.recordStatus !== recordStatus) throw new CalendarResultExpired()
            setHighlightedId(row.id); locatingDay.current = date; setAnchor(date); setDayDetail(date)
            if (row.scheduleType === 'MULTI_DAY') { setShowMultiDay(true); setCalendarMultiDayRows(fresh.accumulated) } else setSchedules(fresh.accumulated)
          }} />
        <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void reloadVisible()} />
        <Button icon={<EyeOutlined />} onClick={() => setMultiDayOpen(true)}>多日考试安排</Button>
        {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新增考期</Button>}
      </Space>
    </div>
    <div className="exam-calendar-toolbar">
      <Space wrap>
        <Button onClick={() => setAnchor(dayjs(examToday()))}>今天</Button>
        <DatePicker picker="month" aria-label="选择考期月份" allowClear={false} value={anchor} onChange={value => { if (value) setAnchor(value) }} />
      </Space>
      <Space wrap>
        <Space><Switch aria-label="显示多日考期" checked={showMultiDay} onChange={setShowMultiDay} /><span>显示多日考期</span></Space>
        <Select allowClear aria-label="发布状态" placeholder="发布状态" value={recordStatus} onChange={value => { setRecordStatus(value); setDayDetail(undefined) }} options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))} className="exam-calendar-filter" />
      </Space>
    </div>
    <ExamCalendarLegend />
    {claimError && <Alert type="error" showIcon message={claimError}
      action={<Button loading={claiming} onClick={() => { if (pendingClaim.current) void reedit(pendingClaim.current.id) }}>重试取回</Button>} />}
    {showMultiDay && calendarMultiDayError && <Alert type="error" showIcon message={calendarMultiDayError}
      action={<Button onClick={() => void loadCalendarMultiDay()}>重试多日考期</Button>} />}
    <div className="exam-note-layout"><div className="exam-note-calendar">
    <CalendarSideNavigation onNavigate={direction => { setDayDetail(undefined); setAnchor(value => moveCalendarMonth(value, direction)) }}>
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />
      : <Spin spinning={loading || (showMultiDay && calendarMultiDayLoading)}>
        <ExamCalendarMonth anchor={anchor} exactRows={visibleSchedules} multiDayRows={visibleMultiDay}
          onDay={setDayDetail} statusLabel={scheduleStatusLabel} />
        {!loading && (!showMultiDay || (!calendarMultiDayLoading && !calendarMultiDayError))
          && !visibleSchedules.length && !visibleMultiDay.length && <Empty description="当前日历范围暂无符合条件的考期" />}
      </Spin>}
    </CalendarSideNavigation>
    </div><ExamCalendarNotePanel canRead={hasPermission(permissions, 'zsjos:exam-calendar:query')} canManage={canManage} /></div>

    <Drawer title="多日考试安排" width={520} open={multiDayOpen} onClose={() => setMultiDayOpen(false)} extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(anchor, 'MULTI_DAY')}>新增多日考期</Button>}>
      {multiDayError && <Alert type="error" showIcon message={multiDayError} action={<Button onClick={() => void loadMultiDay()}>重试</Button>} />}
      <Spin spinning={multiDayLoading}>{!multiDayError && (visibleDrawerRows.length ? <List dataSource={visibleDrawerRows} footer={multiDayTotal > multiDayRows.length ? `当前展示前 ${visibleDrawerRows.length} 条，共 ${multiDayTotal - (multiDayRows.length - visibleDrawerRows.length)} 条` : undefined} renderItem={item => <List.Item className={item.attachments?.length ? "exam-schedule-attachment-record" : undefined} extra={actions(item)}><List.Item.Meta title={<Space wrap><strong>{item.scheduleName || '未命名考期'}</strong>{renderStatus(item)}</Space>} description={<><div>{item.startDate} 至 {item.endDate}</div><div><LinkedText text={item.remark || '无备注'} mode="remark" /></div><ExamScheduleAttachments scheduleId={item.id} files={item.attachments} /></>} /></List.Item>} /> : <Empty description="暂无多日考试安排" />)}</Spin>
    </Drawer>

    <Modal title={`${dayDetail?.format('YYYY年M月D日')} 考期安排`} open={Boolean(dayDetail)} onCancel={() => setDayDetail(undefined)} footer={null} width="min(720px, calc(100vw - 32px))" destroyOnHidden>
      {dayDetail && <div className="exam-day-detail">
        <Typography.Title level={5}>当天单日考试</Typography.Title>
        {(() => {
          const rows = visibleSchedules.filter(row => coversExamDay(row, dayDetail.format('YYYY-MM-DD')))
          return rows.length ? <List dataSource={rows} renderItem={row => <List.Item data-calendar-located={row.id === highlightedId} className={row.attachments?.length ? "exam-schedule-attachment-record" : undefined} extra={actions(row)}>
            <List.Item.Meta title={<Space wrap><strong>{row.scheduleName || '未命名考期'}</strong>{renderStatus(row)}</Space>}
              description={<><div>时间：{row.exactDate}</div><div>备注：<LinkedText text={row.remark || "无"} mode="remark" /></div><ExamScheduleAttachments scheduleId={row.id} files={row.attachments} /></>} />
          </List.Item>} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当天暂无单日考试" />
        })()}
        {showMultiDay && <>
          <Typography.Title level={5}>当天多日考试</Typography.Title>
          <Typography.Paragraph type="secondary">以下考试安排包含当天。</Typography.Paragraph>
          {calendarMultiDayError ? <Alert type="error" showIcon message={calendarMultiDayError}
            action={<Button onClick={() => void loadCalendarMultiDay()}>重试多日考期</Button>} />
            : <Spin spinning={calendarMultiDayLoading}>{!calendarMultiDayLoading && (() => {
              const rows = visibleMultiDay.filter(row => coversExamDay(row, dayDetail.format('YYYY-MM-DD')))
              return rows.length ? <List dataSource={rows} renderItem={row => <List.Item data-calendar-located={row.id === highlightedId} className={row.attachments?.length ? "exam-schedule-attachment-record" : undefined} extra={actions(row)}>
                <List.Item.Meta title={<Space wrap><strong>{row.scheduleName || '未命名考期'}</strong><Tag>多日</Tag>{renderStatus(row)}</Space>}
                  description={<><div>时间：{row.startDate} 至 {row.endDate}</div><div>备注：<LinkedText text={row.remark || "无"} mode="remark" /></div><ExamScheduleAttachments scheduleId={row.id} files={row.attachments} /></>} />
              </List.Item>} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当天暂无多日考期" />
            })()}</Spin>}
        </>}
      </div>}
    </Modal>
    <Modal title={reediting ? '重新编辑考期' : editing ? '编辑考期' : '新增考期'} open={editorOpen} confirmLoading={saving} onCancel={closeEditor} maskClosable={false} footer={<Space><Button onClick={closeEditor} disabled={saving}>取消</Button><Button loading={saving && savingAction === 'DRAFT'} disabled={saving} onClick={() => void save()}>保存草稿</Button><Button type="primary" loading={saving && savingAction === 'PUBLISH'} disabled={saving} onClick={() => void save(true)}>保存并发布</Button></Space>} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ scheduleType: 'EXACT' }} disabled={saving}>
        {editing && examHasEnded(editing) && <Alert type="warning" showIcon title="考期已过期，请先编辑日期；原日期保留供核对。" />}
        <Form.Item name="scheduleName" label="考期名称" rules={[{ required: true, whitespace: true, message: '请填写考期名称' }, { max: 100 }]}><Input maxLength={100} placeholder="请填写考期名称" /></Form.Item>
        <Form.Item name="scheduleType" label="时间类型" rules={[{ required: true }]}><Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 'EXACT', label: '单日' }, { value: 'MULTI_DAY', label: '多日' }]} /></Form.Item>
        {scheduleType === 'MULTI_DAY' ? <Form.Item name="multiDayRange" label="考试时间段" extra="开始日期可在过去，结束日期不得早于北京时间今天。" rules={[{ required: true, message: '请选择考试时间段' }, { validator: (_, value: Dayjs[] | undefined) => {
          const error = !value?.[0] || !value?.[1] ? '请选择完整的考期日期'
            : examDateError(value[0].format('YYYY-MM-DD'), value[1].format('YYYY-MM-DD'))
          return error ? Promise.reject(new Error(error)) : Promise.resolve()
        } }]}><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item>
          : <Form.Item name="exactDate" label="日期" rules={[{ required: true, message: '请选择日期' }, { validator: (_, value: Dayjs | undefined) => {
            const error = examDateError(value?.format('YYYY-MM-DD'))
            return error ? Promise.reject(new Error(error)) : Promise.resolve()
          } }]}><DatePicker disabledDate={date => date.format('YYYY-MM-DD') < examToday()} style={{ width: '100%' }} /></Form.Item>}
        <Form.Item name="backgroundColor" label="考期底色" extra="单日和多日考试条使用相同底色；清除后恢复默认配色。"
          getValueProps={(value: string) => ({ value: value || null })}
          getValueFromEvent={(color: { cleared: boolean; toHexString: () => string }) => color.cleared ? '' : color.toHexString()}>
          <ColorPicker format="hex" disabledFormat disabledAlpha allowClear>
            <Button aria-label="选择考期底色" style={examBackgroundStyle(backgroundColor)}>{backgroundColor || '默认底色'}</Button>
          </ColorPicker>
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(before, after) => before.backgroundColor !== after.backgroundColor || before.scheduleName !== after.scheduleName}>
          {({ getFieldValue }) => <div className="exam-calendar-events" aria-label="考期底色预览">
            <div className="exam-calendar-event exam-status-tone tone-draft" style={examBackgroundStyle(getFieldValue('backgroundColor'))}>
              {getFieldValue('scheduleName') || '考期底色预览'}
            </div>
          </div>}
        </Form.Item>
        <Form.Item name="remark" label="备注" className="remark-link-field" extra={REMARK_LINK_HINT} rules={[{ max: 1000 }]}><Input.TextArea rows={4} showCount maxLength={1000} /></Form.Item>
        <Form.Item label="备注附件" extra={EXAM_ATTACHMENT_HINT}>
          <ExamAttachmentPicker value={attachments} onChange={setAttachments} disabled={saving} />
        </Form.Item>
      </Form>
    </Modal>
    {notifyTarget && <CalendarNotificationPanel key={notifyTarget.id} calendarType="EXAM" calendarId={notifyTarget.id} permissions={permissions} onClose={() => setNotifyTarget(undefined)} />}
  </section>
}
