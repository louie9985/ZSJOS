import { EditOutlined, PlusOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Empty, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Spin, Tag, Typography, message } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type DeliveryClass, type DeliveryClassExamOption, type HomeroomCandidate } from '../services/api'
import { formatTimestamp } from '../services/time'

type ClassForm = { className: string; examScheduleId: number; homeroomUserId: number }
const has = (permissions: string[], permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)
const CLASS_PAGE_SIZE = 12

export default function DeliveryClassPage({ permissions = [] }: { permissions?: string[]; manage?: boolean }) {
  const manage = has(permissions, 'zsjos:delivery-class:query-managed')
  const navigate = useNavigate()
  const [status, setStatus] = useState('SERVING')
  const [keyword, setKeyword] = useState('')
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
  const [exams, setExams] = useState<DeliveryClassExamOption[]>([])
  const [candidates, setCandidates] = useState<HomeroomCandidate[]>([])
  const [form] = Form.useForm<ClassForm>()
  const load = useCallback(async (targetPage = 1) => {
    if (loadingRef.current || (targetPage > 1 && !hasMore)) return
    loadingRef.current = true; setLoading(true); setError('')
    try {
      const page = await api.deliveryClasses.page({ pageNo: targetPage, pageSize: CLASS_PAGE_SIZE, status, keyword: keyword || undefined }, manage)
      setRows(current => targetPage === 1 ? page.list : [...current, ...page.list]); setTotal(page.total); setPageNo(targetPage)
      setHasMore(targetPage * CLASS_PAGE_SIZE < page.total)
    } catch (e) { setError(e instanceof Error ? e.message : '班级加载失败') }
    finally { loadingRef.current = false; setLoading(false) }
  }, [hasMore, keyword, manage, status])
  useEffect(() => { setRows([]); setPageNo(1); setHasMore(true); void load(1) }, [status, manage, keyword])
  useEffect(() => {
    const node = sentinelRef.current
    if (!node) return
    const observer = new IntersectionObserver(entries => { if (entries[0]?.isIntersecting && hasMore && !loadingRef.current) void load(pageNo + 1) })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, load, pageNo])
  const openEditor = async (row?: DeliveryClass) => {
    setEditing(row); setEditOpen(true); setSaving(true)
    try {
      const [examRows, candidateRows] = await Promise.all([api.deliveryClasses.exams(), api.deliveryClasses.candidates()])
      setExams(examRows); setCandidates(candidateRows)
      form.resetFields()
      form.setFieldsValue(row ? { className: row.className, examScheduleId: row.examScheduleId, homeroomUserId: row.homeroomUserId } : {})
    } catch (e) { message.error(e instanceof Error ? e.message : '创建班级所需数据加载失败，请重试'); setEditOpen(false) }
    finally { setSaving(false) }
  }
  const save = async () => {
    const value = await form.validateFields().catch(() => undefined)
    if (!value) return
    setSaving(true)
    try {
      const payload = { ...value, className: value.className.trim() }
      if (editing) await api.deliveryClasses.update(editing.id, { ...payload, version: editing.version })
      else await api.deliveryClasses.create(payload)
      message.success(editing ? '班级已更新' : '班级已创建'); setEditOpen(false); form.resetFields(); await load(1)
    } catch (e) { message.error(e instanceof Error ? e.message : '班级保存失败') }
    finally { setSaving(false) }
  }
  const complete = async (row: DeliveryClass) => {
    try { await api.deliveryClasses.complete(row.id); message.success('班级已结课'); await load(1) }
    catch (e) { message.error(e instanceof Error ? e.message : '结课失败') }
  }
  const orderedRows = [...rows].sort((left, right) => Number(right.systemClass) - Number(left.systemClass))
  return <section className="workspace-page">
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>班级管理</Typography.Title>
        <Space wrap>
          <Input.Search value={keyword} allowClear placeholder="班级名称 / 编号" onChange={e => setKeyword(e.target.value)} onSearch={() => void load(1)} />
          <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void load()} />
          {manage && has(permissions, 'zsjos:delivery-class:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void openEditor()}>创建班级</Button>}
        </Space>
      </Space>
      <Segmented value={status} onChange={value => setStatus(String(value))} options={[{ label: '服务中', value: 'SERVING' }, { label: '已结课', value: 'COMPLETED' }]} />
      {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>} />}
      {loading && rows.length === 0 ? <Spin /> : rows.length === 0 ? <Empty description="暂无班级" /> : <>
        <div className="delivery-class-grid">{orderedRows.map(row => <Card key={row.id} className={`delivery-class-card${row.systemClass ? ' delivery-class-pending' : ''}`} hoverable onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: row.id } })}>
          <div className="delivery-class-card-head"><Typography.Title level={5} ellipsis={{ tooltip: row.className }}>{row.className}</Typography.Title>{row.systemClass ? <Tag color="blue">系统班</Tag> : <Tag color={row.status === 'SERVING' ? 'green' : 'default'}>{row.status === 'SERVING' ? '服务中' : '已结课'}</Tag>}</div>
          <div className="delivery-class-card-meta"><span>{row.systemClass ? '当前状态' : '创建时间'}</span><strong>{row.systemClass ? '待分班' : row.createTime ? formatTimestamp(row.createTime) : '未记录'}</strong></div>
          <div className="delivery-class-card-meta"><span>{row.systemClass ? '学员人数' : '考期'}</span><strong>{row.systemClass ? `${row.studentCount} 名` : row.examScheduleSnapshot || row.exactDate || '未设置'}</strong></div>
          <div className="delivery-class-card-foot"><span>{row.systemClass ? '点击查看待分班学员' : `${row.studentCount} 名学员`}</span><span>{row.classNo}</span></div>
          {!row.systemClass && row.scheduleType === 'ROUGH' && <Alert type="warning" showIcon icon={<WarningOutlined />} message="未设置精确考期" />}
          {manage && !row.systemClass && <Space onClick={event => event.stopPropagation()}>{has(permissions, 'zsjos:delivery-class:update') && row.status === 'SERVING' && <Button type="text" icon={<EditOutlined />} aria-label="编辑班级" onClick={() => void openEditor(row)} />}{has(permissions, 'zsjos:delivery-class:complete') && row.status === 'SERVING' && <Popconfirm title="确认结课该班级？" onConfirm={() => void complete(row)}><Button type="link" danger>结课</Button></Popconfirm>}</Space>}
        </Card>)}</div>
      </>}
      <div ref={sentinelRef} className="delivery-class-sentinel">{loading && rows.length > 0 ? '加载中…' : hasMore ? '加载更多' : rows.length ? '已加载全部班级' : ''}</div>
    </Space>
    <Modal open={editOpen} title={editing ? '编辑班级' : '创建班级'} confirmLoading={saving} onOk={() => void save()} onCancel={() => setEditOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="className" label="班级名称" rules={[{ required: true, whitespace: true, message: '请填写班级名称' }]}><Input maxLength={100} placeholder="请填写班级名称" /></Form.Item>
        <Form.Item name="examScheduleId" label="考期" rules={[{ required: true, message: '请选择考期' }]}><Select showSearch optionFilterProp="label" loading={saving} options={exams.map(row => ({ label: row.displayName, value: row.id }))} notFoundContent="暂无已发布且未结束的考期" /></Form.Item>
        <Form.Item name="homeroomUserId" label="班主任" rules={[{ required: true, message: '请选择班主任' }]}><Select showSearch optionFilterProp="label" options={candidates.map(row => ({ label: `${row.name}${row.deptName ? ` · ${row.deptName}` : ''}`, value: row.id }))} /></Form.Item>
      </Form>
    </Modal>
  </section>
}
