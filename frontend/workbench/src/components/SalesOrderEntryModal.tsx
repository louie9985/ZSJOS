import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Cascader, Col, DatePicker, Divider, Form, Input, InputNumber, Modal, Row, Segmented, Select, Space, Spin, Typography, message } from 'antd'
import { CopyOutlined, DeleteOutlined, LinkOutlined, PlusOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, type AreaNode, type CollectionMode, type DictData, type LeadCatalog, type PurchaseIntent, type PurchaseIntentDraftRequest, type SalesOrder, type SalesOrderSubmitRequest, type SalesOrderVoucher } from '../services/api'
import { createIdempotencyKey } from '../services/idempotency'
import { DICT_TYPE, PHONE_PATTERN } from '../constants'
import { buildLeadAreaOptions, normalizeLeadAreaPath, resolveLeadAreaPath } from '../services/area'
import { validateSalesOrderSubmission } from '../services/salesOrder'
import SalesOrderCoursePicker from './SalesOrderCoursePicker'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'

type Values = {
  repurchaseReason?: string
  buyerName?: string; studentName: string; studentNature: string; mobile?: string; wechatId?: string; regionPath: string[]
  agreedExamTime?: string; classType?: string; servicePeriod: string; studentSource: string
  customerPaidAt: Dayjs; feeMode: string; paymentMethod: string; remark?: string
  specialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ courseKey?: string; actualAmount?: number }>
}

const emptyCatalog: LeadCatalog = { categoryTree: [], spus: [], skus: [] }
const errorText = (error: unknown) => error instanceof Error ? error.message : '加载失败'
export function getPaymentLinkActionLabel(
  collectionMode: CollectionMode,
  orderId: number | undefined,
  purchaseIntent?: Pick<PurchaseIntent, 'paymentStatus' | 'paymentUrl'>,
) {
  if (orderId || collectionMode !== 'online_link') return null
  if (purchaseIntent?.paymentStatus === 'expired' || purchaseIntent?.paymentStatus === 'closed') return '重新生成支付链接'
  if (purchaseIntent?.paymentStatus) return null
  return purchaseIntent?.paymentUrl ? null : '生成支付链接'
}

function findRegion(areas: AreaNode[], path: string[]) {
  const province = areas.find(item => item.selectionCode === path[0])
  const city = province?.children?.find(item => item.selectionCode === path[1])
  return { provinceName: province?.name || '', cityName: city?.name || '' }
}

export type SalesOrderEntryLead = {
  id: number; personId?: number; submittedName: string; submittedMobile?: string; submittedWechatId?: string
  provinceCode?: string; provinceName?: string; cityCode?: string; cityName?: string
  primaryProduct?: { spuRef?: string; skuRef?: string }
}

