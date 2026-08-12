import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography, message } from 'antd'
import SalesOrderEntryModal, { type SalesOrderEntryLead } from '../components/SalesOrderEntryModal'

type CustomerValues = { customerName: string; customerMobile?: string; customerWechatId?: string }

export default function ExternalRepurchasePage() {
  const [form] = Form.useForm<CustomerValues>()
  const [customer, setCustomer] = useState<SalesOrderEntryLead>()
  return <section className="workspace-page">
    <Typography.Title level={3}>历史客户复购</Typography.Title>
    <Alert type="info" showIcon message="适用于系统外历史客户" description="若客户已有主客资，请从客资详情发起复购。身份冲突时系统会阻止提交。"/>
    <Card size="small" title="客户身份" style={{ marginTop: 16, maxWidth: 720 }}>
      <Form form={form} layout="vertical" onFinish={values => {
        if (!values.customerMobile?.trim() && !values.customerWechatId?.trim()) { message.warning('手机号和微信号至少填写一个'); return }
        setCustomer({ id: 0, submittedName: values.customerName.trim(), submittedMobile: values.customerMobile?.trim(), submittedWechatId: values.customerWechatId?.trim() })
      }}>
        <Form.Item name="customerName" label="客户姓名" rules={[{ required: true }, { max: 100 }]}><Input/></Form.Item>
        <Form.Item name="customerMobile" label="手机号"><Input maxLength={32}/></Form.Item>
        <Form.Item name="customerWechatId" label="微信号"><Input maxLength={128}/></Form.Item>
        <Button type="primary" htmlType="submit">填写复购订单</Button>
      </Form>
    </Card>
    {customer && <ExternalOrderModal customer={customer} onClose={() => setCustomer(undefined)}/>} 
  </section>
}

function ExternalOrderModal({ customer, onClose }: { customer: SalesOrderEntryLead; onClose: () => void }) {
  // The shared order editor owns all server-backed dictionaries, course data, region and voucher validation.
  return <SalesOrderEntryModal lead={customer} repurchase externalCustomer={{ customerName: customer.submittedName,
    customerMobile: customer.submittedMobile, customerWechatId: customer.submittedWechatId }} open onClose={onClose} onSubmitted={() => onClose()}/>
}
