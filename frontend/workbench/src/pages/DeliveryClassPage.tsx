import { EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, DatePicker, Empty, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Spin, Tag, Typography, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type DeliveryClass, type DeliveryClassFilterContext, type HomeroomCandidate } from '../services/api'
import DeliveryClassFilters, { defaultClassFilters, type ClassFilters } from './DeliveryClassFilters'
import DeliveryClassExamCalendar from './DeliveryClassExamCalendar'

type ClassForm = { className: string; startDate: Dayjs; homeroomUserId: number }
const has = (permissions: string[], permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)
const CLASS_PAGE_SIZE = 12

export default function DeliveryClassPage({ permissions = [] }: { permissions?: string[]; manage?: boolean }) {
  const manage = has(permissions, 'zsjos:delivery-class:query-managed')
  const navigate = useNavigate()
  const [status, setStatus] = useState('SERVING')
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [filters, setFilters] = useState<ClassFilters>(defaultClassFilters)
  const [context, setContext] = useState<DeliveryClassFilterContext>()
  const [contextLoading, setContextLoading] = useState(true)
  const [contextError, setContextError] = useState('')
  const canQuery = has(permissions, 'zsjos:delivery-class:query') || manage || has(permissions, 'zsjos:delivery-class:query-my')
  const requestId = useRef(0)
  const contextRequest = useRef(0)
  const failedPage = useRef(1)
  const [rows, setRows] = useState<DeliveryClass[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [hasMore, setHasMore] = useState(true)
  const loadingRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<DeliveryClass>()
  const [saving, setSaving] = useState(false)
  const [calendarOpen, setCalendarOpen] = useState(false)
  const [candidates, setCandidates] = useState<HomeroomCandidate[]>([])
  const [form] = Form.useForm<ClassForm>()
  useEffect(() => { const timer = window.setTimeout(() => setSearch(keyword.trim()), 300); return () => window.clearTimeout(timer) }, [keyword])
  const query = useMemo(() => ({ status: status || undefined, keyword: search || undefined, projectCategoryId: filters.projectCategoryId,
    homeroomUserId: filters.homeroomUserId, examDateStart: filters.dateRange?.[0], examDateEnd: filters.dateRange?.[1], excludeSystemClass: true }),
    [status, search, filters.projectCategoryId, filters.homeroomUserId, filters.dateRange])
  const activeQuery = useRef(query)
  activeQuery.current = query
  const loadContext = useCallback(async () => {
    const id = ++contextRequest.current
    if (!canQuery) { setContext(undefined); setContextLoading(false); return }
    setContextLoading(true); setContextError('')
    try { const data = await api.deliveryClasses.filterContext(manage); if (id === contextRequest.current) setContext(data) }
    catch (e) { if (id === contextRequest.current) { setContext(undefined); setContextError(e instanceof Error ? e.message : '筛选选项与待分班入口加载失败') } }
    finally { if (id === contextRequest.current) setContextLoading(false) }
  }, [canQuery, manage])
  useEffect(() => { setContext(undefined); void loadContext(); return () => { contextRequest.current++ } }, [loadContext])
  const load = useCallback(async (targetPage = 1) => {
    if (!canQuery || (targetPage > 1 && (loadingRef.current || !hasMore))) return
    const id = ++requestId.current
    loadingRef.current = true; setLoading(true); setError('')
    try {
      const page = await api.deliveryClasses.page({ ...query, pageNo: targetPage, pageSize: CLASS_PAGE_SIZE }, manage)
      if (id !== requestId.current || query !== activeQuery.current) return
      setRows(current => targetPage === 1 ? page.list : [...current, ...page.list]); setTotal(page.total); setPageNo(targetPage)
      setHasMore(targetPage * CLASS_PAGE_SIZE < page.total)
    } catch (e) { if (id === requestId.current && query === activeQuery.current) { failedPage.current = targetPage; setError(e instanceof Error ? e.message : '班级加载失败') } }
    finally { if (id === requestId.current) { loadingRef.current = false; setLoading(false) } }
  }, [canQuery, hasMore, manage, query])
  useEffect(() => {
    setRows([]); setTotal(0); setPageNo(1); setHasMore(true)
    if (canQuery) void load(1); else setLoading(false)
    return () => { requestId.current++; loadingRef.current = false }
  }, [query, manage, canQuery])
  useEffect(() => {
    const node = sentinelRef.current
    if (!node || error || rows.length === 0) return
    const observer = new IntersectionObserver(entries => { if (entries[0]?.isIntersecting && hasMore && !loadingRef.current) void load(pageNo + 1) })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, load, pageNo, error, rows.length])
  const openEditor = async (row?: DeliveryClass) => {
    setEditing(row); setEditOpen(true); setSaving(true)
    try {
      const candidateRows = await api.deliveryClasses.candidates()
      setCandidates(candidateRows)
      form.resetFields()
      form.setFieldsValue(row ? { className: row.className, startDate: row.startDate ? dayjs(row.startDate) : undefined,
        homeroomUserId: row.homeroomUserId } : {})
    } catch (e) { message.error(e instanceof Error ? e.message : '创建班级所需数据加载失败，请重试'); setEditOpen(false) }
    finally { setSaving(false) }
  }
  const save = async () => {
    const value = await form.validateFields().catch(() => undefined)
    if (!value) return
    setSaving(true)
    try {
      const payload = { ...value, className: value.className.trim(), startDate: value.startDate.format('YYYY-MM-DD') }
      if (editing) await api.deliveryClasses.update(editing.id, { ...payload, version: editing.version })
      else await api.deliveryClasses.create(payload)
      message.success(editing ? '班级已更新' : '班级已创建'); setEditOpen(false); form.resetFields(); await Promise.all([load(1), loadContext()])
    } catch (e) { message.error(e instanceof Error ? e.message : '班级保存失败') }
    finally { setSaving(false) }
  }
  const complete = async (row: DeliveryClass) => {
    try { await api.deliveryClasses.complete(row.id); message.success('班级已结课'); await Promise.all([load(1), loadContext()]) }
    catch (e) { message.error(e instanceof Error ? e.message : '结课失败') }
  }
  if (!canQuery) return <Alert type="warning" showIcon message="暂无班级查询权限" />
  const pending = context?.pendingClass
  const filtered = !!(search || filters.projectCategoryId || filters.homeroomUserId || filters.dateRange)
  return <section className="workspace-page">
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>班级管理</Typography.Title>
        <Space wrap>
          <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => { void load(); void loadContext() }} />
          {manage && has(permissions, 'zsjos:delivery-class:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void openEditor()}>创建班级</Button>}
        </Space>
      </Space>
      <DeliveryClassFilters value={filters} onChange={setFilters} context={context} loading={contextLoading} keyword={keyword} onKeyword={setKeyword}
        onSearch={() => { if (keyword.trim() === search) void load(1); else setSearch(keyword.trim()) }}
        onReset={() => { setFilters(defaultClassFilters); setKeyword(''); setSearch(''); setStatus('SERVING') }} />
      {contextError && <Alert type="error" showIcon message={contextError} action={<Button onClick={() => void loadContext()}>重试选项与待分班入口</Button>} />}
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Segmented value={status} onChange={value => setStatus(String(value))} options={[{ label: '服务中', value: 'SERVING' }, { label: '已结课', value: 'COMPLETED' }, { label: '全部', value: '' }]} />
        <Typography.Text type="secondary" aria-live="polite">{loading ? '查询中…' : error ? '查询未完成' : '共 ' + total + ' 个班级'}</Typography.Text>
      </Space>
      {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(failedPage.current)}>重试</Button>} />}
      {loading && rows.length === 0 ? <Spin /> : rows.length === 0 ? !error && <Empty description={filtered ? '暂无符合条件的班级' : '暂无班级'} /> : <>
        <div className="delivery-class-grid">{rows.map(row => <Card key={row.id} className={`delivery-class-card${row.systemClass ? ' delivery-class-pending' : ''}`} hoverable onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })}>
          <div className="delivery-class-card-head"><Typography.Title level={5} ellipsis={{ tooltip: row.className }}>{row.className}</Typography.Title>{row.systemClass ? <Tag color="blue">系统班</Tag> : <Tag color={row.status === 'SERVING' ? 'green' : 'default'}>{row.status === 'SERVING' ? '服务中' : '已结课'}</Tag>}</div>
          <Space wrap>{row.projects?.length ? row.projects.map(project => <Tag key={project.id} title={project.path}>{project.name}</Tag>) : <Typography.Text type="secondary">项目未记录</Typography.Text>}</Space>
          <div className="delivery-class-card-meta"><span>班主任</span><strong>{row.homeroomUserNameSnapshot || '未记录'}</strong></div>
          <div className="delivery-class-card-meta"><span>{row.systemClass ? '服务学员数' : '考期'}</span><strong>{row.systemClass ? `${row.studentCount} 条` : row.examScheduleSnapshot || row.exactDate || '未设置'}</strong></div>
          <div className="delivery-class-card-foot"><span>{row.systemClass ? '点击查看待分班学员' : `${row.studentCount} 条服务`}</span><span>{row.classNo}</span></div>
          {manage && !row.systemClass && <Space onClick={event => event.stopPropagation()}>{has(permissions, 'zsjos:delivery-class:update') && row.status === 'SERVING' && <Button type="text" icon={<EditOutlined />} aria-label="编辑班级" onClick={() => void openEditor(row)} />}{has(permissions, 'zsjos:delivery-class:complete') && row.status === 'SERVING' && <Popconfirm title="确认结课该班级？" onConfirm={() => void complete(row)}><Button type="link" danger>结课</Button></Popconfirm>}</Space>}
        </Card>)}</div>
      </>}
      <div ref={sentinelRef} className="delivery-class-sentinel">{loading && rows.length > 0 ? '加载中…' : hasMore ? '加载更多' : rows.length ? '已加载全部班级' : ''}</div>
      {contextLoading && !context && <Typography.Text type="secondary">筛选选项与待分班入口加载中…</Typography.Text>}
      {pending && <div className="delivery-class-pending-entry"><Space wrap><Typography.Text strong>待分班系统班</Typography.Text><Typography.Text type="secondary">{pending.studentCount} 条待分班服务 · 不计入筛选结果</Typography.Text></Space>
        <Button onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: pending.id } })}>查看待分班学员</Button></div>}
    </Space>
    <Modal open={editOpen} title={editing ? '编辑班级' : '创建班级'} confirmLoading={saving} onOk={() => void save()} onCancel={() => setEditOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="className" label="班级名称" rules={[{ required: true, whitespace: true, message: '请填写班级名称' }]}><Input maxLength={100} placeholder="请填写班级名称" /></Form.Item>
        <Form.Item name="startDate" label="考期日期" rules={[{ required: true, message: '请选择考期日期' }]}><DatePicker style={{ width: '100%' }} placeholder="选择日期" /></Form.Item>
        <Form.Item><Button onClick={() => setCalendarOpen(true)}>选择已有考期</Button></Form.Item>
        <Form.Item name="homeroomUserId" label="班主任" rules={[{ required: true, message: '请选择班主任' }]}><Select showSearch optionFilterProp="label" options={candidates.map(row => ({ label: `${row.name}${row.deptName ? ` · ${row.deptName}` : ''}`, value: row.id }))} /></Form.Item>
      </Form>
    </Modal>
    <DeliveryClassExamCalendar open={calendarOpen} initialDate={form.getFieldValue('startDate')?.format('YYYY-MM-DD')}
      onClose={() => setCalendarOpen(false)} onSelect={date => {
        form.setFieldsValue({ startDate: dayjs(date) }); setCalendarOpen(false)
      }} />
  </section>
}
