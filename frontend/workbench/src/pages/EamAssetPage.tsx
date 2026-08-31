import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Form, Input, InputNumber, List, Modal, Select, Space, Spin, Statistic, Table, Tag, message } from 'antd'
import { CheckOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, ToolOutlined } from '@ant-design/icons'
import { api, type EamAssetItem, type EamAssetSummary, type EamCategory, type EamCategoryField, type EamDemand, type EamDemandItem, type EamStockCandidate, type EamTransfer, type EamTransferAsset } from '../services/api'
import { DICT_TYPE } from '../constants'
import { useDict } from '../services/useDict'

const HOLDING_STATUS = ['待签收', '持有中', '待退还验收', '已退还', '遗失']
const DEMAND_STATUS: Record<number, string> = { 0: '草稿', 1: '审批中', 2: '已通过', 3: '已驳回', 4: '已取消', 5: '履约中', 6: '已完成' }
const TASK_TYPE: Record<number, string> = { 1: '入职配资', 2: '异动复核', 3: '离职结清', 4: '取消记录' }
const TASK_STATUS: Record<number, string> = { 0: '草稿', 1: '审批中', 2: '已通过', 3: '已驳回', 4: '已取消', 5: '履行中', 6: '已完成' }
const TRANSFER_TYPE: Record<number, string> = { 1: '领用', 2: '退还', 3: '借用', 4: '归还', 5: '调拨' }
const TRANSFER_STATUS: Record<number, string> = { 0: '审批中', 1: '已生效', 2: '已驳回', 3: '已取消', 4: '草稿', 5: '待验收', 6: '已完成', 7: '异常待处理' }

function DictionaryField({ field, name, rules }: { field: EamCategoryField; name: (string | number)[]; rules: Array<{ required: boolean; message: string }> }) {
  const dictionary = useDict(field.dictType || '')
  return <Form.Item
    name={name}
    label={field.fieldName}
    rules={rules}
    validateStatus={dictionary.error ? 'error' : undefined}
    help={dictionary.error ? <Button type="link" size="small" icon={<ReloadOutlined/>} onClick={() => void dictionary.reload()}>字典加载失败，重试</Button> : undefined}
  >
    <Select
      loading={dictionary.loading}
      options={dictionary.items.map(item => ({ value: item.value, label: item.label }))}
      notFoundContent={dictionary.loading ? <Spin size="small"/> : '暂无可用选项'}
    />
  </Form.Item>
}

function DynamicFields({ fields, prefix }: { fields: EamCategoryField[]; prefix: (string | number)[] }) {
  return <>{fields.filter(field => field.collectionVisible !== false).map(field => {
    const name = [...prefix, 'extFields', field.fieldKey]
    const rules = [{ required: field.collectionRequired ?? field.required, message: `请填写${field.fieldName}` }]
    if (field.fieldType === 2) return <Form.Item key={field.fieldKey} name={name} label={field.fieldName} rules={rules}><Input.TextArea rows={2}/></Form.Item>
    if (field.fieldType === 3) return <Form.Item key={field.fieldKey} name={name} label={field.fieldName} rules={rules}><InputNumber style={{ width: '100%' }}/></Form.Item>
    if (field.fieldType === 4) return <Form.Item key={field.fieldKey} name={name} label={field.fieldName} rules={rules}><Input type="date"/></Form.Item>
    if (field.fieldType === 5 && field.optionSource === 'SYSTEM_DICT' && field.dictType) return <DictionaryField key={field.fieldKey} field={field} name={name} rules={rules}/>
    if (field.fieldType === 5) return <Form.Item key={field.fieldKey} name={name} label={field.fieldName} rules={rules}><Select options={(field.options || []).map(value => ({ value, label: value }))}/></Form.Item>
    return <Form.Item key={field.fieldKey} name={name} label={field.fieldName} rules={rules}><Input/></Form.Item>
  })}</>
}

