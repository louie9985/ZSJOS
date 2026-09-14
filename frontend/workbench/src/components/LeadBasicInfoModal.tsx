import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, App, Button, Cascader, Form, Input, Modal, Select, Space, Spin } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type AreaNode, type DictData, type LeadCatalog, type LeadAttachment, type ManagedLead } from '../services/api'
import { DICT_TYPE, PHONE_PATTERN } from '../constants'
import { buildLeadAreaOptions, normalizeLeadAreaPath, resolveLeadAreaPath } from '../services/area'
import LeadIntendedProductEditor, { selectionFromManagedProduct, type IntendedProductSelection } from './LeadIntendedProductEditor'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'
import { createIdempotencyKey } from '../services/idempotency'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'

type Values = {
  name: string; mobile?: string; wechatId?: string; regionPath: string[]
  leadCategory?: string; reason?: string
}

type ConfigSource = 'area' | 'category' | 'catalog'

const configLabels: Record<ConfigSource, string> = {
  area: '地区配置', category: '客资分类', catalog: '意向课程配置'
}

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '加载失败'

function LeadBasicInfoModalImpl({ lead, open, onClose, onChanged, onDirtyChange, submitterOnly = false }: {
  lead: ManagedLead; open: boolean; onClose: () => void; onChanged: () => void
  onDirtyChange?: (dirty: boolean) => void; submitterOnly?: boolean
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const supplementAttempt = useRef<{ content: string; key: string } | undefined>(undefined)
  const mobile = Form.useWatch('mobile', form)
  const wechatId = Form.useWatch('wechatId', form)
  const [areas, setAreas] = useState<AreaNode[]>([])
  const [categories, setCategories] = useState<DictData[]>([])
  const [catalog, setCatalog] = useState<LeadCatalog>({ categoryTree: [], spus: [], skus: [] })
  const [loadingSources, setLoadingSources] = useState<Record<ConfigSource, boolean>>({
    area: false, category: false, catalog: false
  })
  const [saving, setSaving] = useState(false)
  const [configErrors, setConfigErrors] = useState<Partial<Record<ConfigSource, string>>>({})
  const [dirty, setDirty] = useState(false)
  const [products, setProducts] = useState<IntendedProductSelection[]>([])
  const [primaryKey, setPrimaryKey] = useState<string>()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<Values>()
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])

  const load = async () => {
    setLoadingSources({ area: true, category: true, catalog: true })
    setConfigErrors({})
    const [areaResult, categoryResult, catalogResult] = await Promise.allSettled([
      api.areaTree(), api.dictDataByType(DICT_TYPE.LEAD_CATEGORY), api.leadCatalog()
    ])
    const errors: Partial<Record<ConfigSource, string>> = {}
    if (areaResult.status === 'fulfilled') {
      setAreas(areaResult.value)
      const path = resolveLeadAreaPath(areaResult.value, lead.provinceCode, lead.cityCode, lead.provinceName, lead.cityName)
      form.setFieldValue('regionPath', path.length ? path : undefined)
    } else {
      setAreas([])
      form.setFieldValue('regionPath', undefined)
      errors.area = errorMessage(areaResult.reason)
    }
    if (categoryResult.status === 'fulfilled') setCategories(categoryResult.value)
    else { setCategories([]); errors.category = errorMessage(categoryResult.reason) }
    if (catalogResult.status === 'fulfilled') setCatalog(catalogResult.value)
    else {
      setCatalog({ categoryTree: [], spus: [], skus: [] })
      errors.catalog = errorMessage(catalogResult.reason)
    }
    setConfigErrors(errors)
    setLoadingSources({ area: false, category: false, catalog: false })
  }

  useEffect(() => {
    if (!open) return
    void load()
    const products = lead.intendedProducts || []
    form.setFieldsValue({
      name: lead.submittedName, mobile: lead.submittedMobile, wechatId: lead.submittedWechatId,
      regionPath: undefined, leadCategory: lead.leadCategory,
      reason: ''
    })
    supplementAttempt.current = undefined
    const selections = products.map(selectionFromManagedProduct)
    setProducts(selections)
    setPrimaryKey(selections[products.findIndex(item => item.primary)]?.key)
    setDirty(false)
  }, [form, lead, open])

  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])
  const loading = Object.values(loadingSources).some(Boolean)
  const regionSnapshot = [lead.provinceName, lead.cityName].filter(Boolean).join(' / ')
  const close = () => {
    if (dirty && !window.confirm('基础信息尚未保存，确定关闭吗？')) return
    setConfirmOpen(false)
    setPendingValues(undefined)
    setDirty(false)
    onClose()
  }
  const prepareSubmit = async () => {
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    if (!products.length || !primaryKey || !products.some(item => item.key === primaryKey)) {
      message.warning('请选择至少一项意向课程并指定主意向'); return
    }
    setPendingValues(values); setConfirmOpen(true)
  }
  const submit = async () => {
    const values = pendingValues
    setConfirmOpen(false)
    if (!values) return
    setSaving(true)
    try {
      const [provinceCode, cityCode] = normalizeLeadAreaPath(values.regionPath)
      const intendedProducts = products.map(item => ({ spuRef: item.spuRef, skuRef: item.skuRef, spuUnknown: item.spuUnknown,
        skuUnknown: item.skuUnknown, primary: item.key === primaryKey }))
      await api.updateLeadBasicInfo(lead.id, {
        name: values.name.trim(), mobile: values.mobile?.trim() || undefined, wechatId: values.wechatId?.trim() || undefined,
        provinceCode, cityCode, leadCategory: values.leadCategory,
        intendedProducts, reason: values.reason!.trim()
      })
      setDirty(false); message.success('基础信息已更新'); onClose(); onChanged()
    } catch (saveError) { message.error(saveError instanceof Error ? saveError.message : '保存失败') }
    finally { setSaving(false) }
  }

  return <Modal title={submitterOnly ? '补充客资资料' : '修改基础信息'} open={open} onCancel={close} footer={null} width={760} destroyOnHidden>
    {Object.keys(configErrors).length > 0 && <Alert type="error" showIcon message="部分配置加载失败"
      description={<Space direction="vertical" size={0}>{(Object.entries(configErrors) as Array<[ConfigSource, string]>).map(([source, detail]) =>
        <span key={source}>{configLabels[source]}：{detail}</span>)}</Space>}
      action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void load()}>重试</Button>}/>}
    <Spin spinning={loading}>
      <Form form={form} layout="vertical" onValuesChange={() => setDirty(true)}>
        {!submitterOnly && <div className="follow-up-field-grid">
          <Form.Item name="name" label="姓名" rules={[{ required: true }, { max: 100 }]}><Input/></Form.Item>
          <Form.Item name="mobile" label="手机号" required={!wechatId?.trim()} extra="手机号、微信号必填其中一个" dependencies={['wechatId']} rules={[{ pattern: PHONE_PATTERN, message: '手机号格式不正确' }, { validator: (_, value) => value?.trim() || form.getFieldValue('wechatId')?.trim() ? Promise.resolve() : Promise.reject(new Error('请填写手机号或微信号')) }]}><Input maxLength={32}/></Form.Item>
          <Form.Item name="wechatId" label="微信号" required={!mobile?.trim()} dependencies={['mobile']} rules={[{ validator: (_, value) => value?.trim() || form.getFieldValue('mobile')?.trim() ? Promise.resolve() : Promise.reject(new Error('请填写手机号或微信号')) }]}><Input maxLength={64}/></Form.Item>
        </div>}
        <Form.Item name="regionPath" label="所在地区" rules={[{ required: true, message: '请选择所在地区' }]}>
          <Cascader options={areaOptions} showSearch disabled={loadingSources.area || Boolean(configErrors.area)}
            placeholder={configErrors.area ? (regionSnapshot || '地区配置加载失败') : (regionSnapshot ? `${regionSnapshot}（请选择）` : '请选择省 / 市，如果不清楚可填写【其他】')}/>
        </Form.Item>
        <Form.Item name="leadCategory" label="客资分类"><Select allowClear loading={loadingSources.category}
          disabled={loadingSources.category || Boolean(configErrors.category)} options={categories.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
        <Form.Item label="意向课程" required>
          <LeadIntendedProductEditor catalog={catalog} value={products} primaryKey={primaryKey}
            disabled={loadingSources.catalog || Boolean(configErrors.catalog)} onChange={next => { setProducts(next); setDirty(true) }}
            onPrimaryChange={next => { setPrimaryKey(next); setDirty(true) }}/>
        </Form.Item>
        <Form.Item name="reason" label={submitterOnly ? '补充备注' : '修改原因'} rules={[{ required: !submitterOnly }, { max: submitterOnly ? 1000 : 500 }]}><Input.TextArea rows={3} maxLength={submitterOnly ? 1000 : 500} showCount/></Form.Item>
        <Space><IrreversiblePopconfirm action={`${submitterOnly ? '补充' : '修改'}客资「${lead.submittedName}」的资料`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" loading={saving}
          disabled={loading || Boolean(configErrors.area) || Boolean(configErrors.catalog)} onClick={() => void prepareSubmit()}>保存</Button></IrreversiblePopconfirm><Button onClick={close}>取消</Button></Space>
      </Form>
    </Spin>
  </Modal>
}

function SubmitterSupplementModal({ lead, open, onClose, onChanged }: { lead: ManagedLead; open: boolean; onClose: () => void; onChanged: () => void }) {
  const { message } = App.useApp()
  const [remark, setRemark] = useState('')
  const [attachments, setAttachments] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const [saving, setSaving] = useState(false)
  const [attempt, setAttempt] = useState<{ content: string; key: string }>()
  useEffect(() => { if (open) { setRemark(''); setAttachments([]); setAttempt(undefined) } }, [open, lead.id])
  const submit = async () => {
    const normalized = remark.trim()
    if (!normalized) { message.warning('请填写补充说明'); return }
    setSaving(true)
    try {
      const uploaded = await uploadDeferredFiles(attachments, api.uploadLeadAttachment, setAttachments)
      if (uploaded.failed) { message.error('有图片上传失败，请重试失败项'); return }
      const payload = { remark: normalized, attachments: uploaded.items.filter(item => item.uploaded).map(item => ({ infraFileId: item.uploaded!.infraFileId })) }
      const content = JSON.stringify(payload)
      const current = attempt?.content === content ? attempt : { content, key: createIdempotencyKey() }
      if (!attempt || attempt.content !== content) setAttempt(current)
      await api.supplementLead(lead.id, { ...payload, idempotencyKey: current.key })
      message.success('补充资料已提交'); onClose(); onChanged()
    } catch (error) { message.error(error instanceof Error ? error.message : '提交失败') }
    finally { setSaving(false) }
  }
  return <Modal title="补充资料" open={open} onCancel={onClose} onOk={() => void submit()} okText="提交补充" confirmLoading={saving} destroyOnHidden>
    <Form layout="vertical">
      <Form.Item label="补充说明" required help="请填写本次需要补充的客户信息">
        <Input.TextArea value={remark} onChange={event => setRemark(event.target.value)} rows={6} maxLength={1000} showCount placeholder="请输入补充说明" disabled={saving}/>
      </Form.Item>
      <Form.Item label="图片附件（可选）"><DeferredAttachmentPicker value={attachments} onChange={setAttachments} accept="image/jpeg,image/png,image/webp" maxCount={9} disabled={saving}/></Form.Item>
    </Form>
  </Modal>
}

export default function LeadBasicInfoModal(props: { lead: ManagedLead; open: boolean; onClose: () => void; onChanged: () => void; onDirtyChange?: (dirty: boolean) => void; submitterOnly?: boolean }) {
  return props.submitterOnly
    ? <SubmitterSupplementModal lead={props.lead} open={props.open} onClose={props.onClose} onChanged={props.onChanged}/>
    : <LeadBasicInfoModalImpl {...props}/>
}
