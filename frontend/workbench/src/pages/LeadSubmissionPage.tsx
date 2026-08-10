import { DeleteOutlined, PlusOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Cascader, Checkbox, Col, Divider, Form, Image, Input, Radio, Row, Select, Space, Spin, Tag, Typography, Upload } from 'antd'
import type { UploadFile, UploadProps } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DICT_TYPE, LEAD_ASSIGNMENT_MODE, LEAD_ASSIGNMENT_OPTIONS, PHONE_PATTERN } from '../constants'
import { api, type AreaNode, type LeadAttachment, type LeadCatalog, type LeadCategoryNode } from '../services/api'
import { buildLeadAreaOptions, normalizeLeadAreaPath } from '../services/area'

const { Text, Title } = Typography
type Option = { label: string; value: string }
type FormValues = {
  name: string; mobile?: string; wechatId?: string; regionPath: string[]
  sourceChannel: string; leadCategory: string; remark?: string; dispatchMode: 'auto' | 'specified'; specifiedSalesUserId?: number
}
type RemoteState = { loading: boolean; error?: string }
type Intention = { key: string; spuRef?: string; skuRef?: string; spuUnknown: boolean; skuUnknown: boolean; spuName: string; skuName: string; path: string; price?: number }

export default function LeadSubmissionPage() {
  const [form] = Form.useForm<FormValues>()
  const { message } = App.useApp()
  const [areas, setAreas] = useState<AreaNode[]>([])
  const [areaState, setAreaState] = useState<RemoteState>({ loading: true })
  const [catalog, setCatalog] = useState<LeadCatalog>({ categoryTree: [], spus: [], skus: [] })
  const [sources, setSources] = useState<Option[]>([])
  const [categories, setCategories] = useState<Option[]>([])
  const [remote, setRemote] = useState<RemoteState>({ loading: true })
  const [files, setFiles] = useState<UploadFile<LeadAttachment>[]>([])
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)
  const idempotencyKeyRef = useRef(crypto.randomUUID())
  const [sales, setSales] = useState<Array<{ label: string; value: number }>>([])
  const [salesState, setSalesState] = useState<RemoteState>({ loading: false })
  const salesLoaded = useRef(false)
  const assignmentMode = Form.useWatch('dispatchMode', form)
  const [categoryPathIds, setCategoryPathIds] = useState<number[]>([])
  const [spuUnknown, setSpuUnknown] = useState(false)
  const [spuRef, setSpuRef] = useState<string>()
  const [attrValues, setAttrValues] = useState<Record<string, string>>({})
  const [skuRef, setSkuRef] = useState<string>()
  const [skuUnknown, setSkuUnknown] = useState(false)
  const [intentions, setIntentions] = useState<Intention[]>([])
  const [primaryKey, setPrimaryKey] = useState<string>()

  const loadOptions = useCallback(async () => {
    setRemote({ loading: true })
    try {
      const [catalogItems, sourceItems, categoryItems] = await Promise.all([
        api.leadCatalog(), api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL), api.dictDataByType(DICT_TYPE.LEAD_CATEGORY)
      ])
      setCatalog(catalogItems)
      setSources(sourceItems.map(item => ({ label: item.label, value: item.value })))
      setCategories(categoryItems.map(item => ({ label: item.label, value: item.value })))
      setRemote({ loading: false })
    } catch (error) { setRemote({ loading: false, error: error instanceof Error ? error.message : '表单配置加载失败' }) }
  }, [])
  useEffect(() => { void loadOptions() }, [loadOptions])

  const loadAreas = useCallback(async () => {
    setAreaState({ loading: true })
    try {
      setAreas(await api.areaTree())
      setAreaState({ loading: false })
    } catch (error) {
      setAreas([])
      setAreaState({ loading: false, error: error instanceof Error ? error.message : '地区数据加载失败' })
    }
  }, [])
  useEffect(() => { void loadAreas() }, [loadAreas])

  const loadSales = useCallback(async () => {
    setSalesState({ loading: true })
    try { const users = await api.salesUsers(); setSales(users.map(user => ({ value: user.id, label: `${user.nickname}${user.deptName ? ` · ${user.deptName}` : ''}` }))); salesLoaded.current = true; setSalesState({ loading: false }) }
    catch (error) { setSalesState({ loading: false, error: error instanceof Error ? error.message : '销售列表加载失败' }) }
  }, [])
  useEffect(() => { if (assignmentMode === LEAD_ASSIGNMENT_MODE.SPECIFIED && !salesLoaded.current) void loadSales() }, [assignmentMode, loadSales])

  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])

  const selectedCategoryId = categoryPathIds.at(-1)
  const categoryOptions = useMemo(() => catalog.categoryTree.map(function mapCategory(item: LeadCategoryNode): any {
    return { label: item.name, value: item.id, children: item.children?.length ? item.children.map(mapCategory) : undefined }
  }), [catalog.categoryTree])
  const spuOptions = useMemo(() => catalog.spus.filter(item => item.categoryId === selectedCategoryId).map(item => ({ label: item.spuName, value: item.spuRef })), [catalog.spus, selectedCategoryId])
  const selectedSpu = catalog.spus.find(item => item.spuRef === spuRef)
  const selectedSpuSkus = useMemo(() => catalog.skus.filter(sku => sku.spuRef === spuRef), [catalog.skus, spuRef])
  const matchedSku = selectedSpuSkus.find(sku => selectedSpu?.attrs.every(attr => !attr.required || sku.attrValues[attr.attrKey] === attrValues[attr.attrKey]))
  const selectedSku = selectedSpuSkus.find(sku => sku.skuRef === (selectedSpu?.attrs.length ? matchedSku?.skuRef : skuRef))
  const canAdd = spuUnknown || Boolean(selectedSpu && (skuUnknown || selectedSku))

  const resetDraft = () => { setCategoryPathIds([]); setSpuUnknown(false); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }
  const addIntention = () => {
    if (!canAdd) return
    const unknown = spuUnknown
    const key = unknown ? 'UNKNOWN' : `${selectedSpu!.spuRef}|${skuUnknown ? 'UNKNOWN' : selectedSku!.skuRef}`
    if (intentions.some(item => item.key === key)) return message.warning('该意向课程已经添加')
    const item: Intention = unknown
      ? { key, spuUnknown: true, skuUnknown: true, spuName: '未明确课程', skuName: '未明确具体班次/方案', path: '未明确课程' }
      : { key, spuRef: selectedSpu!.spuRef, skuRef: skuUnknown ? undefined : selectedSku!.skuRef, spuUnknown: false, skuUnknown, spuName: selectedSpu!.spuName, skuName: skuUnknown ? '未明确具体班次/方案' : selectedSku!.skuName, path: selectedSpu!.categoryPath.map(node => node.name).join(' / '), price: skuUnknown ? undefined : selectedSku!.price }
    setIntentions(current => [...current, item]); setPrimaryKey(current => current || key); resetDraft()
  }
  const removeIntention = (key: string) => { setIntentions(current => { const next = current.filter(item => item.key !== key); if (primaryKey === key) setPrimaryKey(next[0]?.key); return next }) }

  const uploadProps: UploadProps<LeadAttachment> = {
    accept: '.jpg,.jpeg,.png,.webp', listType: 'picture-card', fileList: files, maxCount: 9,
    customRequest: async ({ file, onSuccess, onError }) => { try { onSuccess?.(await api.uploadLeadAttachment(file as File)) } catch (error) { onError?.(error instanceof Error ? error : new Error('上传失败')) } },
    onChange: ({ fileList }) => setFiles(fileList),
    beforeUpload: file => { if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) { message.error('仅支持 JPG、PNG、WebP，单张不超过 10MB'); return Upload.LIST_IGNORE } return true },
    itemRender: (_, file, __, actions) => <div className="lead-upload-item"><Image preview={false} src={file.thumbUrl || file.response?.fileUrl} /><Button danger type="text" icon={<DeleteOutlined />} aria-label="删除图片" onClick={actions.remove}/></div>
  }
  const validateContact = () => form.getFieldValue('mobile')?.trim() || form.getFieldValue('wechatId')?.trim() ? Promise.resolve() : Promise.reject(new Error('手机号和微信号至少填写一个'))
  const hasUploading = files.some(file => file.status === 'uploading'); const hasUploadError = files.some(file => file.status === 'error')
  const unavailable = Boolean(remote.error || areaState.error) || !areas.length || !sources.length || !categories.length

  const submit = async (values: FormValues) => {
    if (submittingRef.current) return
    if (!intentions.length || !primaryKey) return message.error('请先添加至少一条意向课程')
    if (hasUploading || hasUploadError) return message.error(hasUploading ? '图片仍在上传，请稍候' : '请删除或重试上传失败的图片')
    submittingRef.current = true; setSubmitting(true)
    try {
      const [provinceCode, cityCode] = normalizeLeadAreaPath(values.regionPath)
      const result = await api.createLead({
        name: values.name.trim(), mobile: values.mobile?.trim() || undefined, wechatId: values.wechatId?.trim() || undefined,
        provinceCode, cityCode,
        intendedProducts: intentions.map(item => ({ spuRef: item.spuRef, skuRef: item.skuRef, spuUnknown: item.spuUnknown, skuUnknown: item.skuUnknown, primary: item.key === primaryKey })),
        sourceChannel: values.sourceChannel, leadCategory: values.leadCategory, remark: values.remark?.trim() || undefined,
        attachments: files.filter(file => file.status === 'done' && file.response)
          .map(file => ({ infraFileId: file.response!.infraFileId })),
        dispatchMode: values.dispatchMode, specifiedSalesUserId: values.specifiedSalesUserId, idempotencyKey: idempotencyKeyRef.current
      })
      message.success(result.outcome === 'created' ? '客资已提交' : '重复客资已记录为再次激活')
      form.resetFields(); setFiles([]); setIntentions([]); setPrimaryKey(undefined); resetDraft(); idempotencyKeyRef.current = crypto.randomUUID()
    } catch (error) { message.error(error instanceof Error ? error.message : '提交失败') }
    finally { submittingRef.current = false; setSubmitting(false) }
  }

  return <section className="workspace-page lead-submission-page">
    {remote.error && <Alert showIcon type="error" message={remote.error} action={<Button icon={<ReloadOutlined />} onClick={() => void loadOptions()}>重试</Button>} />}
    {areaState.error && <Alert showIcon type="error" message={areaState.error} action={<Button icon={<ReloadOutlined />} onClick={() => void loadAreas()}>重新加载地区</Button>} />}
    {!areaState.loading && !areaState.error && !areas.length && <Alert showIcon type="warning" message="地区数据尚未配置" />}
    {!remote.loading && !remote.error && !catalog.spus.length && <Alert showIcon type="warning" message="课程目录尚未配置，可选择“未明确课程”继续提交" />}
    <Card bordered={false} className="lead-form-card"><Spin spinning={remote.loading || areaState.loading} tip="正在读取地区、课程和字典配置">
      <Form<FormValues> form={form} layout="vertical" requiredMark="optional" initialValues={{ dispatchMode: LEAD_ASSIGNMENT_MODE.AUTO }} onFinish={submit}>
        <Title level={5}>客户信息</Title><Row gutter={[24, 0]}>
          <Col xs={24} md={12}><Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }, { max: 100 }]}><Input /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="mobile" label="手机号" dependencies={['wechatId']} rules={[{ validator: validateContact }, { pattern: PHONE_PATTERN, message: '手机号格式不正确' }]}><Input maxLength={32} /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="wechatId" label="微信号" dependencies={['mobile']} rules={[{ validator: validateContact }]}><Input maxLength={128} /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="regionPath" label="客户地区" rules={[{ required: true, message: '请选择客户省市' }]}><Cascader options={areaOptions} showSearch placeholder="请选择省 / 市" /></Form.Item></Col>
        </Row>
        <Divider /><Title level={5}>意向课程</Title>
        <Row gutter={[12, 0]} align="bottom">
          <Col xs={24} md={8}><Form.Item label="课程分类"><Cascader disabled={spuUnknown} value={categoryPathIds} options={categoryOptions} changeOnSelect={false} showSearch onChange={value => { setCategoryPathIds(Array.from(value) as number[]); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }} placeholder="请选择课程分类" /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item label="课程 SPU"><Select disabled={!selectedCategoryId || spuUnknown} value={spuRef} options={spuOptions} onChange={value => { setSpuRef(value); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }} /></Form.Item></Col>
          <Col xs={24} md={6}><Form.Item><Button block type="primary" disabled={!canAdd} onClick={addIntention}>添加意向课程</Button></Form.Item></Col>
          <Col xs={24}><Checkbox checked={spuUnknown} onChange={event => { setSpuUnknown(event.target.checked); if (event.target.checked) { setCategoryPathIds([]); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(true) } }}>未明确课程</Checkbox></Col>
          {selectedSpu?.attrs.map(attr => <Col xs={24} md={6} key={attr.attrKey}><Form.Item label={attr.attrName} required={attr.required}><Select disabled={skuUnknown} value={attrValues[attr.attrKey]} options={attr.values.map(value => ({ label: value.label, value: value.value }))} onChange={value => setAttrValues(current => ({ ...current, [attr.attrKey]: value }))} /></Form.Item></Col>)}
          {selectedSpu && !selectedSpu.attrs.length && <Col xs={24} md={8}><Form.Item label="具体 SKU"><Select disabled={skuUnknown} value={skuRef} options={selectedSpuSkus.map(sku => ({ label: `${sku.skuName}（¥${sku.price}）`, value: sku.skuRef }))} onChange={setSkuRef} /></Form.Item></Col>}
          {selectedSpu && <Col xs={24} md={8}><Form.Item label="方案状态"><Checkbox checked={skuUnknown} onChange={event => { setSkuUnknown(event.target.checked); if (event.target.checked) { setSkuRef(undefined); setAttrValues({}) } }}>未明确具体班次/方案</Checkbox></Form.Item></Col>}
        </Row>
        <Radio.Group value={primaryKey} onChange={event => setPrimaryKey(event.target.value)} className="w-full">
          <Space direction="vertical" className="w-full">
            {intentions.map(item => <Card key={item.key} size="small"><div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}><div><Radio value={item.key}>主意向</Radio><strong>{item.spuName}</strong><div><Text type="secondary">{item.path} · {item.skuName}</Text></div><div>{item.price == null ? <Tag>价格待确认</Tag> : <Tag color="green">¥{item.price.toFixed(2)}</Tag>}</div></div><Button danger type="text" onClick={() => removeIntention(item.key)}>删除</Button></div></Card>)}
          </Space>
        </Radio.Group>
        <Divider /><Title level={5}>来源与备注</Title><Row gutter={[24, 0]}>
          <Col xs={24} md={12}><Form.Item name="sourceChannel" label="来源渠道" rules={[{ required: true }]}><Select options={sources} notFoundContent="来源渠道未配置" /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="leadCategory" label="客资分类" rules={[{ required: true }]}><Select options={categories} notFoundContent="客资分类未配置" /></Form.Item></Col>
          <Col xs={24}><Form.Item name="remark" label="备注信息"><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item></Col>
          <Col xs={24}><Form.Item label="附件图片" extra="最多 9 张，JPG、PNG、WebP，单张不超过 10MB"><Upload {...uploadProps}>{files.length < 9 && <button className="upload-trigger" type="button"><PlusOutlined /><span>上传</span></button>}</Upload></Form.Item></Col>
        </Row>
        <Divider /><Title level={5}>派单方式</Title><Form.Item name="dispatchMode" label="派单模式" rules={[{ required: true }]}><Radio.Group options={LEAD_ASSIGNMENT_OPTIONS} /></Form.Item>
        {assignmentMode === LEAD_ASSIGNMENT_MODE.SPECIFIED && <Form.Item name="specifiedSalesUserId" label="指定销售" preserve={false} rules={[{ required: true, message: '请选择指定销售' }]}><Select loading={salesState.loading} options={sales} showSearch optionFilterProp="label" notFoundContent={salesState.error ? <Button icon={<ReloadOutlined />} onClick={() => void loadSales()}>重新加载</Button> : '暂未配置可指定销售'} /></Form.Item>}
        <div className="lead-form-actions"><Space><Button onClick={() => { form.resetFields(); setFiles([]); setIntentions([]); setPrimaryKey(undefined); resetDraft() }}>重置</Button><Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={submitting} disabled={unavailable || hasUploading}>提交客资</Button></Space></div>
      </Form>
    </Spin></Card>
  </section>
}