function DemandEditor({ open, categories, onCancel, onSaved }: { open: boolean; categories: EamCategory[]; onCancel: () => void; onSaved: () => Promise<void> }) {
  const [form] = Form.useForm()
  const [fields, setFields] = useState<Record<number, EamCategoryField[]>>({})
  const [fieldsReady, setFieldsReady] = useState<Record<number, boolean>>({})
  const [previews, setPreviews] = useState<Record<number, EamStockCandidate[]>>({})
  const [previewing, setPreviewing] = useState<Record<number, boolean>>({})
  const [previewErrors, setPreviewErrors] = useState<Record<number, string>>({})
  const [previewRefresh, setPreviewRefresh] = useState(0)
  const watchedItems = Form.useWatch('items', form) as EamDemandItem[] | undefined
  const options = useMemo(() => categories.filter(item => item.status === 0 && item.effectiveDeliveryMode && item.effectiveCustodyMode).map(item => ({ value: item.id, label: item.name })), [categories])
  const loadFields = async (index: number, categoryId: number) => {
    const category = categories.find(item => item.id === categoryId)
    form.setFieldValue(['items', index, 'unit'], category?.unit)
    setFields(value => ({ ...value, [index]: [] }))
    setFieldsReady(value => ({ ...value, [index]: false }))
    try {
      const nextFields = await api.eam.categoryFields(categoryId)
      setFields(value => ({ ...value, [index]: nextFields }))
      setFieldsReady(value => ({ ...value, [index]: true }))
    }
    catch { message.error('分类自定义字段加载失败') }
  }
  useEffect(() => {
    if (open) {
      form.resetFields()
      setFields({})
      setFieldsReady({})
      setPreviews({})
      setPreviewing({})
      setPreviewErrors({})
    }
  }, [open, form])
  useEffect(() => {
    if (!open) return
    let cancelled = false
    const timer = window.setTimeout(async () => {
      const eligible = (watchedItems || []).flatMap((item, index) => {
        if (!fieldsReady[index] || !item?.categoryId || !item.name?.trim() || !item.quantity || !item.unit) return []
        const requiredFields = (fields[index] || []).filter(field =>
          field.collectionVisible !== false && (field.collectionRequired ?? field.required))
        const complete = requiredFields.every(field => {
          const value = item.extFields?.[field.fieldKey]
          return value !== undefined && value !== null && value !== ''
        })
        return complete ? [{ index, item }] : []
      })
      if (!eligible.length) {
        if (!cancelled) {
          setPreviews({})
          setPreviewing({})
          setPreviewErrors({})
        }
        return
      }
      setPreviewing(Object.fromEntries(eligible.map(({ index }) => [index, true])))
      const results = await Promise.all(eligible.map(async ({ index, item }) => {
        try {
          return { index, items: await api.eam.previewStockCandidates(item), error: undefined }
        } catch (cause) {
          return { index, items: undefined, error: cause instanceof Error ? cause.message : '库存预览失败' }
        }
      }))
      if (cancelled) return
      setPreviews(Object.fromEntries(results.filter(result => result.items).map(result => [result.index, result.items!])))
      setPreviewErrors(Object.fromEntries(results.filter(result => result.error).map(result => [result.index, result.error!])))
      setPreviewing({})
    }, 500)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [fields, fieldsReady, open, previewRefresh, watchedItems])
  return <Modal open={open} title="采购申请" width={760} okText="提交审批" onCancel={onCancel} onOk={async () => {
    const value = await form.validateFields()
    await api.eam.createDemand(value)
    message.success('采购申请已提交')
    await onSaved()
  }} destroyOnHidden>
    <Form form={form} layout="vertical" initialValues={{ items: [{ quantity: 1 }] }}>
      <Form.Item name="reason" label="申请事由"><Input.TextArea rows={2}/></Form.Item>
      <Form.List name="items">{(rows, { add, remove }) => <>
        {rows.map((row, index) => <Card key={row.key} size="small" title={`申请明细 ${index + 1}`} extra={rows.length > 1 ? <Button type="link" danger onClick={() => remove(row.name)}>删除</Button> : null}>
          <div className="eam-demand-form-grid">
            <Form.Item name={[row.name, 'name']} label="物品名称" rules={[{ required: true }]}><Input/></Form.Item>
            <Form.Item name={[row.name, 'categoryId']} label="资产分类" rules={[{ required: true }]}><Select options={options} onChange={value => loadFields(index, value)}/></Form.Item>
            <Form.Item name={[row.name, 'quantity']} label="数量" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }}/></Form.Item>
            <Form.Item name={[row.name, 'unit']} label="单位"><Input disabled/></Form.Item>
          </div>
          <DynamicFields fields={fields[index] || []} prefix={['items', row.name]}/>
          {previewing[index] && <Spin size="small"/>}
          {!previewing[index] && previewErrors[index] && <Alert
            type="warning"
            showIcon
            message="库存预览暂不可用"
            description={previewErrors[index]}
            action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => setPreviewRefresh(value => value + 1)}>重试</Button>}
          />}
          {!previewing[index] && previews[index] && <Alert
            type={previews[index].length ? 'info' : 'warning'}
            showIcon
            message={previews[index].length
              ? `当前匹配库存可用 ${previews[index].reduce((total, item) => total + item.availableQuantity, 0)} ${form.getFieldValue(['items', row.name, 'unit']) || ''}，审批后将再次确认`
              : '暂无匹配库存，审批后将再次确认'}
          />}
        </Card>)}
        <Button icon={<PlusOutlined/>} onClick={() => add({ quantity: 1 })}>增加明细</Button>
      </>}</Form.List>
    </Form>
  </Modal>
}

