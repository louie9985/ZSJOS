import { EditOutlined, PlusOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Cascader, Empty, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Spin, Tag, Typography, message } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type DeliveryClass, type DeliveryClassCategoryOption, type DeliveryClassExamOption, type DeliveryClassProductOption, type HomeroomCandidate } from '../services/api'
import { formatTimestamp } from '../services/time'

type ClassForm = { className?: string; productId: number; productSelection?: number[]; selectedAttrs?: Record<string, string>; selectedSkuIds?: number[]; categoryId: number; examScheduleId: number; homeroomUserId: number }
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
  const [categories, setCategories] = useState<DeliveryClassCategoryOption[]>([])
  const [exams, setExams] = useState<DeliveryClassExamOption[]>([])
  const [candidates, setCandidates] = useState<HomeroomCandidate[]>([])
  const [products, setProducts] = useState<DeliveryClassProductOption[]>([])
  const [form] = Form.useForm<ClassForm>()
  const selectedProductId = Form.useWatch('productId', form)
  const selectedSkuIds = Form.useWatch('selectedSkuIds', form) || []
  const selectedProduct = products.find(row => row.productId === selectedProductId)
  const productCascaderOptions = useMemo(() => {
    type Node = { value: number; label: string; children?: Node[]; isLeaf?: boolean }
    const roots: Node[] = []
    for (const product of products) {
      const path = product.categoryPath || []
      if (!path.length) continue
      let level = roots
      path.forEach((category, index) => {
        let node = level.find(item => item.value === category.id)
        if (!node) {
          node = { value: category.id, label: category.name, children: [] }
          level.push(node)
        }
        level = node.children!
        if (index === path.length - 1) {
          if (!level.some(item => item.value === product.productId)) {
            level.push({ value: product.productId, label: product.productName, isLeaf: true })
          }
        }
      })
    }
    return roots
  }, [products])
  const normalizeAttrs = (attrs?: Record<string, string>) => Object.fromEntries(Object.entries(attrs || {}).filter(([, value]) => value != null && value !== ''))
  const loadExams = async (categoryId: number, productId?: number, attrs?: Record<string, string>, skuIds?: number[]) => {
    setExams(await api.deliveryClasses.exams(categoryId, productId, normalizeAttrs(attrs), skuIds))
  }

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
      const [categoryRows, productRows, candidateRows] = await Promise.all([api.deliveryClasses.categories(), api.deliveryClasses.products(), api.deliveryClasses.candidates()])
      setCategories(categoryRows); setProducts(productRows); setCandidates(candidateRows)
      const attrs = normalizeAttrs(row?.selectedAttrs)
      form.resetFields()
      const product = row?.productId ? productRows.find(item => item.productId === row.productId) : undefined
      form.setFieldsValue(row ? { className: row.className, productId: row.productId!, productSelection: (product?.categoryPath?.map(category => category.id) || [row.categoryId!]).concat(row.productId!), selectedAttrs: attrs, selectedSkuIds: row.selectedSkus?.map(sku => sku.id), categoryId: row.categoryId!, examScheduleId: row.examScheduleId!, homeroomUserId: row.homeroomUserId! } : {})
      if (row?.categoryId && row?.selectedSkus?.length) await loadExams(row.categoryId, row.productId, attrs, row.selectedSkus.map(sku => sku.id))
    } catch (e) { message.error(e instanceof Error ? e.message : '创建班级所需数据加载失败'); setEditOpen(false) }
    finally { setSaving(false) }
  }
  const productChanged = async (productId: number) => {
    const product = products.find(item => item.productId === productId)
    if (!product) return
    form.setFieldsValue({ categoryId: product.categoryId, selectedSkuIds: [], selectedAttrs: {}, examScheduleId: undefined })
    setExams([])
  }
  const skuChanged = async (skuIds: number[]) => {
    form.setFieldValue('examScheduleId', undefined)
    if (!selectedProduct || !skuIds.length) { setExams([]); return }
    try { await loadExams(selectedProduct.categoryId, selectedProduct.productId, {}, skuIds) }
    catch (e) { message.error(e instanceof Error ? e.message : '考期加载失败'); setExams([]) }
  }
  const categoryChanged = async (categoryId: number) => {
    form.setFieldValue('examScheduleId', undefined); setExams([])
    try { setExams(await api.deliveryClasses.exams(categoryId)) } catch (e) { message.error(e instanceof Error ? e.message : '考期加载失败') }
  }
  const save = async () => {
    const value = await form.validateFields();
    const selectedPath = value.productSelection || []
    const selectedPathProductId = selectedPath.length ? selectedPath[selectedPath.length - 1] : undefined
    if (selectedPathProductId && products.some(product => product.productId === selectedPathProductId)) {
      value.productId = selectedPathProductId
    }
    const selectedFormProduct = products.find(product => product.productId === value.productId)
    if (selectedFormProduct) value.categoryId = selectedFormProduct.categoryId
    const categoryId = value.categoryId || selectedProduct?.categoryId;
    if (!categoryId) { message.error('当前产品缺少产品分类，无法创建班级'); return }
    value.categoryId = categoryId;
    setSaving(true)
    try {
      value.selectedAttrs = {}
      const payload = { ...value }
      delete payload.productSelection
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
          <div className="delivery-class-card-meta"><span>{row.systemClass ? '学员人数' : '考期'}</span><strong>{row.systemClass ? `${row.studentCount} 名` : row.exactDate || row.examScheduleSnapshot || '未设置'}</strong></div>
          <div className="delivery-class-card-foot"><span>{row.systemClass ? '点击查看待分班学员' : `${row.studentCount} 名学员`}</span><span>{row.classNo}</span></div>
          {!row.systemClass && row.scheduleType === 'ROUGH' && <Alert type="warning" showIcon icon={<WarningOutlined />} message="未设置精确考期" />}
          {manage && !row.systemClass && <Space onClick={event => event.stopPropagation()}>{has(permissions, 'zsjos:delivery-class:update') && row.status === 'SERVING' && <Button type="text" icon={<EditOutlined />} aria-label="编辑班级" onClick={() => void openEditor(row)} />}{has(permissions, 'zsjos:delivery-class:complete') && row.status === 'SERVING' && <Popconfirm title="确认结课该班级？" onConfirm={() => void complete(row)}><Button type="link" danger>结课</Button></Popconfirm>}</Space>}
        </Card>)}</div>
      </>}
      <div ref={sentinelRef} className="delivery-class-sentinel">{loading && rows.length > 0 ? '加载中…' : hasMore ? '加载更多' : rows.length ? '已加载全部班级' : ''}</div>
    </Space>
    <Modal open={editOpen} title={editing ? '编辑班级' : '创建班级'} confirmLoading={saving} onOk={() => void save()} onCancel={() => setEditOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical"><Form.Item name="className" label="班级名称"><Input maxLength={100} placeholder="留空时按分类、考期和序号生成" /></Form.Item><Form.Item name="productSelection" label="产品" rules={[{ required: true, message: '请选择分类和产品' }]}><Cascader showSearch options={productCascaderOptions} changeOnSelect={false} placeholder="请选择产品" onChange={path => { const values = path as number[]; const productId = values[values.length - 1]; if (products.some(product => product.productId === productId)) { form.setFieldValue('productId', productId); void productChanged(productId) } }} /></Form.Item><Form.Item name="productId" hidden><Input /></Form.Item><Form.Item name="categoryId" hidden><Input /></Form.Item><Form.Item name="selectedSkuIds" label="SKU" rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一个 SKU' }]}><Select mode="multiple" showSearch optionFilterProp="label" disabled={!selectedProduct} options={(selectedProduct?.skus || []).map(row => ({ label: row.skuName, value: row.id }))} onChange={value => void skuChanged(value as number[])} /></Form.Item><Form.Item name="examScheduleId" label="考期" rules={[{ required: true }]}><Select disabled={!selectedProduct || !selectedSkuIds.length} options={exams.map(row => ({ label: row.displayName, value: row.id }))} /></Form.Item><Form.Item name="homeroomUserId" label="班主任" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={candidates.map(row => ({ label: `${row.name}${row.deptName ? ` · ${row.deptName}` : ''}`, value: row.id }))} /></Form.Item></Form>
    </Modal>
  </section>
}
