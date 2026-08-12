import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Cascader, Col, DatePicker, Divider, Form, Input, InputNumber, Modal, Row, Select, Space, Spin, Typography, message } from 'antd'
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, type AreaNode, type DictData, type LeadCatalog, type SalesOrder, type SalesOrderSubmitRequest, type SalesOrderVoucher } from '../services/api'
import { DICT_TYPE, PHONE_PATTERN } from '../constants'
import { buildLeadAreaOptions, normalizeLeadAreaPath, resolveLeadAreaPath } from '../services/area'
import { validateSalesOrderSubmission } from '../services/salesOrder'
import SalesOrderCoursePicker from './SalesOrderCoursePicker'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'

type Values = {
  buyerName?: string; studentName: string; studentNature: string; mobile?: string; wechatId?: string; regionPath: string[]
  agreedExamTime?: string; classType?: string; servicePeriod: string; studentSource: string
  customerPaidAt: Dayjs; feeMode: string; paymentMethod: string; remark?: string
  specialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ courseKey?: string; actualAmount?: number }>
}

const emptyCatalog: LeadCatalog = { categoryTree: [], spus: [], skus: [] }
const errorText = (error: unknown) => error instanceof Error ? error.message : '加载失败'

function findRegion(areas: AreaNode[], path: string[]) {
  const province = areas.find(item => item.selectionCode === path[0])
  const city = province?.children?.find(item => item.selectionCode === path[1])
  return { provinceName: province?.name || '', cityName: city?.name || '' }
}

export type SalesOrderEntryLead = {
  id: number; submittedName: string; submittedMobile?: string; submittedWechatId?: string
  provinceCode?: string; provinceName?: string; cityCode?: string; cityName?: string
  primaryProduct?: { spuRef?: string; skuRef?: string }
}

export default function SalesOrderEntryModal({ lead, orderId, repurchase, externalCustomer, open, onClose, onSubmitted }: {
  lead: SalesOrderEntryLead; orderId?: number; repurchase?: boolean
  externalCustomer?: { customerName: string; customerMobile?: string; customerWechatId?: string }
  open: boolean; onClose: () => void; onSubmitted: (orderId: number) => void
}) {
  const [form] = Form.useForm<Values>()
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
  const [repurchaseReason, setRepurchaseReason] = useState('')

  const items = Form.useWatch('items', form) || []
  const total = items.reduce((sum, item) => sum + Number(item?.actualAmount || 0), 0)
  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])

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
    } catch (error) {
      setAreas([]); setCatalog(emptyCatalog); setDicts({}); setLoadError(errorText(error))
    } finally { setLoading(false) }
  }

  useEffect(() => {
    if (!open) return
    resetIntent()
    setConfirmOpen(false); setPendingValues(undefined)
    form.resetFields(); setVouchers([]); setRepurchaseReason(''); void load()
  }, [open, lead.id, orderId, resetIntent])

  const close = () => { setConfirmOpen(false); setPendingValues(undefined); onClose() }

  const options = (type: string) => (dicts[type] || []).map(item => ({ value: item.value, label: item.label }))
  const validateContact = () => form.getFieldValue('mobile')?.trim() || form.getFieldValue('wechatId')?.trim()
    ? Promise.resolve() : Promise.reject(new Error('请填写手机号或微信号'))
  const prepareSubmit = async () => {
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
        const uploadResult = await uploadDeferredFiles(vouchers, api.uploadSalesOrderVoucher, setVouchers)
        if (uploadResult.failed) { message.error('有缴费凭证上传失败，请重试失败项'); return }
        request.paymentVouchers = uploadResult.items.filter(file => file.uploaded).map(file => ({ infraFileId: file.uploaded!.infraFileId }))
        if (repurchase) {
          if (!repurchaseReason.trim()) { message.warning('请填写复购说明'); return }
          const submittedOrderId = externalCustomer
            ? await api.submitExternalRepurchase({ ...externalCustomer, repurchaseReason: repurchaseReason.trim(), order: request })
            : await api.submitSystemRepurchase(lead.id, repurchaseReason.trim(), request)
          message.success('复购订单已提交双中心审批')
          onSubmitted(submittedOrderId)
        } else if (orderId) {
          await api.resubmitSalesOrder(orderId, request)
          message.success('成交订单已补正并重新提交会签')
          onSubmitted(orderId)
        } else {
          const submittedOrderId = await api.submitSalesOrder(lead.id, request)
          message.success('成交订单已提交会签')
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
      <Form form={form} layout="vertical" disabled={Boolean(loadError) || saving}>
        {repurchase && <Form.Item label="复购说明" required><Input.TextArea rows={3} maxLength={1000} showCount value={repurchaseReason} onChange={event => setRepurchaseReason(event.target.value)}/></Form.Item>}
        <Divider titlePlacement="start">学员信息</Divider>
        <Row gutter={16}>
          <Col xs={24} md={8}><Form.Item name="buyerName" label="购买方" extra="不填则默认同学员姓名"><Input maxLength={100}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="studentName" label="学员姓名" rules={[{ required: true }, { max: 100 }]}><Input/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="studentNature" label="学员性质" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_STUDENT_NATURE)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="mobile" label="手机号" extra="手机号、微信号必填其中一个" dependencies={['wechatId']} rules={[{ pattern: PHONE_PATTERN, message: '手机号格式不正确' }, { validator: validateContact }]}><Input maxLength={32}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="wechatId" label="微信号" dependencies={['mobile']} rules={[{ validator: validateContact }]}><Input maxLength={64}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="regionPath" label="所在省市" rules={[{ required: true, message: '请选择所在省市' }]}><Cascader options={areaOptions} showSearch placeholder="请选择省 / 市"/></Form.Item></Col>
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
              <Col flex="auto"><Form.Item name={[field.name, 'courseKey']} label={`成交课程 ${index + 1}`} rules={[{ required: true, message: '请选择成交课程' }]}><SalesOrderCoursePicker catalog={catalog}/></Form.Item></Col>
              <Col xs={24} md={6}><Form.Item name={[field.name, 'actualAmount']} label={`实际成交金额 ${index + 1}`} rules={[{ required: true, message: '请输入金额' }]}><InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }}/></Form.Item></Col>
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
          <Col xs={24}><Form.Item label={`缴费凭证${vouchers.some(file => file.status === 'uploading') ? '（上传中）' : ''}`} required={total > 0} extra="确认提交后上传；订单金额大于 0 时至少上传一份；最多 9 个 JPG、PNG、WebP 或 PDF，单个不超过 10MB">
            <DeferredAttachmentPicker value={vouchers} onChange={setVouchers} accept="image/jpeg,image/png,image/webp,application/pdf" imageOnly={false}/>
          </Form.Item></Col>
        </Row>
        <Space><IrreversiblePopconfirm action={orderId ? `重新提交成交订单「${orderNo || orderId}」会签` : `提交「${lead.submittedName}」的成交订单会签`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" loading={saving} onClick={() => void prepareSubmit()}>{orderId ? '重新提交会签' : '提交会签'}</Button></IrreversiblePopconfirm><Button onClick={close}>取消</Button></Space>
      </Form>
    </Spin>
  </Modal>
}