export default function SalesOrderEntryModal({ lead, orderId, repurchase, externalCustomer, studentRepurchase, open, onClose, onSubmitted }: {
  lead: SalesOrderEntryLead; orderId?: number; repurchase?: boolean
  externalCustomer?: { customerName: string; customerMobile?: string; customerWechatId?: string }
  studentRepurchase?: boolean
  open: boolean; onClose: () => void; onSubmitted: (orderId: number) => void
}) {
  const [form] = Form.useForm<Values>()
  const mobile = Form.useWatch('mobile', form)
  const wechatId = Form.useWatch('wechatId', form)
  const [areas, setAreas] = useState<AreaNode[]>([])
  const [catalog, setCatalog] = useState<LeadCatalog>(emptyCatalog)
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({})
  const [loading, setLoading] = useState(false)
  const { submitting: saving, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [loadError, setLoadError] = useState('')
  const [vouchers, setVouchers] = useState<DeferredUploadItem<SalesOrderVoucher>[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<Values>()
  const [orderNo, setOrderNo] = useState<string>()
  const [collectionMode, setCollectionMode] = useState<CollectionMode>('offline_paid')
  const [purchaseIntent, setPurchaseIntent] = useState<PurchaseIntent>()
  const [draftSaving, setDraftSaving] = useState(false)

  const items = Form.useWatch('items', form) || []
  const total = items.reduce((sum, item) => sum + Number(item?.actualAmount || 0), 0)
  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])
  const paymentLinkActionLabel = getPaymentLinkActionLabel(collectionMode, orderId, purchaseIntent)
  const purchaseType: PurchaseIntentDraftRequest['purchaseType'] = externalCustomer ? 'external_repurchase'
    : studentRepurchase ? 'student_repurchase' : repurchase ? 'lead_repurchase' : 'lead_first_purchase'
  const purchaseSource = { purchaseType, leadId: externalCustomer || studentRepurchase ? undefined : lead.id,
    personId: studentRepurchase ? lead.id : lead.personId,
    sourceKey: `${purchaseType}:${lead.id}:${lead.submittedMobile || lead.submittedWechatId || lead.submittedName}` }

  const load = async () => {
    setLoading(true); setLoadError('')
    const dictTypes = [DICT_TYPE.ORDER_STUDENT_NATURE, DICT_TYPE.ORDER_SERVICE_PERIOD, DICT_TYPE.ORDER_STUDENT_SOURCE, DICT_TYPE.ORDER_FEE_MODE, DICT_TYPE.ORDER_PAYMENT_METHOD]
    try {
      const [areaResult, catalogResult, orderResult, ...dictResults] = await Promise.all([
        api.areaTree(), api.salesOrderCatalog(), orderId ? api.salesOrder(orderId) : Promise.resolve(undefined),
        ...dictTypes.map(type => api.dictDataByType(type))
      ])
      setAreas(areaResult as AreaNode[]); setCatalog(catalogResult as LeadCatalog)
      setDicts(Object.fromEntries(dictTypes.map((type, index) => [type, dictResults[index] as DictData[]])))
      const order = orderResult as SalesOrder | undefined
      setOrderNo(order?.orderNo)
      const regionPath = resolveLeadAreaPath(areaResult as AreaNode[], order?.provinceCode || lead.provinceCode,
        order?.cityCode || lead.cityCode, order?.provinceName || lead.provinceName, order?.cityName || lead.cityName)
      const primary = lead.primaryProduct?.spuRef && lead.primaryProduct.skuRef
        ? `${lead.primaryProduct.spuRef}::${lead.primaryProduct.skuRef}` : undefined
      const hasPrimary = primary && (catalogResult as LeadCatalog).skus.some(sku => `${sku.spuRef}::${sku.skuRef}` === primary)
      form.setFieldsValue({
        buyerName: order?.buyerName, studentName: order?.studentName || lead.submittedName,
        studentNature: order?.studentNature, mobile: order?.studentMobile || lead.submittedMobile,
        wechatId: order?.studentWechatId || lead.submittedWechatId, regionPath: regionPath.length ? regionPath : undefined,
        agreedExamTime: order?.agreedExamTime, classType: order?.classType, servicePeriod: order?.servicePeriod,
        studentSource: order?.studentSource, customerPaidAt: order ? dayjs(order.customerPaidAt) : dayjs(),
        feeMode: order?.feeMode, paymentMethod: order?.paymentMethod, remark: order?.remark,
        specialRequirements: order?.studentSpecialRequirements, materialDeliveryContact: order?.materialDeliveryContact,
        items: order?.items.map(item => ({ courseKey: `${item.productRef}::${item.skuRef}`, actualAmount: item.actualAmount }))
          || [{ courseKey: hasPrimary ? primary : undefined, actualAmount: undefined }]
      })
      setVouchers((order?.paymentVouchers || []).map(file => ({ uid: String(file.infraFileId), name: file.originalName,
        type: file.contentType, status: 'done', url: file.fileUrl, uploaded: file })))
      if (!orderId) {
        const current = await api.currentPurchaseIntent(purchaseSource)
        setPurchaseIntent(current); setCollectionMode(current?.collectionMode || 'offline_paid')
        if (current?.draft) {
          const draft = current.draft as Partial<Values> & { customerPaidAt?: number }
          form.setFieldsValue({ ...draft, customerPaidAt: draft.customerPaidAt ? dayjs(draft.customerPaidAt) : undefined } as Partial<Values>)
        }
      } else { setPurchaseIntent(undefined); setCollectionMode('offline_paid') }
    } catch (error) {
      setAreas([]); setCatalog(emptyCatalog); setDicts({}); setLoadError(errorText(error))
    } finally { setLoading(false) }
  }

  useEffect(() => {
    if (!open) return
    resetIntent()
    setConfirmOpen(false); setPendingValues(undefined)
    form.resetFields(); setVouchers([]); void load()
  }, [open, lead.id, lead.submittedName, lead.submittedMobile, lead.submittedWechatId, orderId, resetIntent])

  const close = () => { setConfirmOpen(false); setPendingValues(undefined); onClose() }

  const options = (type: string) => (dicts[type] || []).map(item => ({ value: item.value, label: item.label }))
  const buildDraftRequest = (values: Partial<Values>, idempotencyKey: string): PurchaseIntentDraftRequest | undefined => {
    const draftItems = (values.items || []).flatMap(item => {
      if (!item.courseKey || item.actualAmount == null) return []
      const [spuRef, skuRef] = item.courseKey.split('::')
      return spuRef && skuRef ? [{ spuRef, skuRef, actualAmount: Number(item.actualAmount) }] : []
    })
    const draftTotal = draftItems.reduce((sum, item) => sum + item.actualAmount, 0)
    if (!draftItems.length || draftTotal <= 0) { message.warning('保存草稿前请至少选择一个课程并填写有效金额'); return undefined }
    return { ...purchaseSource, id: purchaseIntent?.id, version: purchaseIntent?.version, collectionMode,
      draft: { ...values, customerPaidAt: values.customerPaidAt?.valueOf(), studentMobile: values.mobile, studentWechatId: values.wechatId },
      items: draftItems, totalAmount: Number(draftTotal.toFixed(2)), idempotencyKey }
  }
  const saveDraft = async (createLink = false) => {
    const request = buildDraftRequest(form.getFieldsValue(true), createIdempotencyKey())
    if (!request) return undefined
    setDraftSaving(true)
    try {
      const saved = createLink ? await api.createPurchasePaymentLink(request) : await api.savePurchaseIntentDraft(request)
      setPurchaseIntent(saved); setCollectionMode(saved.collectionMode)
      message.success(createLink ? '支付链接已生成' : '草稿已保存')
      return saved
    } catch (error) { message.error(errorText(error)); return undefined }
    finally { setDraftSaving(false) }
  }
  const validateContact = () => form.getFieldValue('mobile')?.trim() || form.getFieldValue('wechatId')?.trim()
    ? Promise.resolve() : Promise.reject(new Error('请填写手机号或微信号'))
  const prepareSubmit = async () => {
    if (!orderId && collectionMode === 'online_link' && purchaseIntent?.paymentStatus !== 'paid') {
      message.warning('线上支付尚未确认到账，不能提交审批'); return
    }
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    const validationError = validateSalesOrderSubmission(values.mobile, values.wechatId, total, vouchers.length)
    if (validationError) { message.warning(validationError); return }
    setPendingValues(values); setConfirmOpen(true)
  }
  const submit = async () => {
    const values = pendingValues
    setConfirmOpen(false)
    if (!values) return
    const [provinceCode, cityCode] = normalizeLeadAreaPath(values.regionPath)
    const region = findRegion(areas, values.regionPath)
    if (!region.provinceName) { message.warning('请选择有效省市'); return }
    await runSubmission(async ({ idempotencyKey, complete }) => {
      const request: SalesOrderSubmitRequest = {
        buyerName: values.buyerName?.trim() || undefined, studentName: values.studentName.trim(), studentNature: values.studentNature,
        studentMobile: values.mobile?.trim() || undefined, studentWechatId: values.wechatId?.trim() || undefined,
        provinceCode, provinceName: region.provinceName, cityCode, cityName: region.cityName,
        agreedExamTime: values.agreedExamTime?.trim() || undefined, classType: values.classType?.trim() || undefined,
        servicePeriod: values.servicePeriod, studentSource: values.studentSource, customerPaidAt: values.customerPaidAt.valueOf(),
        feeMode: values.feeMode, paymentMethod: values.paymentMethod, remark: values.remark?.trim() || undefined,
        studentSpecialRequirements: values.specialRequirements?.trim() || undefined,
        materialDeliveryContact: values.materialDeliveryContact?.trim() || undefined,
        items: values.items.map(item => { const [spuRef, skuRef] = item.courseKey!.split('::'); return { spuRef, skuRef, actualAmount: Number(item.actualAmount) } }),
        paymentVouchers: [], idempotencyKey
      }
      try {
        if (!orderId) {
          const draftRequest = buildDraftRequest(values, `purchase:${idempotencyKey}`)
          if (!draftRequest) return
          const saved = await api.savePurchaseIntentDraft(draftRequest)
          setPurchaseIntent(saved); request.purchaseIntentId = saved.id
        }
        const uploadResult = await uploadDeferredFiles(vouchers, api.uploadSalesOrderVoucher, setVouchers)
        if (uploadResult.failed) { message.error('有缴费凭证上传失败，请重试失败项'); return }
        request.paymentVouchers = uploadResult.items.filter(file => file.uploaded).map(file => ({ infraFileId: file.uploaded!.infraFileId }))
        if (repurchase) {
          const repurchaseReason = values.repurchaseReason!.trim()
          let submittedOrderId: number
          if (externalCustomer) {
            submittedOrderId = await api.submitExternalRepurchase({ ...externalCustomer, repurchaseReason, order: request })
          } else if (studentRepurchase) {
            submittedOrderId = await api.submitStudentRepurchase(lead.id, { customerName: request.studentName, customerMobile: request.studentMobile, customerWechatId: request.studentWechatId, repurchaseReason, order: request })
          } else {
            submittedOrderId = await api.submitSystemRepurchase(lead.id, repurchaseReason, request)
          }
          message.success('复购订单已提交审批')
          onSubmitted(submittedOrderId)
        } else if (orderId) {
          const submittedOrderId = await api.resubmitSalesOrder(orderId, request)
          message.success('成交订单已补正并重新提交')
          onSubmitted(submittedOrderId)
        } else {
          const submittedOrderId = await api.submitSalesOrder(lead.id, request)
          message.success('成交订单已提交')
          onSubmitted(submittedOrderId)
        }
        complete()
      }
      catch (error) { message.error(errorText(error)) }
    })
  }

  return <Modal title={repurchase ? '录入复购订单' : orderId ? '补正成交' : '录入成交'} open={open} onCancel={close} footer={null} width={980} destroyOnHidden>
    {loadError && <Alert type="error" showIcon message="成交配置加载失败" description={loadError}
      action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void load()}>重试</Button>}/>}
    <Spin spinning={loading}>
      <Form form={form} layout="vertical" disabled={Boolean(loadError) || saving || draftSaving}>
        {!orderId && <><Divider titlePlacement="start">收款路径</Divider>
          <Segmented block value={collectionMode} disabled={Boolean(purchaseIntent?.paymentLocked)} onChange={value => setCollectionMode(value as CollectionMode)}
            options={[{ label: '线上支付链接', value: 'online_link' }, { label: '线下已支付', value: 'offline_paid' }]}/>
          {collectionMode === 'online_link' && purchaseIntent?.paymentUrl && <Alert style={{ marginTop: 12 }} showIcon
            type={purchaseIntent.paymentStatus === 'paid' ? 'success' : purchaseIntent.paymentStatus === 'expired' || purchaseIntent.paymentStatus === 'closed' ? 'warning' : 'info'}
            message={purchaseIntent.paymentStatus === 'paid' ? '通联已确认到账' : purchaseIntent.paymentStatus === 'expired' || purchaseIntent.paymentStatus === 'closed' ? '支付链接已失效，可重新生成' : '支付链接已生成'}
            description={<Space direction="vertical" style={{ width: '100%' }}><Typography.Text copyable>{purchaseIntent.paymentUrl}</Typography.Text>
              <Typography.Text type="secondary">状态：{purchaseIntent.paymentStatus}，有效期至 {purchaseIntent.paymentExpiresAt ? dayjs(purchaseIntent.paymentExpiresAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Typography.Text>
              <Space><Button size="small" icon={<CopyOutlined/>} onClick={() => void navigator.clipboard.writeText(purchaseIntent.paymentUrl!)}>复制链接</Button>
                <Button size="small" icon={<ReloadOutlined/>} onClick={async () => setPurchaseIntent(await api.refreshPurchasePayment(purchaseIntent.id))}>刷新状态</Button></Space>
            </Space>}/>}</>}
         {repurchase && <Form.Item name="repurchaseReason" label="复购说明"
           rules={[{ required: true, whitespace: true, message: '请填写复购说明' }, { max: 1000 }]}>
           <Input.TextArea rows={3} maxLength={1000} showCount/>
         </Form.Item>}
        <Divider titlePlacement="start">学员信息</Divider>
        <Row gutter={16}>
          <Col xs={24} md={8}><Form.Item name="buyerName" label="购买方" extra="不填则默认同学员姓名"><Input maxLength={100}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="studentName" label="学员姓名" rules={[{ required: true }, { max: 100 }]}><Input disabled={Boolean(purchaseIntent?.paymentLocked)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="studentNature" label="学员性质" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_STUDENT_NATURE)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="mobile" label="手机号" required={!wechatId?.trim()} extra="手机号、微信号必填其中一个" dependencies={['wechatId']} rules={[{ pattern: PHONE_PATTERN, message: '手机号格式不正确' }, { validator: validateContact }]}><Input maxLength={32} disabled={Boolean(purchaseIntent?.paymentLocked)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="wechatId" label="微信号" required={!mobile?.trim()} dependencies={['mobile']} rules={[{ validator: validateContact }]}><Input maxLength={64} disabled={Boolean(purchaseIntent?.paymentLocked)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="regionPath" label="所在省市" rules={[{ required: true, message: '请选择所在省市' }]}><Cascader options={areaOptions} showSearch placeholder="请选择省 / 市，如果不清楚可填写【其他】"/></Form.Item></Col>
        </Row>
        <Divider titlePlacement="start">报名与服务</Divider>
        <Row gutter={16}>
          <Col xs={24} md={12}><Form.Item name="agreedExamTime" label="商定考试时间"><Input maxLength={100}/></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="classType" label="开通班种"><Input maxLength={100}/></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="servicePeriod" label="服务周期" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_SERVICE_PERIOD)}/></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="studentSource" label="学生来源" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_STUDENT_SOURCE)}/></Form.Item></Col>
        </Row>
        <Divider titlePlacement="start">成交课程与金额</Divider>
        <Form.List name="items" rules={[{ validator: async (_, value) => value?.length ? undefined : Promise.reject(new Error('至少添加一个成交课程')) }]}>
          {(fields, { add, remove }, { errors }) => <>
            {fields.map((field, index) => <Row gutter={12} key={field.key} align="top">
              <Col flex="auto"><Form.Item name={[field.name, 'courseKey']} label={`成交课程 ${index + 1}`} rules={[{ required: true, message: '请选择成交课程' }]}><SalesOrderCoursePicker catalog={catalog} disabled={Boolean(purchaseIntent?.paymentLocked)}/></Form.Item></Col>
              <Col xs={24} md={6}><Form.Item name={[field.name, 'actualAmount']} label={`实际成交金额 ${index + 1}`} rules={[{ required: true, message: '请输入金额' }]}><InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} disabled={Boolean(purchaseIntent?.paymentLocked)}/></Form.Item></Col>
              <Col><Button aria-label="删除成交课程" title="删除成交课程" icon={<DeleteOutlined/>} danger disabled={fields.length === 1} onClick={() => remove(field.name)} style={{ marginTop: 30 }}/></Col>
            </Row>)}
            <Button type="dashed" icon={<PlusOutlined/>} onClick={() => add({ actualAmount: 0 })}>添加成交课程</Button><Form.ErrorList errors={errors}/>
          </>}
        </Form.List>
        <div style={{ textAlign: 'right', marginTop: 12 }}><Typography.Text strong>订单总金额：¥{total.toFixed(2)}</Typography.Text></div>
        <Divider titlePlacement="start">订单与缴费</Divider>
        <Row gutter={16}>
          <Col xs={24} md={8}><Form.Item name="customerPaidAt" label="客户付款时间" rules={[{ required: true }]}><DatePicker showTime style={{ width: '100%' }}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="feeMode" label="缴费方式" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_FEE_MODE)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="paymentMethod" label="支付方式" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_PAYMENT_METHOD)}/></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="remark" label="订单备注"><Input.TextArea rows={3} maxLength={1000} showCount/></Form.Item></Col>
          <Col xs={24} md={12}><Form.Item name="specialRequirements" label="学生特殊要求"><Input.TextArea rows={3} maxLength={1000} showCount/></Form.Item></Col>
          <Col xs={24}><Form.Item name="materialDeliveryContact" label="教材邮递联系"><Input.TextArea rows={3} maxLength={1000} showCount placeholder="收件人、联系电话和邮寄地址"/></Form.Item></Col>
          <Col xs={24}><Form.Item label={`缴费凭证${vouchers.some(file => file.status === 'uploading') ? '（上传中）' : ''}`} required extra="确认提交后上传；所有订单至少一份，最多 6 个 JPG、PNG、WebP 或 PDF">
            <DeferredAttachmentPicker value={vouchers} onChange={setVouchers} accept="image/jpeg,image/png,image/webp,application/pdf" imageOnly={false} maxCount={6}/>
          </Form.Item></Col>
        </Row>
        <Space wrap>{!orderId && <Button icon={<SaveOutlined/>} loading={draftSaving} onClick={() => void saveDraft(false)}>保存草稿</Button>}
          {paymentLinkActionLabel && <Button type="primary" icon={<LinkOutlined/>} loading={draftSaving} onClick={() => void saveDraft(true)}>{paymentLinkActionLabel}</Button>}
          <IrreversiblePopconfirm action={orderId ? `重新提交成交订单「${orderNo || orderId}」审批` : `提交「${lead.submittedName}」的成交订单审批`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" loading={saving} onClick={() => void prepareSubmit()}>{orderId ? '重新提交审批' : '提交审批'}</Button></IrreversiblePopconfirm><Button onClick={close}>取消</Button></Space>
      </Form>
    </Spin>
  </Modal>
}