export default function EamAssetPage({ permissions, view }: { permissions: string[]; view: 'assets' | 'demands' }) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [summary, setSummary] = useState<EamAssetSummary>()
  const [demands, setDemands] = useState<EamDemand[]>([])
  const [categories, setCategories] = useState<EamCategory[]>([])
  const [editorOpen, setEditorOpen] = useState(false)
  const [transferOpen, setTransferOpen] = useState(false)
  const [transfers, setTransfers] = useState<EamTransfer[]>([])
  const [transferAssets, setTransferAssets] = useState<EamTransferAsset[]>([])
  const [transferForm] = Form.useForm()
  const transferType = Form.useWatch('type', transferForm) as number | undefined
  const assetStatuses = useDict(DICT_TYPE.EAM_ASSET_STATUS)
  const load = async () => {
    setLoading(true); setError('')
    try {
      if (view === 'assets') { const [assetSummary, transferRows] = await Promise.all([api.eam.myAssets(), api.eam.myTransfers()]); setSummary(assetSummary); setTransfers(transferRows) }
      else { const [items, categoryRows] = await Promise.all([api.eam.myDemands(), api.eam.categories()]); setDemands(items); setCategories(categoryRows) }
    } catch (cause) { setError(cause instanceof Error ? cause.message : '加载失败，请稍后重试') }
    finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [view])
  const sign = async (id: number) => { await api.eam.sign(id); message.success('已签收'); await load() }
  const applyReturn = async (id: number) => { const remark = await new Promise<string | undefined>(resolve => Modal.confirm({ title: '申请退还', content: <Input.TextArea id="eam-return-remark" rows={3} placeholder="可填写退还说明"/>, onOk: () => resolve((document.getElementById('eam-return-remark') as HTMLTextAreaElement)?.value), onCancel: () => resolve(undefined) })); if (remark === undefined) return; await api.eam.applyReturn(id, remark); message.success('已提交退还'); await load() }
  const repair = async (asset: EamAssetItem) => { if (!asset.assetId) return; const faultDesc = await new Promise<string | undefined>(resolve => Modal.confirm({ title: `报修 ${asset.name}`, content: <Input.TextArea id="eam-repair-fault" rows={3} placeholder="请描述故障"/>, onOk: () => resolve((document.getElementById('eam-repair-fault') as HTMLTextAreaElement)?.value), onCancel: () => resolve(undefined) })); if (!faultDesc) return; await api.eam.repair({ assetId: asset.assetId, faultDesc }); message.success('报修已提交'); await load() }
  const openTransfer = async () => {
    transferForm.resetFields(); transferForm.setFieldValue('type', 1)
    setTransferAssets(await api.eam.transferAssets())
    setTransferOpen(true)
  }
  const submitTransfer = async () => {
    const values = await transferForm.validateFields()
    await api.eam.createTransfer(values)
    message.success([1, 3].includes(values.type) ? '已提交审批' : '已提交，等待资产管理员验收')
    setTransferOpen(false); await load()
  }
  const ownTransferAssets = (summary?.items || []).filter(item => item.assetId && !item.holdingId && ((transferType === 2 && item.status === 1) || (transferType === 4 && item.status === 2)))
  const assetOptions = [1, 3].includes(transferType || 0)
    ? transferAssets.map(item => ({ value: item.id, label: `${item.assetCode} ${item.name}` }))
    : ownTransferAssets.map(item => ({ value: item.assetId!, label: `${item.assetCode || ''} ${item.name}` }))
  return <section className="workspace-page eam-assets-page">
    <div className="eam-page-toolbar"><h1>{view === 'assets' ? '我的资产' : '采购申请'}</h1><Space><Button icon={<ReloadOutlined/>} onClick={load}>刷新</Button>{view === 'assets' && permissions.includes('eam:workbench:asset:transfer') && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openTransfer()}>发起流转</Button>}{view === 'demands' && permissions.includes('eam:workbench:demand:create') && <Button type="primary" icon={<PlusOutlined/>} onClick={() => setEditorOpen(true)}>新建申请</Button>}</Space></div>
    {error && <Alert type="error" showIcon message="加载失败" description={error} action={<Button onClick={load}>重试</Button>}/>}
    {view === 'assets' && assetStatuses.error && <Alert type="error" showIcon message="资产状态字典加载失败" description={assetStatuses.error} action={<Button onClick={() => void assetStatuses.reload()}>重试</Button>}/>}
    {view === 'assets' && !assetStatuses.loading && !assetStatuses.error && assetStatuses.items.length === 0 && <Alert type="warning" showIcon message="资产状态字典暂无可用选项" action={<Button onClick={() => void assetStatuses.reload()}>重试</Button>}/>}
    {loading ? <div className="eam-loading"><Spin/></div> : view === 'assets' ? <>
      <div className="eam-stat-grid"><Statistic title="持有资产" value={summary?.items.length || 0}/><Statistic title="待签收" value={summary?.pendingSignCount || 0}/><Statistic title="待归还验收" value={summary?.pendingReturnCount || 0}/><Statistic title="资产任务" value={summary?.tasks.length || 0}/></div>
      {summary?.offboardingUncleared && <Alert type="warning" showIcon message="离职资产尚未结清"/>}
      {summary?.items.length ? <Table rowKey={row => `${row.itemType}-${row.holdingId || row.assetId}`} pagination={false} dataSource={summary.items} scroll={{ x: 720 }} columns={[
        { title: '资产', dataIndex: 'name' }, { title: '资产编号', dataIndex: 'assetCode' }, { title: '数量', render: (_, row) => `${row.quantity} ${row.unit || ''}` },
        { title: '状态', render: (_, row) => <Tag>{row.itemType.endsWith('HOLDING') ? HOLDING_STATUS[row.status] : assetStatuses.labels[String(row.status)] || '未知状态'}</Tag> },
        { title: '操作', render: (_, row) => <Space>{row.holdingId && row.status === 0 && permissions.includes('eam:workbench:asset:sign') && <Button icon={<CheckOutlined/>} onClick={() => sign(row.holdingId!)}>签收</Button>}{row.holdingId && row.status === 1 && row.custodyMode === 2 && permissions.includes('eam:workbench:asset:return') && <Button icon={<RollbackOutlined/>} onClick={() => applyReturn(row.holdingId!)}>退还</Button>}{row.assetId && (row.holdingId ? row.status === 1 : [1, 2].includes(row.status)) && permissions.includes('eam:workbench:asset:repair') && <Button icon={<ToolOutlined/>} onClick={() => repair(row)}>报修</Button>}</Space> }
      ]}/> : <Empty description="暂无个人资产"/>}
      <Card size="small" title="入离职与异动资产任务"><List locale={{ emptyText: '暂无资产任务' }} dataSource={summary?.tasks || []} renderItem={task => <List.Item><List.Item.Meta title={TASK_TYPE[task.type] || '资产任务'} description={task.remark || '系统根据员工生命周期自动创建'}/><Tag>{TASK_STATUS[task.status] || task.status}</Tag></List.Item>}/></Card>
      <Card size="small" title="我的流转"><Table rowKey="id" size="small" pagination={false} dataSource={transfers} scroll={{ x: 620 }} columns={[
        { title: '单据', dataIndex: 'no' }, { title: '类型', render: (_, row) => TRANSFER_TYPE[row.type] || row.type },
        { title: '资产', render: (_, row) => `${row.assetCodeSnapshot || ''} ${row.assetNameSnapshot || ''}`.trim() || row.assetId },
        { title: '状态', render: (_, row) => <Tag>{TRANSFER_STATUS[row.status] || row.status}</Tag> },
        { title: '验收', render: (_, row) => row.inspectionResult ? ['-', '完好', '损坏', '缺件/遗失', '不符驳回'][row.inspectionResult] : '-' }
      ]}/></Card>
    </> : demands.length ? <List dataSource={demands} renderItem={demand => <Card size="small" className="eam-demand-card" title={demand.no || '采购申请'} extra={<Tag>{DEMAND_STATUS[demand.status || 0]}</Tag>}><div className="eam-demand-meta">{demand.reason || '未填写事由'}</div><List size="small" dataSource={demand.items} renderItem={item => <List.Item><span>{item.name}</span><span>{item.fulfilledQuantity || 0}/{item.quantity} {item.unit}</span></List.Item>}/></Card>}/> : <Empty description="暂无采购申请"/>}
    <DemandEditor open={editorOpen} categories={categories} onCancel={() => setEditorOpen(false)} onSaved={async () => { setEditorOpen(false); await load() }}/>
    <Modal open={transferOpen} title="发起资产流转" okText="提交" onCancel={() => setTransferOpen(false)} onOk={() => void submitTransfer()} destroyOnHidden>
      <Form form={transferForm} layout="vertical" initialValues={{ type: 1 }}>
        <Form.Item name="type" label="流转类型" rules={[{ required: true }]}><Select options={[1, 2, 3, 4].map(value => ({ value, label: TRANSFER_TYPE[value] }))} onChange={() => transferForm.setFieldValue('assetId', undefined)}/></Form.Item>
        <Form.Item name="assetId" label="资产" rules={[{ required: true, message: '请选择资产' }]}><Select showSearch optionFilterProp="label" options={assetOptions} notFoundContent="暂无符合条件的资产"/></Form.Item>
        {transferType === 3 && <Form.Item name="expectedReturnDate" label="预计归还日期" rules={[{ required: true, message: '请选择预计归还日期' }]}><Input type="date" min={new Date().toISOString().slice(0, 10)}/></Form.Item>}
        <Form.Item name="reason" label="事由"><Input.TextArea rows={3}/></Form.Item>
      </Form>
    </Modal>
  </section>
}
