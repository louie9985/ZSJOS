import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Cascader, Col, DatePicker, Divider, Form, Input, InputNumber, Modal, Row, Select, Space, Spin, Typography, Upload, message, type UploadFile, type UploadProps } from 'antd'
import { DeleteOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, type AreaNode, type DictData, type LeadCatalog, type SalesOrder, type SalesOrderSubmitRequest, type SalesOrderVoucher } from '../services/api'
import { DICT_TYPE, PHONE_PATTERN } from '../constants'
import { buildLeadAreaOptions, normalizeLeadAreaPath, resolveLeadAreaPath } from '../services/area'
import { validateSalesOrderSubmission } from '../services/salesOrder'

type Values = {
  buyerName?: string; studentName: string; studentNature: string; mobile?: string; wechatId?: string; regionPath: string[]
  agreedExamTime?: string; classType?: string; servicePeriod: string; studentSource: string
  customerPaidAt: Dayjs; feeMode: string; paymentMethod: string; remark?: string
  specialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ skuKey?: string; actualAmount?: number }>
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

export default function SalesOrderEntryModal({ lead, orderId, open, onClose, onSubmitted }: {
  lead: SalesOrderEntryLead; orderId?: number; open: boolean; onClose: () => void; onSubmitted: (orderId: number) => void
}) {
  const [form] = Form.useForm<Values>()
  const [areas, setAreas] = useState<AreaNode[]>([])
  const [catalog, setCatalog] = useState<LeadCatalog>(emptyCatalog)
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [vouchers, setVouchers] = useState<SalesOrderVoucher[]>([])

  const items = Form.useWatch('items', form) || []
  const total = items.reduce((sum, item) => sum + Number(item?.actualAmount || 0), 0)
  const areaOptions = useMemo(() => buildLeadAreaOptions(areas), [areas])
  const skuOptions = useMemo(() => {
    const spus = new Map(catalog.spus.map(spu => [spu.spuRef, spu]))
    return catalog.skus.map(sku => {
      const spu = spus.get(sku.spuRef)
      const path = [...(spu?.categoryPath || []).map(item => item.name), spu?.spuName, sku.skuName].filter(Boolean)
      return { value: `${sku.spuRef}::${sku.skuRef}`, label: path.join(' / '), sku }
    })
  }, [catalog])

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
        items: order?.items.map(item => ({ skuKey: `${item.productRef}::${item.skuRef}`, actualAmount: item.actualAmount }))
          || [{ skuKey: hasPrimary ? primary : undefined, actualAmount: undefined }]
      })
      setVouchers(order?.paymentVouchers || [])
    } catch (error) {
      setAreas([]); setCatalog(emptyCatalog); setDicts({}); setLoadError(errorText(error))
    } finally { setLoading(false) }
  }

  useEffect(() => {
    if (!open) return
    form.resetFields(); setVouchers([]); void load()
  }, [open, lead.id, orderId])

  const options = (type: string) => (dicts[type] || []).map(item => ({ value: item.value, label: item.label }))
  const files: UploadFile[] = vouchers.map(file => ({ uid: String(file.infraFileId), name: file.originalName, status: 'done', url: file.fileUrl, type: file.contentType }))
  const upload: UploadProps['customRequest'] = async request => {
    const file = request.file as File
    const accepted = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
    if (!accepted.includes(file.type) || file.size > 10 * 1024 * 1024 || vouchers.length >= 9) {
      const error = new Error(vouchers.length >= 9 ? '最多上传 9 个缴费凭证' : '仅支持 JPG、PNG、WebP、PDF，单个不超过 10MB')
      message.error(error.message); request.onError?.(error); return
    }
    try { const result = await api.uploadSalesOrderVoucher(file); setVouchers(value => [...value, result]); request.onSuccess?.(result) }
    catch (error) { const uploadError = error instanceof Error ? error : new Error('凭证上传失败'); message.error(uploadError.message); request.onError?.(uploadError) }
  }

  const submit = async (values: Values) => {
    const validationError = validateSalesOrderSubmission(values.mobile, values.wechatId, total, vouchers.length)
    if (validationError) { message.warning(validationError); return }
    const [provinceCode, cityCode] = normalizeLeadAreaPath(values.regionPath)
    const region = findRegion(areas, values.regionPath)
    if (!region.provinceName) { message.warning('请选择有效省市'); return }
    const request: SalesOrderSubmitRequest = {
      buyerName: values.buyerName?.trim() || undefined, studentName: values.studentName.trim(), studentNature: values.studentNature,
      studentMobile: values.mobile?.trim() || undefined, studentWechatId: values.wechatId?.trim() || undefined,
      provinceCode, provinceName: region.provinceName, cityCode, cityName: region.cityName,
      agreedExamTime: values.agreedExamTime?.trim() || undefined, classType: values.classType?.trim() || undefined,
      servicePeriod: values.servicePeriod, studentSource: values.studentSource, customerPaidAt: values.customerPaidAt.valueOf(),
      feeMode: values.feeMode, paymentMethod: values.paymentMethod, remark: values.remark?.trim() || undefined,
      studentSpecialRequirements: values.specialRequirements?.trim() || undefined,
      materialDeliveryContact: values.materialDeliveryContact?.trim() || undefined,
      items: values.items.map(item => { const [spuRef, skuRef] = item.skuKey!.split('::'); return { spuRef, skuRef, actualAmount: Number(item.actualAmount) } }),
      paymentVouchers: vouchers.map(file => ({ infraFileId: file.infraFileId })), idempotencyKey: crypto.randomUUID()
    }
    setSaving(true)
    try {
      if (orderId) {
        await api.resubmitSalesOrder(orderId, request)
        message.success('成交订单已补正并重新提交会签')
        onSubmitted(orderId)
      } else {
        const submittedOrderId = await api.submitSalesOrder(lead.id, request)
        message.success('成交订单已提交会签')
        onSubmitted(submittedOrderId)
      }
    }
    catch (error) { message.error(errorText(error)) }
    finally { setSaving(false) }
  }

  return <Modal title={orderId ? '补正成交' : '录入成交'} open={open} onCancel={onClose} footer={null} width={980} destroyOnHidden>
    {loadError && <Alert type="error" showIcon message="成交配置加载失败" description={loadError}
      action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void load()}>重试</Button>}/>}
    <Spin spinning={loading}>
      <Form form={form} layout="vertical" onFinish={submit} disabled={Boolean(loadError)}>
        <Divider titlePlacement="start">学员信息</Divider>
        <Row gutter={16}>
          <Col xs={24} md={8}><Form.Item name="buyerName" label="购买方" extra="不填则默认同学员姓名"><Input maxLength={100}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="studentName" label="学员姓名" rules={[{ required: true }, { max: 100 }]}><Input/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="studentNature" label="学员性质" rules={[{ required: true }]}><Select options={options(DICT_TYPE.ORDER_STUDENT_NATURE)}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="mobile" label="手机号" dependencies={['wechatId']} rules={[{ pattern: PHONE_PATTERN, message: '手机号格式不正确' }]}><Input maxLength={32}/></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="wechatId" label="微信号"><Input maxLength={64}/></Form.Item></Col>
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
              <Col flex="auto"><Form.Item name={[field.name, 'skuKey']} label={index === 0 ? '成交课程' : undefined} rules={[{ required: true, message: '请选择成交课程' }]}><Select showSearch optionFilterProp="label" options={skuOptions}/></Form.Item></Col>
              <Col xs={24} md={6}><Form.Item name={[field.name, 'actualAmount']} label={index === 0 ? '实际成交金额' : undefined} rules={[{ required: true, message: '请输入金额' }]}><InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }}/></Form.Item></Col>
              <Col><Button aria-label="删除成交课程" title="删除成交课程" icon={<DeleteOutlined/>} danger disabled={fields.length === 1} onClick={() => remove(field.name)} style={{ marginTop: index === 0 ? 30 : 0 }}/></Col>
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
          <Col xs={24}><Form.Item label="缴费凭证" required={total > 0} extra="订单金额大于 0 时至少上传一份；最多 9 个 JPG、PNG、WebP 或 PDF，单个不超过 10MB">
            <Upload fileList={files} customRequest={upload} accept="image/jpeg,image/png,image/webp,application/pdf"
              onRemove={file => { setVouchers(value => value.filter(item => String(item.infraFileId) !== file.uid)); return true }}>
              {vouchers.length < 9 && <Button icon={<UploadOutlined/>}>上传凭证</Button>}
            </Upload>
          </Form.Item></Col>
        </Row>
        <Space><Button type="primary" htmlType="submit" loading={saving}>{orderId ? '重新提交会签' : '提交会签'}</Button><Button onClick={onClose}>取消</Button></Space>
      </Form>
    </Spin>
  </Modal>
}
