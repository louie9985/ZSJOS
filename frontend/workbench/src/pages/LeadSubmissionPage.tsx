import { ReloadOutlined, SendOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Cascader, Col, Descriptions, Form, Input, Radio, Row, Select, Space, Spin, Steps, Tag, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DICT_TYPE, LEAD_ASSIGNMENT_MODE, LEAD_ASSIGNMENT_OPTIONS, PHONE_PATTERN } from '../constants'
import { api, type AreaNode, type LeadAttachment, type LeadCatalog, type LeadCreateResult } from '../services/api'
import { buildLeadAreaOptions, normalizeLeadAreaPath } from '../services/area'
import LeadIntendedProductEditor, { type IntendedProductSelection } from '../components/LeadIntendedProductEditor'
import DeferredAttachmentPicker from '../components/DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import EmployeeSelect from '../components/EmployeeSelect'
import type { SalesUser } from '../services/api'
import { createIdempotencyKey } from '../services/idempotency'

const { Title, Text } = Typography
type Option = { label: string; value: string }
type FormValues = {
  name: string; mobile?: string; wechatId?: string; regionPath: string[]
  sourceChannel: string; leadCategory: string; remark?: string; dispatchMode: 'auto' | 'specified'; specifiedSalesUserId?: number
  newMediaProviderUserId?: number
}
type RemoteState = { loading: boolean; error?: string }
type StepKey = 'customer' | 'product' | 'source' | 'dispatch'

/** 每一步负责校验的字段，进入下一步前只校验本步字段，最后提交再整表校验。 */
const STEP_FIELDS: Record<StepKey, Array<keyof FormValues>> = {
  customer: ['name', 'mobile', 'wechatId', 'regionPath'],
  product: [],
  source: ['sourceChannel', 'leadCategory', 'remark'],
  dispatch: ['dispatchMode', 'specifiedSalesUserId', 'newMediaProviderUserId']
}

/** 两种派单模式的后果说明，写在选项卡片里让提交人选之前就知道区别。 */
const DISPATCH_MODE_HINTS: Record<string, string> = {
  [LEAD_ASSIGNMENT_MODE.AUTO]: '按派单规则匹配销售，对方接单后归属确定',
  [LEAD_ASSIGNMENT_MODE.SPECIFIED]: '跳过派单规则，直接指派给选定的销售'
}

