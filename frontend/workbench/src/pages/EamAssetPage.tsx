import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal, Select, Skeleton, Space, Tag, Timeline, TreeSelect, Upload, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined, DownloadOutlined, UploadOutlined, PrinterOutlined } from '@ant-design/icons'
import { api, type EamAsset, type EamAssetChangeLog, type EamAssetListItem, type EamCategory, type EamCategoryField } from '../services/api'
import { requestBlob, downloadBlob } from '../services/download'
import { CHANGE_TYPE_LABELS, MANAGEMENT_MODE, buildEamTree, findCategory, pruneExtFields, toTreeSelectData } from '../services/eam'
import { useDict } from '../services/useDict'
import DynamicFields from '../components/DynamicFields'
import AssetImportModal from '../components/AssetImportModal'
import type { UploadFile } from 'antd/es/upload/interface'
import dayjs from 'dayjs'

const DEFAULT_PAGE_SIZE = 10

function fmtDate(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }
function fmtTime(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type AssetFormValues = Omit<EamAsset, 'purchaseDate' | 'warrantyDate'> & {
  purchaseDate?: dayjs.Dayjs; warrantyDate?: dayjs.Dayjs
}

export default function EamAssetPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<EamAssetListItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [query, setQuery] = useState<{ name?: string; assetCode?: string; categoryId?: number; status?: number; extFieldKey?: string; extFieldValue?: string }>({})
  const [filterFields, setFilterFields] = useState<EamCategoryField[]>([])

  const [formOpen, setFormOpen] = useState(false)
  const [formLoading, setFormLoading] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'update'>('create')
  const [editingId, setEditingId] = useState<number>()
  const [form] = Form.useForm<AssetFormValues>()
  const [formCategoryId, setFormCategoryId] = useState<number>()
  const [extFieldDefs, setExtFieldDefs] = useState<EamCategoryField[]>([])
  const [extFieldsLoading, setExtFieldsLoading] = useState(false)
  const [extFieldsError, setExtFieldsError] = useState('')
  const [uploadFiles, setUploadFiles] = useState<UploadFile[]>([])

  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detail, setDetail] = useState<EamAsset>()
  const [changeLogs, setChangeLogs] = useState<EamAssetChangeLog[]>([])
  const [detailFieldDefs, setDetailFieldDefs] = useState<EamCategoryField[]>([])

  const [qrOpen, setQrOpen] = useState(false)
  const [qrAsset, setQrAsset] = useState<EamAssetListItem>()
  const [qrUrl, setQrUrl] = useState<string>()
  const [qrLoading, setQrLoading] = useState(false)
  const qrObjectUrl = useRef<string | undefined>(undefined)

  const [assetImportOpen, setAssetImportOpen] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)

  const [categories, setCategories] = useState<EamCategory[]>([])
  const [depts, setDepts] = useState<Array<{ id: number; name: string; parentId: number }>>([])
  const [users, setUsers] = useState<Array<{ id: number; nickname: string }>>([])
  const [lookupError, setLookupError] = useState('')

  const assetStatus = useDict('eam_asset_status')
  const assetSource = useDict('eam_asset_source')
  const selectedFilterField = useMemo(() => filterFields.find(field => field.fieldKey === query.extFieldKey), [filterFields, query.extFieldKey])
  const filterDict = useDict(selectedFilterField?.optionSource === 'SYSTEM_DICT' ? selectedFilterField.dictType ?? '' : '')

  const canCreate = permissions.includes('eam:asset:create')
  const canUpdate = permissions.includes('eam:asset:update')
  const canDelete = permissions.includes('eam:asset:delete')
  const canQrCode = permissions.includes('eam:asset:qrcode')

  const categoryNodes = useMemo(() => buildEamTree(categories), [categories])
  const categoryTree = useMemo(() => toTreeSelectData(categoryNodes), [categoryNodes])
  const deptTree = useMemo(() => toTreeSelectData(buildEamTree(depts)), [depts])
  const selectedCategory = useMemo(() => findCategory(categoryNodes, formCategoryId), [categoryNodes, formCategoryId])
  const isBatch = selectedCategory?.managementMode === MANAGEMENT_MODE.BATCH

  const loadLookups = useCallback(async () => {
    setLookupError('')
    const [categoryResult, deptResult, userResult] = await Promise.allSettled([
      api.eam.category.list(), api.eam.deptSimpleList(), api.eam.userSimpleList()
    ])
    if (categoryResult.status === 'fulfilled') setCategories(categoryResult.value)
    if (deptResult.status === 'fulfilled') setDepts(deptResult.value)
    if (userResult.status === 'fulfilled') setUsers(userResult.value)
    if ([categoryResult, deptResult, userResult].some(item => item.status === 'rejected')) {
      setLookupError('分类/部门/用户选项加载失败，表单部分字段可能无法选择')
    }
  }, [])
  useEffect(() => { void loadLookups() }, [loadLookups])

  useEffect(() => {
    let active = true
    const categoryId = query.categoryId
    if (!categoryId) { setFilterFields([]); return }
    void api.eam.categoryField.effectiveList(categoryId).then(fields => {
      if (!active) return
      const visible = fields.filter(field => field.adminVisible !== false)
      setFilterFields(visible)
      if (query.extFieldKey && !visible.some(field => field.fieldKey === query.extFieldKey)) {
        setQuery(prev => ({ ...prev, extFieldKey: undefined, extFieldValue: undefined }))
      }
    }).catch(() => { if (active) setFilterFields([]) })
    return () => { active = false }
  }, [query.categoryId])

  const loadPage = useCallback(async (page: number, size: number, params: typeof query) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.asset.page({ pageNo: page, pageSize: size, ...params })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '资产列表加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, pageSize, query) }, [loadPage, pageNo, pageSize, query])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, pageSize, query) }, [loadPage, pageSize, query])

  useEffect(() => () => {
    if (qrObjectUrl.current) URL.revokeObjectURL(qrObjectUrl.current)
  }, [])

  const openQrCode = async (asset: EamAssetListItem) => {
    setQrAsset(asset); setQrOpen(true); setQrLoading(true); setQrUrl(undefined)
    if (qrObjectUrl.current) { URL.revokeObjectURL(qrObjectUrl.current); qrObjectUrl.current = undefined }
    try {
      const blob = await requestBlob('/eam/asset/qrcode', { id: asset.id, size: 300 })
      const objectUrl = URL.createObjectURL(blob)
      qrObjectUrl.current = objectUrl
      setQrUrl(objectUrl)
    } catch (e) { message.error(e instanceof Error ? e.message : '二维码加载失败') }
    finally { setQrLoading(false) }
  }

  const handleExport = async () => {
    setExportLoading(true)
    try { await downloadBlob('/eam/asset/export-excel', '资产台账.xlsx', query) }
    catch (e) { message.error(e instanceof Error ? e.message : '导出失败') }
    finally { setExportLoading(false) }
  }

  /** 只打印标签区域，避免带出后台页面的导航和表格（对标 admin 端点） */
  const handlePrintQr = () => {
    if (!qrAsset || !qrUrl) return
    const win = window.open('', '_blank', 'width=420,height=520')
    if (!win) return
    const title = qrAsset.assetCode ?? '资产标签'
    win.document.write(`<html><head><title>${title}</title><style>
      body { margin: 0; padding: 16px; font-family: system-ui, sans-serif; text-align: center; }
      img { width: 240px; height: 240px; border: 1px solid #e5e7eb; }
      .name { font-size: 16px; font-weight: 500; margin-top: 12px; }
      .code { font-family: monospace; font-size: 14px; color: #4b5563; margin-top: 4px; }
    </style></head><body>
      <img src="${qrUrl}" alt="${qrAsset.name}"/>
      <div class="name">${qrAsset.name}</div>
      <div class="code">${qrAsset.assetCode ?? ''}</div>
    </body></html>`)
    win.document.close()
    // 等图片解码完成再触发打印，否则可能打出空白标签
    const image = win.document.querySelector('img')
    if (image && !image.complete) { image.onload = () => win.print(); image.onerror = () => win.print() }
    else win.print()
  }

  const openForm = async (mode: 'create' | 'update', id?: number) => {
    setFormMode(mode); setEditingId(id); setFormOpen(true)
    form.resetFields(); setExtFieldDefs([]); setExtFieldsError(''); setExtFieldsLoading(false); setUploadFiles([])
    if (mode === 'create') { setFormCategoryId(undefined); form.setFieldValue('fileUrls', []); return }
    if (!id) return
    setFormLoading(true)
    try {
      const asset = await api.eam.asset.get(id)
      setFormCategoryId(asset.categoryId)
      form.setFieldsValue({
        ...asset,
        purchaseDate: asset.purchaseDate ? dayjs(asset.purchaseDate) : undefined,
        warrantyDate: asset.warrantyDate ? dayjs(asset.warrantyDate) : undefined,
        extFields: asset.extFields ?? {}
      })
      const existingFiles = (asset.fileUrls ?? []).map((url, index) => ({ uid: `existing-${index}`, name: url.split('/').pop() || `附件${index + 1}`, status: 'done' as const, url }))
      setUploadFiles(existingFiles)
      form.setFieldValue('fileUrls', asset.fileUrls ?? [])
    } catch (e) { message.error(e instanceof Error ? e.message : '资产详情加载失败'); setFormOpen(false) }
    finally { setFormLoading(false) }
  }

  const submitForm = async () => {
    const values = await form.validateFields()
    if (formCategoryId && (extFieldsLoading || extFieldsError)) {
      message.error(extFieldsError || '自定义字段仍在加载，请稍后再保存')
      return
    }
    setFormLoading(true)
    try {
      const payload: EamAsset = {
        ...values,
        id: formMode === 'update' ? editingId : undefined,
        // 单件管理固定 1 件，与 admin 端行为一致
        quantity: isBatch ? values.quantity ?? 1 : 1,
        purchaseDate: values.purchaseDate?.format('YYYY-MM-DD'),
        warrantyDate: values.warrantyDate?.format('YYYY-MM-DD'),
        extFields: pruneExtFields(values.extFields ?? {}, extFieldDefs.map(field => field.fieldKey))
      }
      if (formMode === 'create') { await api.eam.asset.create(payload); message.success('创建成功') }
      else { await api.eam.asset.update(payload); message.success('更新成功') }
      setFormOpen(false); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setFormLoading(false) }
  }

  const openDetail = async (id: number) => {
    setDetailOpen(true); setDetailLoading(true); setDetailError('')
    setDetail(undefined); setChangeLogs([]); setDetailFieldDefs([])
    try {
      const asset = await api.eam.asset.get(id)
      setDetail(asset)
      const [logs, defs] = await Promise.allSettled([
        api.eam.asset.changeLog(id),
        asset.categoryId ? api.eam.categoryField.effectiveList(asset.categoryId) : Promise.resolve([])
      ])
      if (logs.status === 'fulfilled') setChangeLogs(logs.value)
      if (defs.status === 'fulfilled') setDetailFieldDefs(defs.value.filter(field => field.adminVisible !== false))
    } catch (e) { setDetailError(e instanceof Error ? e.message : '资产详情加载失败') }
    finally { setDetailLoading(false) }
  }

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该资产吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.eam.asset.delete(id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ProColumns<EamAssetListItem>[] = [
    { title: '资产编号', dataIndex: 'assetCode', width: 140, fixed: 'left' },
    { title: '资产名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '分类', dataIndex: 'categoryName', width: 110 },
    { title: '管理', width: 80, align: 'center', render: (_, row) => row.managementMode === MANAGEMENT_MODE.BATCH ? '批量' : '单件' },
    { title: '数量', width: 90, align: 'center', render: (_, row) => `${row.quantity || 1} ${row.unit || '个'}` },
    { title: '状态', width: 90, align: 'center', render: (_, row) => row.status != null
      ? <Tag>{assetStatus.labels[String(row.status)] ?? row.status}</Tag> : '-' },
    { title: '品牌型号', dataIndex: 'brand', width: 130, ellipsis: true },
    { title: '使用人', dataIndex: 'useUserName', width: 100 },
    { title: '使用部门', dataIndex: 'useDeptName', width: 120 },
    { title: '存放地点', dataIndex: 'location', width: 140, ellipsis: true },
    { title: '购入日期', dataIndex: 'purchaseDate', width: 110, render: (_, row) => fmtDate(row.purchaseDate) },
    { title: '操作', width: 220, align: 'center', fixed: 'right', render: (_, row) => <Space size="small">
      <Button type="link" size="small" onClick={() => void openDetail(row.id)}>详情</Button>
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm('update', row.id)}>编辑</Button>}
      {canQrCode && <Button type="link" size="small" onClick={() => void openQrCode(row)}>二维码</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row.id)}>删除</Button>}
    </Space> }
  ]

  const extEntries = useMemo(() => {
    const values = detail?.extFields ?? {}
    const labels = new Map(detailFieldDefs.map(field => [field.fieldKey, field.fieldName]))
    return Object.entries(values).map(([key, value]) => ({
      key, label: labels.get(key) ?? key,
      value: detail?.extFieldLabels?.[key] ?? (value == null || value === '' ? '-' : String(value))
    }))
  }, [detail, detailFieldDefs])

  const content = error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
    : <ProTable<EamAssetListItem>
        rowKey="id"
        columns={columns}
        dataSource={items}
        loading={loading}
        search={false}
        columnsState={{ persistenceKey: 'eam-asset-table-columns', persistenceType: 'localStorage' }}
        options={{ reload, density: true, setting: true, fullScreen: true }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: count => `共 ${count} 条`,
          onChange: (page, size) => { setPageNo(page); setPageSize(size) }
        }}
        scroll={{ x: 1600 }}
      />

  const detailBody = detailLoading ? <Skeleton active paragraph={{ rows: 12 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError}/>
      : !detail ? <Empty description="暂无数据"/>
        : <>
          <Descriptions column={2} bordered size="small" items={[
            { key: 'code', label: '资产编号', children: detail.assetCode || '-' },
            { key: 'name', label: '资产名称', children: detail.name || '-' },
            { key: 'category', label: '分类', children: detail.categoryName || '-' },
            { key: 'mode', label: '管理模式', children: detail.managementMode === MANAGEMENT_MODE.BATCH ? '批量管理' : '单件管理' },
            { key: 'qty', label: '数量', children: `${detail.quantity || 1} ${detail.unit || '个'}` },
            { key: 'status', label: '状态', children: detail.status != null ? (assetStatus.labels[String(detail.status)] ?? detail.status) : '-' },
            { key: 'brand', label: '品牌型号', children: detail.brand || '-' },
            { key: 'spec', label: '规格参数', children: detail.specification || '-' },
            { key: 'sn', label: '序列号', children: detail.sn || '-' },
            { key: 'barcode', label: '条码', children: detail.barcode || '-' },
            { key: 'purchase', label: '购入日期', children: fmtDate(detail.purchaseDate) },
            { key: 'source', label: '资产来源', children: detail.sourceLabelSnapshot || assetSource.labels[String(detail.source)] || '-' },
            { key: 'original', label: '原值', children: detail.originalValue == null ? '-' : `¥${Number(detail.originalValue).toFixed(2)}` },
            { key: 'net', label: '净值', children: detail.netValue == null ? '-' : `¥${Number(detail.netValue).toFixed(2)}` },
            { key: 'warranty', label: '保修到期日', children: fmtDate(detail.warrantyDate) },
            { key: 'life', label: '预计寿命（月）', children: detail.expectedLife ?? '-' },
            { key: 'dept', label: '使用部门', children: detail.useDeptName || '-' },
            { key: 'user', label: '使用人', children: detail.useUserName || '-' },
            { key: 'snapshot', label: '使用人姓名快照', children: detail.useUserNameSnapshot || '-' },
            { key: 'location', label: '存放地点', span: 2, children: detail.location || '-' },
            { key: 'remark', label: '备注', span: 2, children: detail.remark || '-' },
            { key: 'files', label: '附件', span: 2, children: detail.fileUrls?.length
              ? <Space wrap>{detail.fileUrls.map(url => <a key={url} href={url} target="_blank" rel="noreferrer">{url.split('/').pop() || url}</a>)}</Space>
              : '-' }
          ]}/>

          {extEntries.length > 0 && <>
            <div className="eam-section-title">自定义字段</div>
            <Descriptions column={2} bordered size="small" items={extEntries.map(entry => ({ key: entry.key, label: entry.label, children: entry.value }))}/>
          </>}

          <div className="eam-section-title">变更记录</div>
          {!changeLogs.length ? <Empty description="暂无变更记录" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
            : <Timeline items={changeLogs.map(log => ({
              key: log.id,
              children: <div>
                <Space size="small"><Tag>{CHANGE_TYPE_LABELS[log.changeType] ?? '变更'}</Tag><span>{log.content}</span></Space>
                <div className="eam-log-meta">
                  操作人：{log.operatorName || '系统'}
                  {log.afterStatus != null && log.beforeStatus !== log.afterStatus && <>
                    　｜　状态：{assetStatus.labels[String(log.beforeStatus)] ?? log.beforeStatus ?? '-'} → {assetStatus.labels[String(log.afterStatus)] ?? log.afterStatus}
                  </>}
                  　｜　{fmtTime(log.operateTime)}
                </div>
              </div>
            }))}/>}
        </>

  return <section className="workspace-page eam-asset-page">
    <div className="page-heading">
      <Space wrap>
        <Input.Search placeholder="资产名称" allowClear style={{ width: 160 }} onSearch={value => { setQuery(prev => ({ ...prev, name: value || undefined })); setPageNo(1) }}/>
        <Input.Search placeholder="资产编号" allowClear style={{ width: 160 }} onSearch={value => { setQuery(prev => ({ ...prev, assetCode: value || undefined })); setPageNo(1) }}/>
        <TreeSelect treeData={categoryTree} placeholder="全部分类" style={{ width: 160 }} allowClear treeDefaultExpandAll
          onChange={value => { setQuery(prev => ({ ...prev, categoryId: value as number | undefined })); setPageNo(1) }}/>
        <Select placeholder="全部状态" style={{ width: 140 }} allowClear options={assetStatus.options}
          onChange={value => { setQuery(prev => ({ ...prev, status: value })); setPageNo(1) }}/>
        <Select placeholder="自定义字段" style={{ width: 150 }} allowClear value={query.extFieldKey}
          options={filterFields.map(field => ({ value: field.fieldKey, label: field.fieldName }))}
          onChange={value => { setQuery(prev => ({ ...prev, extFieldKey: value, extFieldValue: undefined })); setPageNo(1) }}/>
        {selectedFilterField && (selectedFilterField.fieldType === 5
          ? <Select placeholder={`请选择${selectedFilterField.fieldName}`} style={{ width: 160 }} allowClear value={query.extFieldValue}
              loading={filterDict.loading} options={selectedFilterField.optionSource === 'SYSTEM_DICT' ? filterDict.options : (selectedFilterField.options ?? []).map(value => ({ value: Number(value), label: value }))}
              onChange={value => { setQuery(prev => ({ ...prev, extFieldValue: value == null ? undefined : String(value) })); setPageNo(1) }}/>
          : <Input placeholder={selectedFilterField.fieldName} allowClear value={query.extFieldValue}
              onChange={event => setQuery(prev => ({ ...prev, extFieldValue: event.target.value }))}
              onPressEnter={() => { setPageNo(1); void loadPage(1, pageSize, query) }}/>) }
      </Space>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm('create')}>新增</Button>}
        {permissions.includes('eam:asset:import') && <Button icon={<UploadOutlined/>} onClick={() => setAssetImportOpen(true)}>导入</Button>}
        {permissions.includes('eam:asset:export') && <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={() => void handleExport()}>导出</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {lookupError && <Alert className="eam-inline-alert" type="warning" showIcon message={lookupError} action={<Button size="small" onClick={() => void loadLookups()}>重试</Button>}/>}
    {assetSource.error && <Alert className="eam-inline-alert" type="warning" showIcon message="资产来源字典加载失败，来源字段暂不可选" action={<Button size="small" onClick={() => void assetSource.reload()}>重试</Button>}/>}
    <div className="eam-table-area">{content}</div>

    <Modal title={formMode === 'create' ? '新增资产' : '编辑资产'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={submitForm} confirmLoading={formLoading} width={900} destroyOnClose>
      {formLoading && formMode === 'update' ? <Skeleton active paragraph={{ rows: 10 }}/> : <Form form={form} layout="vertical" className="eam-asset-form">
        <div className="eam-form-grid">
          <Form.Item name="name" label="资产名称" rules={[{ required: true, message: '请输入资产名称' }]}>
            <Input placeholder="请输入资产名称"/>
          </Form.Item>
          <Form.Item name="categoryId" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <TreeSelect treeData={categoryTree} placeholder="请选择分类" style={{ width: '100%' }} treeDefaultExpandAll
              onChange={value => setFormCategoryId(value as number | undefined)}/>
          </Form.Item>
          <Form.Item name="brand" label="品牌型号"><Input placeholder="如 Apple M3 Pro"/></Form.Item>
          <Form.Item label="管理模式"><Input disabled value={isBatch ? '批量管理' : '单件管理'}/></Form.Item>
          <Form.Item name="quantity" label={`数量（${selectedCategory?.unit || '个'}）`}>
            <InputNumber min={1} precision={0} disabled={!isBatch} style={{ width: '100%' }}/>
          </Form.Item>
          <Form.Item name="specification" label="规格参数"><Input placeholder="如 18G/512G"/></Form.Item>
          <Form.Item name="sn" label="序列号"><Input placeholder="设备 SN"/></Form.Item>
          <Form.Item name="barcode" label="条码"><Input placeholder="外部条码"/></Form.Item>
          <Form.Item name="purchaseDate" label="购入日期"><DatePicker style={{ width: '100%' }}/></Form.Item>
          <Form.Item name="source" label="资产来源"><Select allowClear placeholder="请选择来源" options={assetSource.options}/></Form.Item>
          <Form.Item name="originalValue" label="原值"><InputNumber min={0} precision={2} style={{ width: '100%' }}/></Form.Item>
          <Form.Item name="netValue" label="净值"><InputNumber min={0} precision={2} style={{ width: '100%' }}/></Form.Item>
          <Form.Item name="warrantyDate" label="保修到期日"><DatePicker style={{ width: '100%' }}/></Form.Item>
          <Form.Item name="expectedLife" label="预计寿命（月）"><InputNumber min={1} precision={0} style={{ width: '100%' }}/></Form.Item>
          <Form.Item name="useDeptId" label="使用部门">
            <TreeSelect treeData={deptTree} placeholder="请选择使用部门" style={{ width: '100%' }} allowClear treeDefaultExpandAll/>
          </Form.Item>
          <Form.Item name="useUserId" label="使用人">
            <Select allowClear showSearch optionFilterProp="label" placeholder="请选择使用人" options={users.map(user => ({ value: user.id, label: user.nickname }))}/>
          </Form.Item>
        </div>
        <Form.Item name="location" label="存放地点"><Input placeholder="如 总部三楼研发区"/></Form.Item>
        <Form.Item name="remark" label="备注"><Input.TextArea rows={2} placeholder="请输入备注"/></Form.Item>
          <Form.Item label="上传附件">
            <Upload
            fileList={uploadFiles}
            multiple
            showUploadList
            beforeUpload={async file => {
              try {
                const url = await api.eam.uploadFile(file)
                const current = form.getFieldValue('fileUrls') ?? []
                form.setFieldValue('fileUrls', [...current, url])
                setUploadFiles(prev => [...prev, { uid: `${Date.now()}-${file.uid}`, name: file.name, status: 'done', url }])
                message.success(`${file.name} 上传成功`)
              } catch (e) { message.error(e instanceof Error ? e.message : '附件上传失败') }
              return false
            }}
            onRemove={file => {
              const current = form.getFieldValue('fileUrls') ?? []
              const url = file.url
              form.setFieldValue('fileUrls', url ? current.filter((item: string) => item !== url) : current)
              setUploadFiles(prev => prev.filter(item => item.uid !== file.uid))
            }}
          >
            <Button>选择文件</Button>
          </Upload>
        </Form.Item>
        <Form.Item name="fileUrls" hidden><Input/></Form.Item>

        <div className="eam-section-title">分类自定义字段</div>
        <DynamicFields categoryId={formCategoryId} onFieldsChange={setExtFieldDefs}
          onStateChange={state => { setExtFieldsLoading(state.loading); setExtFieldsError(state.error) }}/>
      </Form>}
    </Modal>

    <Drawer title="资产详情" open={detailOpen} onClose={() => setDetailOpen(false)} width={820} className="eam-asset-detail-drawer">
      {detailBody}
    </Drawer>

    <Modal title="资产二维码" open={qrOpen} onCancel={() => { setQrOpen(false); if (qrObjectUrl.current) { URL.revokeObjectURL(qrObjectUrl.current); qrObjectUrl.current = undefined } }}
      footer={<Space><Button type="primary" icon={<PrinterOutlined/>} onClick={handlePrintQr} disabled={!qrUrl}>打印</Button><Button onClick={() => setQrOpen(false)}>关闭</Button></Space>} width={380}>
      {qrAsset && <div className="eam-qrcode-body">
        {qrLoading ? <Skeleton.Node active style={{ width: 300, height: 300 }}/>
          : qrUrl ? <img src={qrUrl} alt={`${qrAsset.name} 二维码`} width={300} height={300}/>
            : <Empty description="二维码加载失败"/>}
        <div className="eam-qrcode-caption">{qrAsset.assetCode} {qrAsset.name}</div>
      </div>}
    </Modal>

    <AssetImportModal open={assetImportOpen} onClose={() => setAssetImportOpen(false)} onImported={reload}/>
  </section>
}
