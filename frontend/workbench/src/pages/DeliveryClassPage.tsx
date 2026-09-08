import { EditOutlined, PlusOutlined, ReloadOutlined, SwapOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Table, Tag, Typography, message } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type ClassTransfer, type DeliveryClass, type DeliveryClassCategoryOption, type DeliveryClassExamOption, type DeliveryClassOption, type DeliveryClassProductOption, type DeliveryClassStudent, type HomeroomCandidate } from '../services/api'

type ClassForm = { className?: string; productId: number; selectedSkuIds?: number[]; categoryId: number; examScheduleId: number; homeroomUserId: number }
type TransferForm = { targetClassId: number; reason: string }
const has = (permissions: string[], permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)
const STUDENT_PAGE_SIZE = 20

export default function DeliveryClassPage({ permissions = [], manage = false }: { permissions?: string[]; manage?: boolean }) {
  const navigate = useNavigate()
  const [status, setStatus] = useState('SERVING')
  const [keyword, setKeyword] = useState('')
  const [rows, setRows] = useState<DeliveryClass[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [selected, setSelected] = useState<DeliveryClass>()
  const [students, setStudents] = useState<DeliveryClassStudent[]>([])
  const [studentPageNo, setStudentPageNo] = useState(1)
  const [studentTotal, setStudentTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<DeliveryClass>()
  const [saving, setSaving] = useState(false)
  const [categories, setCategories] = useState<DeliveryClassCategoryOption[]>([])
  const [exams, setExams] = useState<DeliveryClassExamOption[]>([])
  const [candidates, setCandidates] = useState<HomeroomCandidate[]>([])
  const [products, setProducts] = useState<DeliveryClassProductOption[]>([])
  const [transfer, setTransfer] = useState<DeliveryClassStudent>()
  const [transferOptions, setTransferOptions] = useState<DeliveryClassOption[]>([])
  const [transferLoading, setTransferLoading] = useState(false)
  const [transferError, setTransferError] = useState('')
  const [transferRows, setTransferRows] = useState<ClassTransfer[]>([])
  const [form] = Form.useForm<ClassForm>()
  const [transferForm] = Form.useForm<TransferForm>()
  const canQueryTransfers = !manage && has(permissions, 'zsjos:class-transfer:query')

  const load = useCallback(async (targetPage = pageNo) => {
    setLoading(true); setError('')
    try {
      const page = await api.deliveryClasses.page({ pageNo: targetPage, pageSize: 10, status, keyword: keyword || undefined }, manage)
      setRows(page.list); setTotal(page.total); setPageNo(targetPage)
    } catch (e) { setError(e instanceof Error ? e.message : '班级加载失败') }
    finally { setLoading(false) }
  }, [keyword, manage, pageNo, status])
  useEffect(() => { void load(1) }, [status, manage])
  useEffect(() => {
    if (!canQueryTransfers) return
    void api.deliveryClasses.transferPage({ pageNo: 1, pageSize: 20 })
      .then(page => setTransferRows(page.list))
      .catch(e => message.error(e instanceof Error ? e.message : '调班申请加载失败'))
  }, [canQueryTransfers])

  const loadStudents = async (row: DeliveryClass, targetPage = 1) => {
    setDetailLoading(true); setDetailError('')
    try {
      const page = await api.deliveryClasses.students(row.id, targetPage, STUDENT_PAGE_SIZE)
      setStudents(page.list); setStudentTotal(page.total); setStudentPageNo(targetPage)
    }
    catch (e) {
      setStudents([]); setStudentTotal(0)
      setDetailError(e instanceof Error ? e.message : '班级学员加载失败')
    }
    finally { setDetailLoading(false) }
  }
  const open = async (row: DeliveryClass) => {
    setSelected(row); setStudents([]); setStudentPageNo(1); setStudentTotal(0)
    await loadStudents(row, 1)
  }
  const openEditor = async (row?: DeliveryClass) => {
    setEditing(row); setEditOpen(true); setSaving(true)
    try {
      const [categoryRows, productRows, candidateRows] = await Promise.all([api.deliveryClasses.categories(), api.deliveryClasses.products(), api.deliveryClasses.candidates()])
      setCategories(categoryRows); setProducts(productRows); setCandidates(candidateRows)
      if (row?.categoryId) setExams(await api.deliveryClasses.exams(row.categoryId, row.productId))
      form.setFieldsValue(row ? { className: row.className, productId: row.productId!, selectedSkuIds: row.selectedSkus?.map(sku => sku.id), categoryId: row.categoryId!, examScheduleId: row.examScheduleId!, homeroomUserId: row.homeroomUserId! } : {})
    } catch (e) { message.error(e instanceof Error ? e.message : '创建班级所需数据加载失败'); setEditOpen(false) }
    finally { setSaving(false) }
  }
  const productChanged = async (productId: number) => {
    const product = products.find(item => item.productId === productId)
    if (!product) return
    form.setFieldsValue({ categoryId: product.categoryId, selectedSkuIds: product.skus.map(sku => sku.id), selectedAttrs: {}, examScheduleId: undefined })
    setExams(await api.deliveryClasses.exams(product.categoryId, product.productId))
  }
  const categoryChanged = async (categoryId: number) => {
    form.setFieldValue('examScheduleId', undefined); setExams([])
    try { setExams(await api.deliveryClasses.exams(categoryId)) } catch (e) { message.error(e instanceof Error ? e.message : '考期加载失败') }
  }
  const save = async () => {
    const value = await form.validateFields(); setSaving(true)
    try {
      if (editing) await api.deliveryClasses.update(editing.id, { ...value, version: editing.version })
      else await api.deliveryClasses.create(value)
      message.success(editing ? '班级已更新' : '班级已创建'); setEditOpen(false); form.resetFields(); await load(1)
    } catch (e) { message.error(e instanceof Error ? e.message : '班级保存失败') }
    finally { setSaving(false) }
  }
  const complete = async (row: DeliveryClass) => {
    try { await api.deliveryClasses.complete(row.id); message.success('班级已结课'); await load(1) }
    catch (e) { message.error(e instanceof Error ? e.message : '结课失败') }
  }
  const loadTransferOptions = async (student: DeliveryClassStudent) => {
    setTransferLoading(true); setTransferError(''); setTransferOptions([])
    try { setTransferOptions(await api.deliveryClasses.options(student.categoryId!, false)) }
    catch (e) { setTransferError(e instanceof Error ? e.message : '可选班级加载失败') }
    finally { setTransferLoading(false) }
  }
  const openTransfer = async (student: DeliveryClassStudent) => {
    if (!student.categoryId) { message.error('该课程服务缺少产品分类，无法调班'); return }
    setTransfer(student); transferForm.resetFields(); setTransferOptions([]); setTransferError('')
    await loadTransferOptions(student)
  }
  const submitTransfer = async () => {
    if (!transfer || !selected) return
    const value = await transferForm.validateFields(); setSaving(true)
    try {
      if (manage) await api.deliveryClasses.directTransfer(transfer.serviceRelationId, { ...value, version: transfer.version })
      else await api.deliveryClasses.requestTransfer(transfer.serviceRelationId, { ...value, version: transfer.version })
      message.success(manage ? '调班已完成' : '调班申请已提交审批'); setTransfer(undefined)
      await loadStudents(selected, studentPageNo)
      if (canQueryTransfers) setTransferRows((await api.deliveryClasses.transferPage({ pageNo: 1, pageSize: 20 })).list)
    } catch (e) { message.error(e instanceof Error ? e.message : '调班提交失败') }
    finally { setSaving(false) }
  }

  return <section className="workspace-page">
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{manage ? '班级管理' : '我的班级'}</Typography.Title>
        <Space wrap>
          <Input.Search value={keyword} allowClear placeholder="班级名称 / 编号" onChange={e => setKeyword(e.target.value)} onSearch={() => void load(1)} />
          <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void load()} />
          {manage && has(permissions, 'zsjos:delivery-class:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void openEditor()}>创建班级</Button>}
        </Space>
      </Space>
      <Segmented value={status} onChange={value => setStatus(String(value))} options={[{ label: '服务中', value: 'SERVING' }, { label: '已结课', value: 'COMPLETED' }]} />
      {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>} />}
      <Table rowKey="id" loading={loading} dataSource={rows} pagination={{ current: pageNo, pageSize: 10, total, onChange: p => void load(p) }} onRow={row => ({ onClick: () => void open(row) })} columns={[
        { title: '班级', dataIndex: 'className' }, { title: '班级编号', dataIndex: 'classNo' }, { title: '产品分类', dataIndex: 'categoryNameSnapshot' },
        { title: '考期', dataIndex: 'examScheduleSnapshot' }, { title: '班主任', dataIndex: 'homeroomUserNameSnapshot' }, { title: '归属部门', dataIndex: 'deptNameSnapshot' }, { title: '人数', dataIndex: 'studentCount' },
        { title: '状态', dataIndex: 'status', render: value => <Tag color={value === 'SERVING' ? 'green' : 'default'}>{value === 'SERVING' ? '服务中' : '已结课'}</Tag> },
        ...(manage ? [{ title: '操作', key: 'actions', render: (_: unknown, row: DeliveryClass) => <Space onClick={e => e.stopPropagation()}>
          {has(permissions, 'zsjos:delivery-class:update') && !row.systemClass && row.status === 'SERVING' && <Button type="text" icon={<EditOutlined />} aria-label="编辑班级" onClick={() => void openEditor(row)} />}
          {has(permissions, 'zsjos:delivery-class:complete') && !row.systemClass && row.status === 'SERVING' && <Popconfirm title="确认结课该班级？" onConfirm={() => void complete(row)}><Button type="link" danger>结课</Button></Popconfirm>}
        </Space> }] : [])
      ]} />
      {selected && <section>
        <Typography.Title level={5}>{selected.className} · 学员</Typography.Title>
        {detailError && <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => void loadStudents(selected, studentPageNo)}>重试</Button>} />}
        <Table rowKey="serviceRelationId" loading={detailLoading} dataSource={students} pagination={{ current: studentPageNo, pageSize: STUDENT_PAGE_SIZE, total: studentTotal, showSizeChanger: false, onChange: page => void loadStudents(selected, page) }} locale={{ emptyText: <Empty description="班内暂无学员" /> }} columns={[
          { title: '学员', dataIndex: 'studentName' }, { title: '学员编号', dataIndex: 'personNo' }, { title: '接收状态', dataIndex: 'acceptanceStatus' }, { title: '服务状态', dataIndex: 'serviceStatus' }, { title: '学习规划师', dataIndex: 'ownerUserName' },
          { title: '操作', key: 'actions', render: (_: unknown, row: DeliveryClassStudent) => <Space>{!manage && <Button type="link" onClick={() => navigate(APP_ROUTES.MY_STUDENTS, { state: { serviceRelationId: row.serviceRelationId } })}>查看学员</Button>}{((manage && has(permissions, 'zsjos:delivery-class:direct-transfer')) || (!manage && has(permissions, 'zsjos:class-transfer:create'))) && <Button type="link" icon={<SwapOutlined />} onClick={() => void openTransfer(row)}>调班</Button>}</Space> }
        ]} />
      </section>}
      {canQueryTransfers && <section>
        <Typography.Title level={5}>我的调班申请</Typography.Title>
        <Table rowKey="id" size="small" dataSource={transferRows} pagination={false} locale={{ emptyText: <Empty description="暂无调班申请" /> }} columns={[
          { title: '原班级', dataIndex: 'fromClassName' }, { title: '目标班级', dataIndex: 'targetClassName' },
          { title: '原因', dataIndex: 'reason' }, { title: '状态', dataIndex: 'status', render: value => ({ pending: '审批中', approved: '已通过', rejected: '已拒绝', cancelled: '已取消', invalidated: '已失效' }[String(value)] || value) }
        ]} />
      </section>}
    </Space>
    <Modal open={editOpen} title={editing ? '编辑班级' : '创建班级'} confirmLoading={saving} onOk={() => void save()} onCancel={() => setEditOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical"><Form.Item name="className" label="班级名称"><Input maxLength={100} placeholder="留空时按分类、考期和序号生成" /></Form.Item><Form.Item name="productId" label="产品" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={products.map(row => ({ label: row.productName, value: row.productId }))} onChange={value => void productChanged(value)} /></Form.Item>{(products.find(row => row.productId === form.getFieldValue('productId'))?.attrs || []).map(attr => <Form.Item key={attr.attrKey} name={['selectedAttrs', attr.attrKey]} label={attr.attrName}><Select allowClear options={attr.values.map(value => ({ label: value.label, value: value.value }))} /></Form.Item>)}<Form.Item name="selectedSkuIds" label="SKU"><Select mode="multiple" showSearch optionFilterProp="label" options={(products.find(row => row.productId === form.getFieldValue('productId'))?.skus || []).map(row => ({ label: row.skuName, value: row.id }))} /></Form.Item><Form.Item name="examScheduleId" label="考期" rules={[{ required: true }]}><Select options={exams.map(row => ({ label: row.displayName, value: row.id }))} /></Form.Item><Form.Item name="homeroomUserId" label="班主任" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={candidates.map(row => ({ label: `${row.name}${row.deptName ? ` · ${row.deptName}` : ''}`, value: row.id }))} /></Form.Item></Form>
    </Modal>
    <Modal open={Boolean(transfer)} title={manage ? '主管直接调班' : '申请调班'} confirmLoading={saving} okButtonProps={{ disabled: transferLoading || Boolean(transferError) }} onOk={() => void submitTransfer()} onCancel={() => setTransfer(undefined)} destroyOnHidden>
      {transferError && <Alert type="error" showIcon message={transferError} action={<Button size="small" onClick={() => transfer && void loadTransferOptions(transfer)}>重试</Button>} />}
      <Form form={transferForm} layout="vertical"><Form.Item name="targetClassId" label="目标班级" rules={[{ required: true }]}><Select loading={transferLoading} disabled={transferLoading || Boolean(transferError)} options={transferOptions.filter(row => row.id !== selected?.id).map(row => ({ label: `${row.className} · ${row.homeroomUserName || '未配置班主任'}`, value: row.id }))} /></Form.Item><Form.Item name="reason" label="调班原因" rules={[{ required: true }, { max: 500 }]}><Input.TextArea rows={4} maxLength={500} showCount /></Form.Item></Form>
    </Modal>
  </section>
}
