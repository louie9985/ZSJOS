import { ReloadOutlined, SendOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Cascader, Col, Divider, Form, Input, Radio, Row, Select, Space, Spin, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DICT_TYPE, LEAD_ASSIGNMENT_MODE, LEAD_ASSIGNMENT_OPTIONS, PHONE_PATTERN } from '../constants'
import { api, type AreaNode, type LeadAttachment, type LeadCatalog } from '../services/api'
import { buildLeadAreaOptions, normalizeLeadAreaPath } from '../services/area'
import LeadIntendedProductEditor, { type IntendedProductSelection } from '../components/LeadIntendedProductEditor'
import DeferredAttachmentPicker from '../components/DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import EmployeeSelect from '../components/EmployeeSelect'
import type { SalesUser } from '../services/api'

const { Title } = Typography
type Option = { label: string; value: string }
type FormValues = {
  name: string; mobile?: string; wechatId?: string; regionPath: string[]
  sourceChannel: string; leadCategory: string; remark?: string; dispatchMode: 'auto' | 'specified'; specifiedSalesUserId?: number
}
type RemoteState = { loading: boolean; error?: string }

export default function LeadSubmissionPage({ selfSourced = false }: { selfSourced?: boolean }) {
  const [form] = Form.useForm<FormValues>()
  const mobile = Form.useWatch('mobile', form)
  const wechatId = Form.useWatch('wechatId', form)
  const { message } = App.useApp()
  const [areas, setAreas] = useState<AreaNode[]>([])
  const [areaState, setAreaState] = useState<RemoteState>({ loading: true })
  const [catalog, setCatalog] = useState<LeadCatalog>({ categoryTree: [], spus: [], skus: [] })
  const [sources, setSources] = useState<Option[]>([])
  const [categories, setCategories] = useState<Option[]>([])
  const [remote, setRemote] = useState<RemoteState>({ loading: true })
  const [files, setFiles] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)
  const idempotencyKeyRef = useRef<string | undefined>(undefined)
  const [sales, setSales] = useState<SalesUser[]>([])
  const [salesState, setSalesState] = useState<RemoteState>({ loading: false })
  const salesLoaded = useRef(false)
  const assignmentMode = Form.useWatch('dispatchMode', form)
  const [intentions, setIntentions] = useState<IntendedProductSelection[]>([])
  const [primaryKey, setPrimaryKey] = useState<string>()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<FormValues>()

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
      try { setSales(await api.salesUsers()); salesLoaded.current = true; setSalesState({ loading: false }) }
    catch (error) { setSalesState({ loading: false, error: error instanceof Error ? error.message : '销售列表加载失败' }) }
  }, [])
  useEffect(() => { if (assignmentMode === LEAD_ASSIGNMENT_MODE.SPECIFIED && !salesLoaded.current) void loadSales() }, [assignmentMode, loadSales])

  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])


  const validateContact = () => form.getFieldValue('mobile')?.trim() || form.getFieldValue('wechatId')?.trim() ? Promise.resolve() : Promise.reject(new Error('请填写手机号或微信号'))
  const hasUploading = files.some(file => file.status === 'uploading'); const hasUploadError = files.some(file => file.status === 'error')
  const unavailable = Boolean(remote.error || areaState.error) || !areas.length || !sources.length || !categories.length

  const prepareSubmit = async () => {
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    if (!intentions.length || !primaryKey) return message.error('请先添加至少一条意向课程')
    if (hasUploading || hasUploadError) return message.error(hasUploading ? '图片仍在上传，请稍候' : '请删除或重试上传失败的图片')
    setPendingValues(values)
    setConfirmOpen(true)
  }

  const submit = async () => {
    const values = pendingValues
    setConfirmOpen(false)
    if (!values) return
    if (submittingRef.current) return
    submittingRef.current = true; setSubmitting(true)
    try {
      const idempotencyKey = idempotencyKeyRef.current ?? crypto.randomUUID()
      idempotencyKeyRef.current = idempotencyKey
      const uploadResult = await uploadDeferredFiles(files, api.uploadLeadAttachment, setFiles)
      if (uploadResult.failed) { message.error('有图片上传失败，请重试失败项'); return }
      const [provinceCode, cityCode] = normalizeLeadAreaPath(values.regionPath)
      const result = await (selfSourced ? api.createSelfSourcedLead : api.createLead)({
        name: values.name.trim(), mobile: values.mobile?.trim() || undefined, wechatId: values.wechatId?.trim() || undefined,
        provinceCode, cityCode,
        intendedProducts: intentions.map(item => ({ spuRef: item.spuRef, skuRef: item.skuRef, spuUnknown: item.spuUnknown, skuUnknown: item.skuUnknown, primary: item.key === primaryKey })),
        sourceChannel: values.sourceChannel, leadCategory: values.leadCategory, remark: values.remark?.trim() || undefined,
        attachments: uploadResult.items.filter(file => file.uploaded)
          .map(file => ({ infraFileId: file.uploaded!.infraFileId })),
        dispatchMode: selfSourced ? 'auto' : values.dispatchMode,
        specifiedSalesUserId: selfSourced ? undefined : values.specifiedSalesUserId, idempotencyKey
      })
      if (result.outcome === 'created') message.success('客资已提交')
      if (result.outcome === 'review_pending') message.info(`疑似重复，已进入复核队列 #${result.reviewId}`)
      if (result.outcome === 'duplicate_rejected') message.warning('已有活动客资，本次提交未创建复核任务')
      if (result.outcome === 'activated') message.success('历史重复提交已记录')
      form.resetFields(); setFiles([]); setIntentions([]); setPrimaryKey(undefined); setPendingValues(undefined); idempotencyKeyRef.current = undefined
    } catch (error) { message.error(error instanceof Error ? error.message : '提交失败') }
    finally { submittingRef.current = false; setSubmitting(false) }
  }

  return <section className="workspace-page lead-submission-page">
    {remote.error && <Alert showIcon type="error" message={remote.error} action={<Button icon={<ReloadOutlined />} onClick={() => void loadOptions()}>重试</Button>} />}
    {areaState.error && <Alert showIcon type="error" message={areaState.error} action={<Button icon={<ReloadOutlined />} onClick={() => void loadAreas()}>重新加载地区</Button>} />}
    {!areaState.loading && !areaState.error && !areas.length && <Alert showIcon type="warning" message="地区数据尚未配置" />}
    {!remote.loading && !remote.error && !catalog.spus.length && <Alert showIcon type="warning" message="课程目录尚未配置，可选择“未明确课程”继续提交" />}
    <Card bordered={false} className="lead-form-card"><Spin spinning={remote.loading || areaState.loading} tip="正在读取地区、课程和字典配置">
      <Form<FormValues> form={form} layout="vertical" initialValues={{ dispatchMode: LEAD_ASSIGNMENT_MODE.AUTO }}>
        <Title level={5}>客户信息</Title><Row gutter={[24, 0]}>
          <Col xs={24} md={12}><Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }, { max: 100 }]}><Input /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="mobile" label="手机号" required={!wechatId?.trim()} extra="手机号、微信号必填其中一个" dependencies={['wechatId']} rules={[{ validator: validateContact }, { pattern: PHONE_PATTERN, message: '手机号格式不正确' }]}><Input maxLength={32} /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="wechatId" label="微信号" required={!mobile?.trim()} dependencies={['mobile']} rules={[{ validator: validateContact }]}><Input maxLength={64} /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="regionPath" label="客户地区" rules={[{ required: true, message: '请选择客户省市' }]}><Cascader options={areaOptions} showSearch placeholder="请选择省 / 市" /></Form.Item></Col>
        </Row>
        <Divider /><Title level={5}><span className="required-section-title">意向课程</span></Title>
        <LeadIntendedProductEditor catalog={catalog} value={intentions} primaryKey={primaryKey}
          onChange={setIntentions} onPrimaryChange={setPrimaryKey}/>
        <Divider /><Title level={5}>来源与备注</Title><Row gutter={[24, 0]}>
          <Col xs={24} md={12}><Form.Item name="sourceChannel" label="来源渠道" rules={[{ required: true }]}><Select options={sources} notFoundContent="来源渠道未配置" /></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="leadCategory" label="客资分类" rules={[{ required: true }]}><Select options={categories} notFoundContent="客资分类未配置" /></Form.Item></Col>
          <Col xs={24}><Form.Item name="remark" label="备注信息"><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item></Col>
          <Col xs={24}><Form.Item label={`附件图片${hasUploading ? '（上传中）' : ''}`} extra="确认提交后上传；最多 9 张，JPG、PNG、WebP，单张不超过 10MB"><DeferredAttachmentPicker value={files} onChange={setFiles} accept="image/jpeg,image/png,image/webp"/></Form.Item></Col>
        </Row>
        {!selfSourced && <><Divider /><Title level={5}>派单方式</Title><Form.Item name="dispatchMode" label="派单模式" rules={[{ required: true }]}><Radio.Group options={LEAD_ASSIGNMENT_OPTIONS} /></Form.Item>
        {assignmentMode === LEAD_ASSIGNMENT_MODE.SPECIFIED && <Form.Item name="specifiedSalesUserId" label="指定销售" preserve={false} rules={[{ required: true, message: '请选择指定销售' }]}><EmployeeSelect users={sales} loading={salesState.loading} showSearch optionFilterProp="label" notFoundContent={salesState.error ? <Button icon={<ReloadOutlined />} onClick={() => void loadSales()}>重新加载</Button> : '暂未配置可指定销售'} /></Form.Item>}</>}
        <div className="lead-form-actions"><Space><Button onClick={() => { form.resetFields(); setFiles([]); setIntentions([]); setPrimaryKey(undefined) }}>重置</Button><IrreversiblePopconfirm action={`提交客资「${pendingValues?.name?.trim() || '当前客户'}」`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" icon={<SendOutlined />} loading={submitting} disabled={unavailable || hasUploading} onClick={() => void prepareSubmit()}>提交客资</Button></IrreversiblePopconfirm></Space></div>
      </Form>
    </Spin></Card>
  </section>
}