export default function LeadSubmissionPage({ selfSourced = false }: { selfSourced?: boolean }) {
  const [form] = Form.useForm<FormValues>()
  const mobile = Form.useWatch('mobile', form)
  const wechatId = Form.useWatch('wechatId', form)
  const { message, modal } = App.useApp()
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
  const [providers, setProviders] = useState<SalesUser[]>([])
  const [providerState, setProviderState] = useState<RemoteState>({ loading: false })
  const [intentions, setIntentions] = useState<IntendedProductSelection[]>([])
  const [primaryKey, setPrimaryKey] = useState<string>()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<FormValues>()
  const [current, setCurrent] = useState(0)
  const [invalidSteps, setInvalidSteps] = useState<StepKey[]>([])

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

  const loadProviders = useCallback(async () => {
    setProviderState({ loading: true })
    try { setProviders(await api.newMediaProviders()); setProviderState({ loading: false }) }
    catch (error) { setProviders([]); setProviderState({ loading: false, error: error instanceof Error ? error.message : '新媒体提供方加载失败' }) }
  }, [])
  useEffect(() => { if (selfSourced) void loadProviders() }, [selfSourced, loadProviders])

  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])

  const validateContact = () => form.getFieldValue('mobile')?.trim() || form.getFieldValue('wechatId')?.trim() ? Promise.resolve() : Promise.reject(new Error('请填写手机号或微信号'))
  const hasUploading = files.some(file => file.status === 'uploading'); const hasUploadError = files.some(file => file.status === 'error')
  const unavailable = Boolean(remote.error || areaState.error) || !areas.length || !sources.length || !categories.length

  const steps: Array<{ key: StepKey; title: string; description: string }> = [
    { key: 'customer', title: '客户信息', description: '姓名、联系方式、地区' },
    { key: 'product', title: '意向课程', description: '至少一条并指定主意向' },
    { key: 'source', title: '来源与备注', description: '渠道、分类、附件' },
    { key: 'dispatch', title: selfSourced ? '提供方与确认' : '派单与确认', description: '核对信息后提交' }
  ]
  const lastIndex = steps.length - 1
  const markStep = (key: StepKey, invalid: boolean) =>
    setInvalidSteps(current => invalid ? (current.includes(key) ? current : [...current, key]) : current.filter(item => item !== key))

  /** 校验单步：字段规则 + 该步特有的非表单校验（意向课程、附件上传状态）。 */
  const validateStep = async (key: StepKey, notify: boolean) => {
    const fields = STEP_FIELDS[key].filter(field => {
      if (field === 'specifiedSalesUserId') return !selfSourced && form.getFieldValue('dispatchMode') === LEAD_ASSIGNMENT_MODE.SPECIFIED
      if (field === 'dispatchMode') return !selfSourced
      if (field === 'newMediaProviderUserId') return selfSourced
      return true
    })
    const fieldsOk = await form.validateFields(fields).then(() => true).catch(() => false)
    let extraOk = true
    if (key === 'product') {
      if (!intentions.length || !primaryKey) { extraOk = false; if (notify) message.error('请先添加至少一条意向课程，（点击蓝色按钮）') }
    }
    if (key === 'source' && (hasUploading || hasUploadError)) {
      extraOk = false
      if (notify) message.error(hasUploading ? '图片仍在上传，请稍候' : '请删除或重试上传失败的图片')
    }
    const ok = fieldsOk && extraOk
    markStep(key, !ok)
    return ok
  }

  const goNext = async () => {
    if (!await validateStep(steps[current].key, true)) return
    setCurrent(index => Math.min(index + 1, lastIndex))
  }

  /** 点步骤条跳转：往回自由，往前需要逐步校验通过。 */
  const jumpTo = async (target: number) => {
    if (target <= current) return setCurrent(target)
    for (let index = current; index < target; index += 1) {
      if (!await validateStep(steps[index].key, index === current)) return setCurrent(index)
    }
    setCurrent(target)
  }

  const resetAll = () => {
    form.resetFields(); setFiles([]); setIntentions([]); setPrimaryKey(undefined)
    setPendingValues(undefined); setInvalidSteps([]); setCurrent(0)
  }

  const showResult = (result: LeadCreateResult) => {
    if (result.outcome === 'created' || result.outcome === 'activated') {
      return modal.success({
        title: '客资提交成功',
        content: result.leadNo ? `客资编号 ${result.leadNo} 已创建，可在客资列表中查看跟进。` : '客资已创建，可在客资列表中查看跟进。',
        okText: '知道了'
      })
    }
    if (result.outcome === 'review_pending') {
      return modal.info({
        title: '疑似重复，已转入复核',
        content: `本次提交与既有客资疑似重复，已生成复核任务 #${result.reviewId}，请等待复核结果。`,
        okText: '知道了'
      })
    }
    if (result.outcome === 'duplicate_rejected') {
      return modal.warning({
        title: '未创建客资',
        content: `已存在活动客资${result.leadNo ? ` ${result.leadNo}` : ''}，本次提交未创建客资，也未生成复核任务。`,
        okText: '知道了'
      })
    }
    return modal.success({
      title: '历史重复提交已记录',
      content: `本次提交命中历史客资${result.leadNo ? ` ${result.leadNo}` : ''}，已记录提交行为。`,
      okText: '知道了'
    })
  }

  const prepareSubmit = async () => {
    for (const [index, step] of steps.entries()) {
      if (!await validateStep(step.key, false)) {
        setCurrent(index)
        message.error(`「${step.title}」还有未填完的内容`)
        return
      }
    }
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
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
      const idempotencyKey = idempotencyKeyRef.current ?? createIdempotencyKey()
      idempotencyKeyRef.current = idempotencyKey
      const uploadResult = await uploadDeferredFiles(files, api.uploadLeadAttachment, setFiles)
      if (uploadResult.failed) {
        modal.error({ title: '图片上传失败', content: '有图片上传失败，请返回「来源与备注」重试失败项后再提交。', okText: '知道了' })
        return
      }
      const [provinceCode, cityCode] = normalizeLeadAreaPath(values.regionPath)
      const result = await (selfSourced ? api.createSelfSourcedLead : api.createLead)({
        name: values.name.trim(), mobile: values.mobile?.trim() || undefined, wechatId: values.wechatId?.trim() || undefined,
        provinceCode, cityCode,
        intendedProducts: intentions.map(item => ({ spuRef: item.spuRef, skuRef: item.skuRef, spuUnknown: item.spuUnknown, skuUnknown: item.skuUnknown, primary: item.key === primaryKey })),
        sourceChannel: values.sourceChannel, leadCategory: values.leadCategory, remark: values.remark?.trim() || undefined,
        attachments: uploadResult.items.filter(file => file.uploaded)
          .map(file => ({ infraFileId: file.uploaded!.infraFileId })),
        dispatchMode: selfSourced ? 'auto' : values.dispatchMode,
        specifiedSalesUserId: selfSourced ? undefined : values.specifiedSalesUserId,
        newMediaProviderUserId: selfSourced ? values.newMediaProviderUserId : undefined, idempotencyKey
      })
      showResult(result)
      resetAll(); setFiles([]); idempotencyKeyRef.current = undefined
    } catch (error) {
      modal.error({ title: '提交失败', content: error instanceof Error ? error.message : '提交失败，请稍后重试。', okText: '知道了' })
    }
    finally { submittingRef.current = false; setSubmitting(false) }
  }

  const regionLabel = useMemo(() => {
    const path: string[] = pendingValues?.regionPath || []
    if (!path.length) return '—'
    const province = areaOptions.find(item => item.value === path[0])
    const city = province?.children?.find(item => item.value === path[1])
    return [province?.label, city?.label].filter(Boolean).join(' / ') || '—'
  }, [pendingValues, areaOptions])
  const summary = current === lastIndex ? (pendingValues || form.getFieldsValue(true)) : undefined

  return <section className="workspace-page lead-submission-page">
    {remote.error && <Alert showIcon type="error" message={remote.error} action={<Button icon={<ReloadOutlined />} onClick={() => void loadOptions()}>重试</Button>} />}
    {areaState.error && <Alert showIcon type="error" message={areaState.error} action={<Button icon={<ReloadOutlined />} onClick={() => void loadAreas()}>重新加载地区</Button>} />}
    {!areaState.loading && !areaState.error && !areas.length && <Alert showIcon type="warning" message="地区数据尚未配置" />}
    {!remote.loading && !remote.error && !catalog.spus.length && <Alert showIcon type="warning" message="课程目录尚未配置，可选择“未明确课程”继续提交" />}
    <Card variant="borderless" className="lead-form-card"><Spin spinning={remote.loading || areaState.loading} tip="正在读取地区、课程和字典配置">
      <Steps className="lead-form-steps" current={current} size="small" onChange={index => void jumpTo(index)}
        items={steps.map((step, index) => ({
          title: step.title, description: step.description,
          status: invalidSteps.includes(step.key) && index !== current ? 'error' : undefined
        }))} />
      <Form<FormValues> form={form} layout="vertical" initialValues={{ dispatchMode: LEAD_ASSIGNMENT_MODE.AUTO }}>
        {/* 各步用 hidden 收起而不是卸载：保留已填值与校验状态，回退不丢数据。 */}
        <div className="lead-form-step" hidden={current !== 0}>
          <Title level={5}>客户信息</Title><Row gutter={[24, 0]}>
            <Col xs={24} md={12}><Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }, { max: 100 }]}><Input /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="mobile" label="手机号" required={!wechatId?.trim()} extra="手机号、微信号必填其中一个" dependencies={['wechatId']} rules={[{ validator: validateContact }, { pattern: PHONE_PATTERN, message: '手机号格式不正确' }]}><Input maxLength={32} /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="wechatId" label="微信号" required={!mobile?.trim()} dependencies={['mobile']} rules={[{ validator: validateContact }]}><Input maxLength={64} /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="regionPath" label="客户地区" rules={[{ required: true, message: '请选择客户省市' }]}><Cascader options={areaOptions} showSearch placeholder="请选择省 / 市，如果不清楚可填写【其他】" /></Form.Item></Col>
          </Row>
        </div>
        <div className="lead-form-step" hidden={current !== 1}>
          <Title level={5}><span className="required-section-title">意向课程</span></Title>
          <LeadIntendedProductEditor catalog={catalog} value={intentions} primaryKey={primaryKey}
            onChange={next => { setIntentions(next); if (next.length) markStep('product', false) }}
            onPrimaryChange={key => { setPrimaryKey(key); if (key) markStep('product', false) }} />
        </div>
        <div className="lead-form-step" hidden={current !== 2}>
          <Title level={5}>来源与备注</Title><Row gutter={[24, 0]}>
            <Col xs={24} md={12}><Form.Item name="sourceChannel" label="来源渠道" rules={[{ required: true, message: '请选择来源渠道' }]}><Select options={sources} notFoundContent="来源渠道未配置" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="leadCategory" label="客资分类" rules={[{ required: true, message: '请选择客资分类' }]}><Select options={categories} notFoundContent="客资分类未配置" /></Form.Item></Col>
            <Col xs={24}><Form.Item name="remark" label="备注信息"><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item></Col>
            <Col xs={24}><Form.Item label={`附件图片${hasUploading ? '（上传中）' : ''}`} extra="确认提交后上传；最多 9 张，JPG、PNG、WebP，单张不超过 10MB"><DeferredAttachmentPicker value={files} onChange={setFiles} accept="image/jpeg,image/png,image/webp" /></Form.Item></Col>
          </Row>
        </div>
        <div className="lead-form-step" hidden={current !== 3}>
          {!selfSourced && <><Title level={5}>派单方式</Title>
            {/* 卡片式单选：Radio 仍是真正的控件（键盘可达），整卡 onClick 只是放大鼠标命中区。 */}
            <Form.Item name="dispatchMode" rules={[{ required: true, message: '请选择派单模式' }]}>
              <Radio.Group className="lead-dispatch-options">
                {LEAD_ASSIGNMENT_OPTIONS.map(option => <div key={option.value}
                  className={`lead-dispatch-option${assignmentMode === option.value ? ' selected' : ''}`}
                  onClick={() => form.setFieldValue('dispatchMode', option.value)}>
                  <Radio value={option.value}>{option.label}</Radio>
                  <Text type="secondary" className="lead-dispatch-hint">{DISPATCH_MODE_HINTS[option.value]}</Text>
                  {/* 人员下拉长在卡片内部：卡片撑高，但两列布局不变，外面的汇总表不跳。 */}
                  {option.value === LEAD_ASSIGNMENT_MODE.SPECIFIED && assignmentMode === LEAD_ASSIGNMENT_MODE.SPECIFIED
                    && <Form.Item className="lead-dispatch-nested" name="specifiedSalesUserId" preserve={false} rules={[{ required: true, message: '请选择指定销售' }]}>
                      <EmployeeSelect users={sales} loading={salesState.loading} showSearch optionFilterProp="label" placeholder="请选择销售"
                        notFoundContent={salesState.error ? <Button icon={<ReloadOutlined />} onClick={() => void loadSales()}>重新加载</Button> : '暂未配置可指定销售'} />
                    </Form.Item>}
                </div>)}
              </Radio.Group>
            </Form.Item></>}
          {selfSourced && <><Title level={5}>客资提供方</Title><Row gutter={[24, 0]}>
            <Col xs={24} md={12}><Form.Item name="newMediaProviderUserId" preserve={false} extra="选填；提交后不可补选或更改"><EmployeeSelect users={providers} loading={providerState.loading} showSearch optionFilterProp="label" allowClear placeholder="请选择新媒体提供方" notFoundContent={providerState.error ? <Button icon={<ReloadOutlined />} onClick={() => void loadProviders()}>重新加载</Button> : '暂无可选新媒体人员'} /></Form.Item></Col>
          </Row></>}
          <Title level={5}>信息确认</Title>
          <Descriptions className="lead-form-summary" column={{ xs: 1, md: 2 }} size="small" bordered
            items={[
              { key: 'name', label: '姓名', children: summary?.name?.trim() || '—' },
              { key: 'contact', label: '联系方式', children: [summary?.mobile?.trim(), summary?.wechatId?.trim()].filter(Boolean).join(' / ') || '—' },
              { key: 'region', label: '客户地区', children: regionLabel },
              { key: 'source', label: '来源渠道', children: sources.find(item => item.value === summary?.sourceChannel)?.label || '—' },
              { key: 'category', label: '客资分类', children: categories.find(item => item.value === summary?.leadCategory)?.label || '—' },
              {
                key: 'dispatch', label: selfSourced ? '新媒体提供方' : '派单模式',
                children: selfSourced
                  ? (providers.find(item => item.id === summary?.newMediaProviderUserId)?.nickname || '未指定')
                  : summary?.dispatchMode === LEAD_ASSIGNMENT_MODE.SPECIFIED
                    ? `指定销售 · ${sales.find(item => item.id === summary?.specifiedSalesUserId)?.nickname || '未选择'}`
                    : '自动分配'
              },
              {
                key: 'intentions', label: '意向课程', span: { xs: 1, md: 2 },
                children: intentions.length
                  ? <Space size={[4, 4]} wrap>{intentions.map(item => <Tag key={item.key} color={item.key === primaryKey ? 'blue' : undefined}>
                    {item.key === primaryKey ? '主意向 · ' : ''}{item.spuName} · {item.skuName}
                  </Tag>)}</Space>
                  : '—'
              },
              { key: 'files', label: '附件图片', children: files.length ? `${files.length} 张` : '未上传' },
              { key: 'remark', label: '备注信息', span: { xs: 1, md: 2 }, children: summary?.remark?.trim() || '—' }
            ]} />
          <Text type="secondary">提交后客资将进入查重与派单流程，内容不可撤回。</Text>
        </div>
        <div className="lead-form-actions"><Space>
          <Button onClick={resetAll}>重置</Button>
          {current > 0 && <Button onClick={() => setCurrent(index => index - 1)}>上一步</Button>}
          {current < lastIndex && <Button type="primary" disabled={unavailable} onClick={() => void goNext()}>下一步</Button>}
          {current === lastIndex && <IrreversiblePopconfirm action={`提交客资「${pendingValues?.name?.trim() || '当前客户'}」`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}>
            <Button type="primary" icon={<SendOutlined />} loading={submitting} disabled={unavailable || hasUploading} onClick={() => void prepareSubmit()}>提交客资</Button>
          </IrreversiblePopconfirm>}
        </Space></div>
      </Form>
    </Spin></Card>
  </section>
}
