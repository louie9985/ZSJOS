import '../styles/pages/external-repurchase.css'
import OrderLeadSelectionModal from '../components/OrderLeadSelectionModal'
import { useRef, useState } from 'react'
import { hasPermission } from '../services/managementAccess'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import SalesOrderEntryModal, { type SalesOrderEntryLead } from '../components/SalesOrderEntryModal'
import { api, type OrderLeadCandidate, type RepurchaseCustomerIdentity, type RepurchaseCustomerCheck } from '../services/api'
import { PHONE_PATTERN } from '../constants'

export default function ExternalRepurchasePage({ permissions }: { permissions: string[] }) {
  const [form] = Form.useForm<RepurchaseCustomerIdentity>()
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState('')
  const [checked, setChecked] = useState<{ identity: RepurchaseCustomerIdentity; result: RepurchaseCustomerCheck }>()
  const [customer, setCustomer] = useState<SalesOrderEntryLead>()
  const [candidates, setCandidates] = useState<OrderLeadCandidate[]>([])
  const [selectedLeadId, setSelectedLeadId] = useState<number>()
  const sequence = useRef(0)
  const invalidate = () => { setCandidates([]); setSelectedLeadId(undefined); sequence.current++; setChecking(false); setChecked(undefined); setError(''); setCustomer(undefined) }
  const check = async (values: RepurchaseCustomerIdentity) => {
    const identity = { customerName: values.customerName.trim(), customerMobile: values.customerMobile?.trim() || undefined, customerWechatId: values.customerWechatId?.trim() || undefined }
    if (!identity.customerName || (!identity.customerMobile || !identity.customerWechatId)) { setError('请填写姓名、手机号和微信号后校验客户'); return }
    if (identity.customerMobile && !PHONE_PATTERN.test(identity.customerMobile)) { setError('手机号格式不正确'); return }
    const current = ++sequence.current
    setChecking(true); setError(''); setChecked(undefined); setCandidates([]); setSelectedLeadId(undefined); setCustomer(undefined)
    try {
      const result = await api.checkRepurchaseCustomer(identity)
      const matches = result.canRepurchase ? await api.orderLeadCandidates({ identity }) : []
      if (current !== sequence.current) return
      setCandidates(matches)
      setSelectedLeadId(matches.length === 1 ? matches[0].id : undefined)
      setChecked({ identity, result })
    } catch (e) {
      if (current === sequence.current) setError(e instanceof Error ? e.message : '客户校验失败，请重试')
    } finally { if (current === sequence.current) setChecking(false) }
  }
  if (!hasPermission(permissions, 'zsjos:sales-order:create')) return <Alert type="warning" showIcon message="当前账号没有复购录单权限"/>
  const verified = Boolean(checked?.result.canRepurchase)
  const selected = candidates.find(candidate => candidate.id === selectedLeadId)
  return <section className="workspace-page external-repurchase-page">
    <Typography.Title level={3}>历史客户复购</Typography.Title>
    <Card size="small" title="客户身份">
      {verified && checked && <div className="repurchase-identity-summary">
        <Typography.Text strong>{checked.identity.customerName}</Typography.Text>
        <span>手机号　{checked.identity.customerMobile}</span><span>微信号　{checked.identity.customerWechatId}</span>
        <Button onClick={invalidate}>修改</Button>
      </div>}
      <div hidden={verified}><Form form={form} layout="vertical" className="repurchase-identity-form" onValuesChange={invalidate} onFinish={check}>
        <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input/></Form.Item>
        <Form.Item name="customerMobile" label="手机号" rules={[{ required: true, whitespace: true }]}><Input maxLength={32}/></Form.Item>
        <Form.Item name="customerWechatId" label="微信号" rules={[{ required: true, whitespace: true }, { max: 64 }]}><Input/></Form.Item>
        <Button type="primary" htmlType="submit" loading={checking}>校验客户</Button>
      </Form></div>
      {error && <Alert style={{ marginTop: 16 }} type="error" showIcon message={error} action={<Button onClick={() => form.submit()}>重试</Button>}/>}
      {checked && !verified && <Alert type="warning" showIcon message={checked.result.reason}/>}
    </Card>
    {verified && checked && <>
      <div className="repurchase-candidate-heading">
        <Typography.Text strong>{candidates.length ? `已识别客户，请选择关联客资（${candidates.length} 条）` : '已识别客户，暂无关联客资，可直接填写订单'}</Typography.Text>
        <Typography.Text type="secondary">本次订单归属当前录单人</Typography.Text>
      </div>
      {candidates.length > 0 && <OrderLeadSelectionModal candidates={candidates} selectedId={selectedLeadId} onSelect={setSelectedLeadId} showNotice={false}/>}
      <div className="repurchase-continue-bar">
        <Typography.Text>{selected ? `已选择 ${selected.profile.leadNo}` : candidates.length ? '请选择一条客资' : '客户已核验'}</Typography.Text>
        <Button disabled={candidates.length > 0 && selectedLeadId === undefined} type="primary" onClick={() => setCustomer({ id: 0, personId: checked.result.personId,
          submittedName: checked.identity.customerName, submittedMobile: checked.identity.customerMobile, submittedWechatId: checked.identity.customerWechatId })}>填写复购订单</Button>
      </div>
    </>}
    {customer && <SalesOrderEntryModal lead={customer} repurchase customerRepurchase associationChecked selectedLeadId={selectedLeadId} externalCustomer={{ customerName: customer.submittedName,
      customerMobile: customer.submittedMobile, customerWechatId: customer.submittedWechatId, expectedPersonId: customer.personId }} open
      onClose={() => setCustomer(undefined)} onSubmitted={invalidate}/>}
  </section>
}
