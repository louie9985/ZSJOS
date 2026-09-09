import {
  CalendarOutlined, EditOutlined, EyeOutlined, LeftOutlined, PlusOutlined,
  ReloadOutlined, RightOutlined, SendOutlined, StopOutlined
} from '@ant-design/icons'
import {
  Alert, Button, Calendar, Cascader, DatePicker, Drawer, Empty, Form, Input, List, Modal,
  Popconfirm, Radio, Select, Space, Spin, Tag, Typography, message
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ApiError, api, type ExamCategoryOption, type ExamSchedule, type ExamScheduleInput,
  type ExamScheduleType
} from '../services/api'
import type { ExamProductOption } from '../services/api'
import ProductSpecs from '../components/ProductSpecs'
import { catalogSpecs, specText } from '../services/productSpecs'

type EditorValues = {
  scheduleType: ExamScheduleType
  exactDate?: Dayjs
  roughRange?: [Dayjs, Dayjs]
  categoryId?: number
  productId?: number
  productSelection?: number[]
  selectedAttrs?: Record<string, string>
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

export const scheduleInput = (values: EditorValues): ExamScheduleInput => values.scheduleType === 'EXACT'
  ? {
      scheduleType: 'EXACT', exactDate: values.exactDate?.format('YYYY-MM-DD'),
      categoryId: values.productId ? undefined : values.categoryId, productId: values.productId,
      selectedAttrs: values.productId ? Object.fromEntries(Object.entries(values.selectedAttrs || {}).filter(([, value]) => value != null && value !== '')) : undefined,
      remark: values.remark?.trim() || undefined
    }
  : {
      scheduleType: 'ROUGH', roughStartDate: values.roughRange?.[0].format('YYYY-MM-DD'),
      roughEndDate: values.roughRange?.[1].format('YYYY-MM-DD'),
      categoryId: values.productId ? undefined : values.categoryId, productId: values.productId,
      selectedAttrs: values.productId ? Object.fromEntries(Object.entries(values.selectedAttrs || {}).filter(([, value]) => value != null && value !== '')) : undefined,
      remark: values.remark?.trim() || undefined
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
    <div className="wide"><span>考期名称</span><strong>{schedule.scheduleName || schedule.categoryNameSnapshot}</strong></div>
    <div><span>时间类型</span><strong>{schedule.scheduleType === 'EXACT' ? '精确时间' : '粗略时间'}</strong></div>
    <div><span>考试时间</span><strong>{period}</strong></div>
    <div><span>产品分类</span><strong>{schedule.categoryPathSnapshot.map(item => item.name).join(' / ') || schedule.categoryNameSnapshot}</strong></div>
    <div><span>当前状态</span><ScheduleStatus value={schedule.displayStatus} /></div>
    <div className="wide"><span>备注</span><strong>{schedule.remark || '无'}</strong></div>
    {!!schedule.frozenSkus?.length && <div className="wide"><span>发布时适用 SKU（{schedule.frozenSkus.length}）</span>{schedule.frozenSkus.map(sku => <div key={sku.skuRef}><strong>{sku.skuName}</strong><ProductSpecs product={sku} /></div>)}</div>}
  </div>
}

export default function ExamCalendarPage({ permissions }: { permissions: string[] }) {
  const canManage = hasPermission(permissions, 'zsjos:exam-calendar:manage')
  const [anchor, setAnchor] = useState(dayjs())
  const [schedules, setSchedules] = useState<ExamSchedule[]>([])
  const [categories, setCategories] = useState<ExamCategoryOption[]>([])
  const [products, setProducts] = useState<ExamProductOption[]>([])
  const [productLoading, setProductLoading] = useState(false), [productError, setProductError] = useState('')
  const [dayDetail, setDayDetail] = useState<Dayjs>()
  const [categoryId, setCategoryId] = useState<number>()
  const [displayStatus, setDisplayStatus] = useState<string>()
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [referenceError, setReferenceError] = useState('')
  const [roughOpen, setRoughOpen] = useState(false), [roughLoading, setRoughLoading] = useState(false)
  const [roughRows, setRoughRows] = useState<ExamSchedule[]>([]), [roughTotal, setRoughTotal] = useState(0)
  const [roughError, setRoughError] = useState('')
  const [editorOpen, setEditorOpen] = useState(false), [editing, setEditing] = useState<ExamSchedule>()
  const [detail, setDetail] = useState<ExamSchedule>(), [saving, setSaving] = useState(false)
  const [form] = Form.useForm<EditorValues>()
  const requests = useRef({ exact: 0, rough: 0, products: 0, categories: 0 })
  const [selectedAttrs, setSelectedAttrs] = useState<Record<string, string>>({})
  const [clearedInvalidAttrs, setClearedInvalidAttrs] = useState<string[]>([])
  const scheduleType = Form.useWatch('scheduleType', form)
  const editorCategoryId = Form.useWatch('categoryId', form)
  const editorProductId = Form.useWatch('productId', form)
  const selectedProduct = products.find(p => p.productId === editorProductId)
  const invalidAttrs = Object.entries(selectedAttrs).filter(([key, value]) =>
    !selectedProduct?.attrs.some(attr => attr.attrKey === key && attr.values.some(option => option.value === value)))
  const selectedSpecs = catalogSpecs(selectedAttrs, selectedProduct?.attrs)
  const previewName = [selectedProduct?.productName || categories.find(c => c.id === editorCategoryId)?.name,
    ...selectedSpecs.map(specText)].filter(Boolean).join('，')
  const matchingSkus = selectedProduct?.skus.filter(sku => Object.entries(selectedAttrs)
    .every(([key, value]) => !value || sku.attrValues[key] === value)) || []
  const range = useMemo(() => ({ start: anchor.startOf('month'), end: anchor.endOf('month') }), [anchor])
  const categoryOptions = useMemo(() => categories.map(item => ({
    value: item.id, label: item.path.map(node => node.name).join(' / ')
  })), [categories])
  const productCascaderOptions = useMemo(() => {
    type Node = { value: number; label: string; children?: Node[]; isLeaf?: boolean }
    const roots: Node[] = []
    for (const product of products) {
      let level = roots
      product.categoryPath.forEach((category, index) => {
        let node = level.find(item => item.value === category.id)
        if (!node) {
          node = { value: category.id, label: category.name, children: [] }
          level.push(node)
        }
        level = node.children!
        if (index === product.categoryPath.length - 1) {
          level.push({ value: product.productId, label: product.productName, isLeaf: true })
        }
      })
    }
    return roots
  }, [products])

  const loadCategories = useCallback(async () => {
    const request = ++requests.current.categories
    setReferenceError('')
    try { const rows = await api.examCalendar.categoryOptions(); if (request === requests.current.categories) setCategories(rows) }
    catch (cause) { if (request === requests.current.categories) setReferenceError(cause instanceof Error ? cause.message : '产品分类加载失败') }
  }, [])

  const loadProducts = useCallback(async () => {
    const request = ++requests.current.products
    setProductLoading(true); setProductError('')
    try { const rows = await api.examCalendar.productOptions(); if (request === requests.current.products) setProducts(rows) }
    catch (cause) { if (request === requests.current.products) { setProducts([]); setProductError(cause instanceof Error ? cause.message : '产品加载失败') } }
    finally { if (request === requests.current.products) setProductLoading(false) }
  }, [])

  const load = useCallback(async () => {
    const request = ++requests.current.exact
    setLoading(true); setError(''); setSchedules([]); setDetail(undefined); setDayDetail(undefined)
    try {
      const params = {
        pageNo: 1, pageSize: 100,
        rangeStart: range.start.format('YYYY-MM-DD'), rangeEnd: range.end.format('YYYY-MM-DD'),
        categoryId, displayStatus
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
  }, [categoryId, displayStatus, range.end, range.start])

  const loadRough = useCallback(async () => {
    const request = ++requests.current.rough
    setRoughLoading(true); setRoughError(''); setRoughRows([]); setRoughTotal(0)
    try {
      const params = { pageNo: 1, pageSize: 100, categoryId }
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
  }, [categoryId])

  const latestReload = useRef({ load, loadRough, roughOpen })
  latestReload.current = { load, loadRough, roughOpen }

  useEffect(() => { void loadCategories(); return () => { ++requests.current.categories } }, [loadCategories])
  useEffect(() => { void load(); return () => { ++requests.current.exact } }, [load])
  useEffect(() => { if (roughOpen) void loadRough(); return () => { ++requests.current.rough } }, [loadRough, roughOpen])
  useEffect(() => { if (editorOpen && canManage) void loadProducts(); return () => { ++requests.current.products } }, [editorOpen, canManage, loadProducts])

  const openCreate = (date = anchor, type: ExamScheduleType = 'EXACT') => {
    setEditing(undefined)
    setSelectedAttrs({}); setClearedInvalidAttrs([])
    form.resetFields()
    form.setFieldsValue({
      scheduleType: type, exactDate: type === 'EXACT' ? date : undefined,
      roughRange: type === 'ROUGH' ? [date.startOf('month'), date.endOf('month')] : undefined,
      categoryId: undefined, productId: undefined, selectedAttrs: {}, remark: ''
    })
    setEditorOpen(true)
  }

  const openEdit = (schedule: ExamSchedule) => {
    setDetail(undefined); setEditing(schedule)
    setSelectedAttrs({ ...schedule.selectedAttrs }); setClearedInvalidAttrs([])
    form.resetFields()
    form.setFieldsValue({
      scheduleType: schedule.scheduleType,
      exactDate: schedule.exactDate ? dayjs(schedule.exactDate) : undefined,
      roughRange: schedule.roughStartDate && schedule.roughEndDate
        ? [dayjs(schedule.roughStartDate), dayjs(schedule.roughEndDate)] : undefined,
      categoryId: schedule.categoryId, productId: schedule.productId,
      productSelection: schedule.productId
        ? [...(products.find(product => product.productId === schedule.productId)?.categoryPath
          || schedule.categoryPathSnapshot || []).map(node => node.id), schedule.productId]
        : undefined,
      selectedAttrs: schedule.selectedAttrs || {}, remark: schedule.remark
    })
    setEditorOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    if (values.productId && invalidAttrs.length) {
      message.error('原规格条件已失效，请明确清除或替换后保存'); return
    }
    if (values.productId && (productLoading || productError || !matchingSkus.length)) {
      message.error(productError || '所选规格未匹配到有效 SKU，请重新选择'); return
    }
    setSaving(true)
    try {
      const input = { ...scheduleInput({ ...values, selectedAttrs }), clearedInvalidAttrs }
      if (editing) await api.examCalendar.update(editing.id, input)
      else await api.examCalendar.create(input)
      message.success(editing ? '考期已更新' : '考期草稿已创建')
      setEditorOpen(false)
      const current = latestReload.current
      await Promise.all([current.load(), current.roughOpen ? current.loadRough() : Promise.resolve()])
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '考期保存失败')
    } finally { setSaving(false) }
  }

  const changeProduct = (value: number | undefined) => {
    const previous = editorProductId
    form.setFieldValue('productId', previous)
    const apply = () => {
      form.setFieldValue('productId', value)
      form.setFieldValue('categoryId', value == null ? undefined : products.find(product => product.productId === value)?.categoryId)
      setSelectedAttrs({}); setClearedInvalidAttrs([])
    }
    if (Object.keys(selectedAttrs).length) {
      Modal.confirm({ title: '切换产品将清除原规格条件，确认继续？', onOk: apply,
        onCancel: () => form.setFieldValue('productId', previous) })
    } else apply()
  }

  const changeAttr = (key: string, value?: string) => {
    const apply = () => {
      setSelectedAttrs(current => { const next = { ...current }; if (value == null) delete next[key]; else next[key] = value; return next })
      if (value == null && invalidAttrs.some(([invalid]) => invalid === key)) {
        setClearedInvalidAttrs(current => [...new Set([...current, key])])
      }
    }
    if (value == null) Modal.confirm({ title: '清除规格条件可能扩大适用范围，确认清除？', onOk: apply })
    else apply()
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

  const actions = (schedule: ExamSchedule) => canManage ? <Space wrap>
    {schedule.recordStatus === 'DRAFT' && schedule.displayStatus !== 'ENDED' &&
      <Button icon={<EditOutlined />} onClick={() => openEdit(schedule)}>编辑</Button>}
    {schedule.recordStatus === 'DRAFT' &&
      <Button type="primary" icon={<SendOutlined />} onClick={() => void transition(schedule, 'publish')}>发布</Button>}
    {schedule.recordStatus === 'PUBLISHED' &&
      <Popconfirm title="撤销后不能重新发布，确认撤销？" onConfirm={() => void transition(schedule, 'revoke')}>
        <Button danger icon={<StopOutlined />}>撤销</Button>
      </Popconfirm>}
  </Space> : null

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
        <Select allowClear showSearch optionFilterProp="label" placeholder="产品分类" value={categoryId} onChange={setCategoryId} options={categoryOptions} className="exam-calendar-filter" />
        <Select allowClear placeholder="状态" value={displayStatus} onChange={setDisplayStatus} options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))} className="exam-calendar-filter" />
      </Space>
    </div>
    {referenceError && <Alert type="warning" showIcon message="产品分类加载失败" description={referenceError} action={<Button onClick={() => void loadCategories()}>重试</Button>} />}
    {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />
      : <Spin spinning={loading}><Calendar value={anchor} onPanelChange={setAnchor} onSelect={(date, info) => { if (info.source === 'date') setDayDetail(date) }} cellRender={(date, info) => {
        if (info.type !== 'date') return info.originNode
        const dayRows = schedules.filter(item => item.exactDate && dayjs(item.exactDate).isSame(date, 'day'))
        return <div className="exam-calendar-events">{dayRows.slice(0, 3).map(item => <button type="button" key={item.id} className={`exam-calendar-event tone-${item.displayStatus.toLowerCase()}`} onClick={event => { event.stopPropagation(); setDetail(item) }}><span>{item.productNameSnapshot || item.categoryNameSnapshot}<ProductSpecs product={{ specs: item.selectedSpecs }} /></span><ScheduleStatus value={item.displayStatus} /></button>)}{dayRows.length > 3 && <Button type="link" size="small" className="exam-calendar-overflow" onClick={event => { event.stopPropagation(); setDayDetail(date) }}>另有 {dayRows.length - 3} 条</Button>}</div>
      }} /></Spin>}

    <Drawer title="粗略考试时间" width={520} open={roughOpen} onClose={() => setRoughOpen(false)} extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(anchor, 'ROUGH')}>新增粗略考期</Button>}>
      {roughError && <Alert type="error" showIcon message={roughError} action={<Button onClick={() => void loadRough()}>重试</Button>} />}
      <Spin spinning={roughLoading}>{!roughError && (roughRows.length ? <List dataSource={roughRows} footer={roughTotal > roughRows.length ? `当前展示前 ${roughRows.length} 条，共 ${roughTotal} 条` : undefined} renderItem={item => <List.Item actions={[<Button type="link" key="detail" onClick={() => setDetail(item)}>详情</Button>, ...(canManage && item.recordStatus === 'DRAFT' ? [<Button type="link" key="edit" onClick={() => openEdit(item)}>编辑</Button>] : [])]}><List.Item.Meta title={<Space wrap><strong>{item.scheduleName || item.categoryNameSnapshot}</strong><ScheduleStatus value={item.recordStatus} /></Space>} description={<><div>{item.roughStartDate} 至 {item.roughEndDate}</div><div>{item.remark || '无备注'}</div></>} /></List.Item>} /> : <Empty description="暂无粗略考试时间" />)}</Spin>
    </Drawer>

    <Modal title="考期详情" open={Boolean(detail)} onCancel={() => setDetail(undefined)} footer={detail ? actions(detail) : null} destroyOnHidden>{detail && <ScheduleDetail schedule={detail} />}</Modal>
    <Modal title={`${dayDetail?.format('YYYY年M月D日')} 考期安排`} open={Boolean(dayDetail)} onCancel={() => setDayDetail(undefined)} footer={null} width="min(720px, calc(100vw - 32px))" destroyOnHidden>
      {dayDetail && (() => { const rows = schedules.filter(row => row.exactDate === dayDetail.format('YYYY-MM-DD')); return rows.length ? <List dataSource={rows} renderItem={row => <List.Item actions={[<Button key="detail" type="link" onClick={() => setDetail(row)}>详情</Button>]}><List.Item.Meta title={<Space wrap><strong>{row.scheduleName || row.categoryNameSnapshot}</strong><ScheduleStatus value={row.displayStatus} /></Space>} description={<><div>{row.productNameSnapshot || row.categoryNameSnapshot}<ProductSpecs product={{ specs: row.selectedSpecs }} /></div>{row.remark && <div>{row.remark}</div>}</>} /></List.Item>} /> : <Empty description="当天暂无考期安排" /> })()}
    </Modal>
    <Modal title={editing ? '编辑考期' : '新增考期'} open={editorOpen} confirmLoading={saving} onCancel={() => setEditorOpen(false)} onOk={() => void save()} okText="保存草稿" destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ scheduleType: 'EXACT' }}>
        {productError && <Alert type="error" showIcon message={productError} action={<Button onClick={() => void loadProducts()}>重试</Button>} />}
        <Form.Item name="productSelection" label="产品" rules={[{ required: true, message: '请选择产品' }]}><Cascader showSearch loading={productLoading} disabled={productLoading || !!productError} options={productCascaderOptions} placeholder="请选择产品" onChange={path => {
          const value = Array.from(path).at(-1) as number | undefined
          form.setFieldValue('productSelection', path)
          void changeProduct(value)
        }} /></Form.Item>
        {editorProductId && !selectedProduct && !productLoading && !productError && <Alert type="warning" message={`${editing?.productNameSnapshot || '原产品'}已不可用，请重新选择产品`} />}
        {selectedProduct?.attrs.map(attr => <Form.Item key={attr.attrKey} label={attr.attrName}><Select allowClear value={selectedAttrs[attr.attrKey!]} onChange={value => changeAttr(attr.attrKey!, value)} options={attr.values.map(v => ({ value: v.value, label: v.label }))} /></Form.Item>)}
        {!productLoading && invalidAttrs.map(([key, value]) => {
          const snapshot = editing?.selectedSpecs?.find(spec => spec.attrKey === key && spec.value === value)
          return <Alert key={key} type="warning" showIcon message={`${snapshot ? specText(snapshot) : `${key}：${value}`}（字段或选项已失效）`} action={<Button onClick={() => changeAttr(key)}>清除</Button>} />
        })}
        {selectedProduct && !productLoading && !matchingSkus.length && <Alert type="warning" message="所选规格未匹配到有效 SKU" />}
        {previewName && <Form.Item label="考期名称"><Typography.Text>{previewName}</Typography.Text></Form.Item>}
        <Form.Item name="scheduleType" label="时间类型" rules={[{ required: true }]}><Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 'EXACT', label: '精确时间' }, { value: 'ROUGH', label: '粗略时间' }]} /></Form.Item>
        {scheduleType === 'ROUGH' ? <Form.Item name="roughRange" label="考试时间段" rules={[{ required: true, message: '请选择考试时间段' }]}><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item>
          : <Form.Item name="exactDate" label="考试日期" rules={[{ required: true, message: '请选择考试日期' }]}><DatePicker style={{ width: '100%' }} /></Form.Item>}
        <Form.Item name="remark" label="备注" rules={[{ max: 1000 }]}><Input.TextArea rows={4} showCount maxLength={1000} /></Form.Item>
      </Form>
    </Modal>
  </section>
}
