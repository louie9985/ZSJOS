import { useRef, useState } from 'react'
import { hasPermission } from '../services/managementAccess'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import SalesOrderEntryModal, { type SalesOrderEntryLead } from '../components/SalesOrderEntryModal'
import { api, type RepurchaseCustomerIdentity, type RepurchaseCustomerCheck } from '../services/api'

export default function ExternalRepurchasePage({ permissions }: { permissions: string[] }) {
  const [form] = Form.useForm<RepurchaseCustomerIdentity>()
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState('')
  const [checked, setChecked] = useState<{ identity: RepurchaseCustomerIdentity; result: RepurchaseCustomerCheck }>()
  const [customer, setCustomer] = useState<SalesOrderEntryLead>()
  const sequence = useRef(0)
  const invalidate = () => { sequence.current++; setChecking(false); setChecked(undefined); setError(''); setCustomer(undefined) }
  const check = async (values: RepurchaseCustomerIdentity) => {
    const identity = { customerName: values.customerName.trim(), customerMobile: values.customerMobile?.trim() || undefined, customerWechatId: values.customerWechatId?.trim() || undefined }
    if (!identity.customerName || !identity.customerMobile && !identity.customerWechatId) { setError('请填写姓名及手机号或微信号'); return }
    const current = ++sequence.current
    setChecking(true); setError(''); setChecked(undefined)
    try {
      const result = await api.checkRepurchaseCustomer(identity)
      if (current === sequence.current) setChecked({ identity, result })
    } catch (e) {
      if (current === sequence.current) setError(e instanceof Error ? e.message : '客户校验失败，请重试')
    } finally { if (current === sequence.current) setChecking(false) }
  }
  if (!hasPermission(permissions, 'zsjos:sales-order:create')) return <Alert type="warning" showIcon message="当前账号没有复购录单权限"/>
  return <section className="workspace-page">
    <Typography.Title level={3}>历史客户复购</Typography.Title>
    <Alert type="info" showIcon message="销售与教务均可为已有客户录入新的复购订单" description="先核实客户身份。本次订单归属当前录单人，原客资、服务关系及历史订单不变；系统无记录的老客户也可录入。"/>
    <Card size="small" title="客户身份" style={{ marginTop: 16, maxWidth: 720 }}>
      <Form form={form} layout="vertical" onValuesChange={invalidate} onFinish={check}>
        <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input/></Form.Item>
        <Form.Item name="customerMobile" label="手机号"><Input maxLength={32}/></Form.Item>
        <Form.Item name="customerWechatId" label="微信号" rules={[{ max: 64 }]}><Input/></Form.Item>
        <Button type="primary" htmlType="submit" loading={checking}>校验客户</Button>
      </Form>
      {error && <Alert style={{ marginTop: 16 }} type="error" showIcon message={error} action={<Button onClick={() => form.submit()}>重试</Button>}/>}
      {checked && <Alert style={{ marginTop: 16 }} type={checked.result.canRepurchase ? 'success' : 'warning'} showIcon message={checked.result.reason}
        description={checked.result.customerName ? [checked.result.customerName, checked.result.maskedMobile].filter(Boolean).join(' · ') : undefined}/>}
      {checked?.result.canRepurchase && <Button style={{ marginTop: 16 }} type="primary" onClick={() => setCustomer({ id: 0, personId: checked.result.personId,
        submittedName: checked.identity.customerName, submittedMobile: checked.identity.customerMobile, submittedWechatId: checked.identity.customerWechatId })}>确认客户，填写复购订单</Button>}
    </Card>
    {customer && <SalesOrderEntryModal lead={customer} repurchase customerRepurchase externalCustomer={{ customerName: customer.submittedName,
      customerMobile: customer.submittedMobile, customerWechatId: customer.submittedWechatId, expectedPersonId: customer.personId }} open
      onClose={() => setCustomer(undefined)} onSubmitted={invalidate}/>}
  </section>
}
