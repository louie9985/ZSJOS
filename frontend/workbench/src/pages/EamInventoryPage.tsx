import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Progress, Radio, Select, Skeleton, Space, Tag, TreeSelect, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type EamCategory, type EamInventoryCheck, type EamInventoryCreate, type EamInventoryDetail, type EamInventoryItem } from '../services/api'
import { INVENTORY_RESULT, INVENTORY_RESULT_COLORS, INVENTORY_RESULT_LABELS, SCOPE_TYPE, buildEamTree, toTreeSelectData } from '../services/eam'
import dayjs from 'dayjs'

const DEFAULT_PAGE_SIZE = 10

function fmtTime(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type ScopeTypeValue = typeof SCOPE_TYPE[keyof typeof SCOPE_TYPE]
type ResultValue = typeof INVENTORY_RESULT[keyof typeof INVENTORY_RESULT]
type StatusFilter = 'all' | 0 | 1

export default function EamInventoryPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<EamInventoryItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [nameFilter, setNameFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const listVersion = useRef(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [createForm] = Form.useForm<EamInventoryCreate>()
  const [scopeType, setScopeType] = useState<ScopeTypeValue>(SCOPE_TYPE.ALL)

  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [currentInventory, setCurrentInventory] = useState<EamInventoryItem>()
  const [details, setDetails] = useState<EamInventoryDetail[]>([])

  const [checkOpen, setCheckOpen] = useState(false)
  const [checkLoading, setCheckLoading] = useState(false)
  const [checkForm] = Form.useForm<EamInventoryCheck>()
  const [currentDetail, setCurrentDetail] = useState<EamInventoryDetail>()
  const [checkResult, setCheckResult] = useState<ResultValue>(INVENTORY_RESULT.NORMAL)

  const [depts, setDepts] = useState<Array<{ id: number; name: string; parentId: number }>>([])
  const [users, setUsers] = useState<Array<{ id: number; nickname: string }>>([])
  const [categories, setCategories] = useState<EamCategory[]>([])
  const [lookupError, setLookupError] = useState('')

  const canCreate = permissions.includes('eam:inventory:create')
  const canUpdate = permissions.includes('eam:inventory:update')
  const canDelete = permissions.includes('eam:inventory:delete')

  const deptTree = useMemo(() => toTreeSelectData(buildEamTree(depts)), [depts])
  const categoryTree = useMemo(() => toTreeSelectData(buildEamTree(categories)), [categories])

  const loadLookups = useCallback(async () => {
    setLookupError('')
    const [deptResult, userResult, categoryResult] = await Promise.allSettled([
      api.eam.deptSimpleList(), api.eam.userSimpleList(), api.eam.category.list()
    ])
    if (deptResult.status === 'fulfilled') setDepts(deptResult.value)
    if (userResult.status === 'fulfilled') setUsers(userResult.value)
    if (categoryResult.status === 'fulfilled') setCategories(categoryResult.value)
    const failed = [deptResult, userResult, categoryResult].filter(item => item.status === 'rejected')
    if (failed.length) setLookupError('部门/用户/分类选项加载失败，范围与实盘归属可能无法选择')
  }, [])
  useEffect(() => { void loadLookups() }, [loadLookups])

  const loadPage = useCallback(async (page: number, size: number, name: string, status: StatusFilter) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.inventory.page({ pageNo: page, pageSize: size, name: name || undefined, status: status === 'all' ? undefined : status })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '盘点列表加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, pageSize, nameFilter, statusFilter) }, [loadPage, pageNo, pageSize, nameFilter, statusFilter])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, pageSize, nameFilter, statusFilter) }, [loadPage, pageSize, nameFilter, statusFilter])

  const handleCreate = async () => {
    const values = await createForm.validateFields()
    setCreateLoading(true)
    try {
      await api.eam.inventory.create({ ...values, scopeValue: values.scopeValue != null ? String(values.scopeValue) : undefined })
      message.success('盘点已发起')
      setCreateOpen(false); createForm.resetFields(); setScopeType(SCOPE_TYPE.ALL); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '创建失败') }
    finally { setCreateLoading(false) }
  }

  const handleFinish = (id: number) => {
    Modal.confirm({
      title: '完成盘点', content: '确认完成该盘点？完成后不可再录入结果', okText: '确认完成',
      onOk: async () => {
        try { await api.eam.inventory.finish(id); message.success('盘点已完成'); reload(); setDetailOpen(false) }
        catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e }
      }
    })
  }

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该盘点单吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.eam.inventory.delete(id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const loadDetails = useCallback(async (inventoryId: number) => {
    setDetailLoading(true); setDetailError('')
    try {
      // 同时刷新头部统计，让已盘/异常计数与明细保持一致
      const [detailList, refreshed] = await Promise.all([api.eam.inventory.detailList(inventoryId), api.eam.inventory.get(inventoryId)])
      setDetails(detailList); setCurrentInventory(refreshed)
    } catch (e) { setDetailError(e instanceof Error ? e.message : '盘点明细加载失败') }
    finally { setDetailLoading(false) }
  }, [])

  const openCheck = (row: EamInventoryDetail) => {
    setCurrentDetail(row)
    const result = (row.result === INVENTORY_RESULT.UNCHECKED ? INVENTORY_RESULT.NORMAL : row.result) as ResultValue
    setCheckResult(result)
    checkForm.setFieldsValue({
      detailId: row.id, result,
      actualUserId: row.actualUserId ?? row.expectUserId,
      actualDeptId: row.actualDeptId ?? row.expectDeptId,
      actualLocation: row.actualLocation ?? row.expectLocation,
      remark: row.remark
    })
    setCheckOpen(true)
  }

  const submitCheck = async () => {
    const values = await checkForm.validateFields()
    setCheckLoading(true)
    try {
      await api.eam.inventory.check({ ...values, result: checkResult })
      message.success('已录入'); setCheckOpen(false); checkForm.resetFields()
      if (currentInventory) { void loadDetails(currentInventory.id); reload() }
    } catch (e) { message.error(e instanceof Error ? e.message : '录入失败') }
    finally { setCheckLoading(false) }
  }

  const handleSync = (detailId: number) => {
    Modal.confirm({
      title: '同步归属', content: '确认把实盘归属同步回资产台账？',
      onOk: async () => {
        try { await api.eam.inventory.syncDetail(detailId); message.success('已同步'); if (currentInventory) void loadDetails(currentInventory.id) }
        catch (e) { message.error(e instanceof Error ? e.message : '同步失败'); throw e }
      }
    })
  }

  const handleMarkLost = (detailId: number) => {
    Modal.confirm({
      title: '标记丢失', content: '确认把该资产标记为已丢失？此操作会改变资产状态', okType: 'danger',
      onOk: async () => {
        try { await api.eam.inventory.markLost(detailId); message.success('已标记丢失'); if (currentInventory) void loadDetails(currentInventory.id) }
        catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e }
      }
    })
  }

  const columns: ProColumns<EamInventoryItem>[] = [
    { title: '盘点单号', dataIndex: 'no', width: 150, fixed: 'left' },
    { title: '盘点名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '状态', width: 90, align: 'center', render: (_, row) => <Tag color={row.status === 1 ? 'success' : 'warning'}>{row.status === 1 ? '已完成' : '进行中'}</Tag> },
    { title: '进度', width: 180, render: (_, row) => {
      const percent = row.totalCount ? Math.round(((row.checkedCount ?? 0) / row.totalCount) * 100) : 0
      return <div className="eam-progress-cell">
        <Progress percent={percent} size="small" status={row.status === 1 ? 'success' : 'active'}/>
        <span className="eam-progress-caption">{row.checkedCount ?? 0} / {row.totalCount ?? 0}</span>
      </div>
    }},
    { title: '正常', dataIndex: 'normalCount', width: 80, align: 'center', render: (_, row) => row.normalCount ?? 0 },
    { title: '异常', dataIndex: 'abnormalCount', width: 80, align: 'center', render: (_, row) => <span className={row.abnormalCount ? 'eam-count-alert' : undefined}>{row.abnormalCount ?? 0}</span> },
    { title: '开始时间', dataIndex: 'startTime', width: 170, render: (_, row) => fmtTime(row.startTime) },
    { title: '操作', width: 200, align: 'center', fixed: 'right', render: (_, row) => <Space size="small">
      <Button type="link" size="small" onClick={() => { setCurrentInventory(row); setDetailOpen(true); void loadDetails(row.id) }}>盘点明细</Button>
      {row.status === 0 && canUpdate && <Button type="link" size="small" onClick={() => handleFinish(row.id)}>完成</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row.id)}>删除</Button>}
    </Space> }
  ]

  const detailColumns: ProColumns<EamInventoryDetail>[] = [
    { title: '资产编号', dataIndex: 'assetCode', width: 140, fixed: 'left' },
    { title: '资产名称', dataIndex: 'assetName', width: 150, ellipsis: true },
    { title: '账面使用人', dataIndex: 'expectUserName', width: 110 },
    { title: '账面地点', dataIndex: 'expectLocation', width: 140, ellipsis: true },
    { title: '盘点结果', width: 110, align: 'center', render: (_, row) => <Tag color={INVENTORY_RESULT_COLORS[row.result]}>{INVENTORY_RESULT_LABELS[row.result]}</Tag> },
    { title: '实盘地点', dataIndex: 'actualLocation', width: 140, ellipsis: true },
    { title: '操作', width: 230, align: 'center', fixed: 'right', render: (_, row) => {
      if (currentInventory?.status !== 0) return <span className="eam-muted">已完成</span>
      return <Space size="small">
        {canUpdate && <Button type="link" size="small" onClick={() => openCheck(row)}>录入</Button>}
        {row.result === INVENTORY_RESULT.LOCATION_MISMATCH && canUpdate && <Button type="link" size="small" onClick={() => handleSync(row.id)}>同步归属</Button>}
        {row.result === INVENTORY_RESULT.NOT_FOUND && canUpdate && <Button type="link" size="small" danger onClick={() => handleMarkLost(row.id)}>标记丢失</Button>}
      </Space>
    }}
  ]

  const content = error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
    : <ProTable<EamInventoryItem> rowKey="id" columns={columns} dataSource={items} loading={loading} search={false}
        columnsState={{ persistenceKey: 'eam-inventory-table-columns', persistenceType: 'localStorage' }}
        options={{ reload, density: true, setting: true, fullScreen: true }} scroll={{ x: 1200 }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showQuickJumper: true,
          showTotal: count => `共 ${count} 条`, onChange: (page, size) => { setPageNo(page); setPageSize(size) } }}/>

  const detailBody = detailLoading ? <Skeleton active paragraph={{ rows: 8 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => currentInventory && void loadDetails(currentInventory.id)}>重试</Button>}/>
      : !details.length ? <Empty description="该盘点单暂无明细"/>
        : <ProTable<EamInventoryDetail> rowKey="id" columns={detailColumns} dataSource={details} search={false} pagination={false}
            columnsState={{ persistenceKey: 'eam-inventory-detail-table-columns', persistenceType: 'localStorage' }}
            options={{ reload: currentInventory ? () => loadDetails(currentInventory.id) : false, density: true, setting: true, fullScreen: true }}
            scroll={{ x: 1000, y: 480 }}/>

  return <section className="workspace-page eam-inventory-page">
    <div className="page-heading">
      <Space wrap>
        <Input.Search placeholder="搜索盘点名称" allowClear onSearch={value => { setNameFilter(value); setPageNo(1) }} style={{ width: 200 }}/>
        <Radio.Group value={statusFilter} onChange={event => { setStatusFilter(event.target.value); setPageNo(1) }}>
          <Radio.Button value="all">全部</Radio.Button>
          <Radio.Button value={0}>进行中</Radio.Button>
          <Radio.Button value={1}>已完成</Radio.Button>
        </Radio.Group>
      </Space>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { createForm.resetFields(); setScopeType(SCOPE_TYPE.ALL); setCreateOpen(true) }}>发起盘点</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {lookupError && <Alert className="eam-inline-alert" type="warning" showIcon message={lookupError} action={<Button size="small" onClick={() => void loadLookups()}>重试</Button>}/>}
    <div className="eam-table-area">{content}</div>

    <Modal title="发起盘点" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={handleCreate} confirmLoading={createLoading} width={760} destroyOnClose>
      <Form form={createForm} layout="vertical" className="eam-wide-form" initialValues={{ scopeType: SCOPE_TYPE.ALL }}>
        <Form.Item name="name" label="盘点名称" rules={[{ required: true, message: '请输入盘点名称' }]}>
          <Input placeholder="如 2026年Q3资产盘点"/>
        </Form.Item>
        <Form.Item name="scopeType" label="盘点范围" rules={[{ required: true, message: '请选择盘点范围' }]}>
          <Radio.Group onChange={event => { setScopeType(event.target.value); createForm.setFieldValue('scopeValue', undefined) }}>
            <Radio value={SCOPE_TYPE.ALL}>全部资产</Radio>
            <Radio value={SCOPE_TYPE.DEPT}>按部门</Radio>
            <Radio value={SCOPE_TYPE.CATEGORY}>按分类</Radio>
            <Radio value={SCOPE_TYPE.LOCATION}>按存放地点</Radio>
          </Radio.Group>
        </Form.Item>
        {scopeType === SCOPE_TYPE.DEPT && <Form.Item name="scopeValue" label="部门" rules={[{ required: true, message: '请选择部门' }]}>
          <TreeSelect treeData={deptTree} placeholder="请选择部门" style={{ width: '100%' }} allowClear treeDefaultExpandAll/>
        </Form.Item>}
        {scopeType === SCOPE_TYPE.CATEGORY && <Form.Item name="scopeValue" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
          <TreeSelect treeData={categoryTree} placeholder="请选择分类" style={{ width: '100%' }} allowClear treeDefaultExpandAll/>
        </Form.Item>}
        {scopeType === SCOPE_TYPE.LOCATION && <Form.Item name="scopeValue" label="存放地点" rules={[{ required: true, message: '请输入存放地点' }]}>
          <Input placeholder="多个地点用逗号分隔，支持部分匹配"/>
        </Form.Item>}
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} placeholder="请输入备注"/>
        </Form.Item>
        <Alert message="创建后系统会按范围快照生成盘点明细，之后台账变动不影响本次盘点的比对基准" type="info" showIcon/>
      </Form>
    </Modal>

    <Modal title={`盘点明细 - ${currentInventory?.name ?? ''}`} open={detailOpen} onCancel={() => setDetailOpen(false)}
      footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>} width={1000}>
      {currentInventory && <div className="eam-detail-summary">
        <Tag>{currentInventory.no}</Tag>
        <span>应盘 {currentInventory.totalCount ?? 0} ｜ 已盘 {currentInventory.checkedCount ?? 0} ｜ 正常 {currentInventory.normalCount ?? 0} ｜ 异常 {currentInventory.abnormalCount ?? 0}</span>
        <Tag color={currentInventory.status === 1 ? 'success' : 'warning'}>{currentInventory.status === 1 ? '已完成' : '进行中'}</Tag>
      </div>}
      {detailBody}
    </Modal>

    <Modal title="录入盘点结果" open={checkOpen} onCancel={() => setCheckOpen(false)} onOk={submitCheck} confirmLoading={checkLoading} width={760} destroyOnClose>
      <Form form={checkForm} layout="vertical" className="eam-wide-form">
        {currentDetail && <Form.Item label="资产"><span>{currentDetail.assetCode} {currentDetail.assetName}</span></Form.Item>}
        <Form.Item label="盘点结果" required>
          <Radio.Group value={checkResult} onChange={event => setCheckResult(event.target.value)}>
            <Radio value={INVENTORY_RESULT.NORMAL}>正常</Radio>
            <Radio value={INVENTORY_RESULT.LOCATION_MISMATCH}>位置不符</Radio>
            <Radio value={INVENTORY_RESULT.NOT_FOUND}>未找到</Radio>
          </Radio.Group>
        </Form.Item>
        {checkResult === INVENTORY_RESULT.LOCATION_MISMATCH && <>
          <Form.Item name="actualUserId" label="实盘使用人">
            <Select allowClear showSearch optionFilterProp="label" placeholder="请选择实际使用人"
              options={users.map(user => ({ value: user.id, label: user.nickname }))}/>
          </Form.Item>
          <Form.Item name="actualDeptId" label="实盘部门">
            <TreeSelect treeData={deptTree} placeholder="请选择实际部门" style={{ width: '100%' }} allowClear treeDefaultExpandAll/>
          </Form.Item>
          <Form.Item name="actualLocation" label="实盘地点">
            <Input placeholder="请输入实际存放地点"/>
          </Form.Item>
        </>}
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} placeholder="请输入备注"/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
