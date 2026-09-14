import { Alert, Button, Card, Form, InputNumber, Skeleton } from 'antd'
import { useEffect, useState } from 'react'
import { api } from '../services/api'

export function StudentDeliveryConfigPage() {
  const [form] = Form.useForm(); const [loading, setLoading] = useState(true); const [error, setError] = useState('')
  useEffect(() => { void api.studentDeliveryConfig?.get?.().then(value => form.setFieldsValue(value)).catch(cause => setError(cause instanceof Error ? cause.message : '加载失败')).finally(() => setLoading(false)) }, [form])
  const save = async () => { try { await api.studentDeliveryConfig.update(await form.validateFields()); } catch (cause) { setError(cause instanceof Error ? cause.message : '保存失败') } }
  if (loading) return <Skeleton active />
  return <Card title="学员账号交付周期配置">{error && <Alert type="error" message={error} />}<Form form={form} layout="vertical">{['s0Days','s1Days','s2Days','s3Days','s4Days','s5Days','s6Days'].map((name,index) => <Form.Item key={name} name={name} label={`S${index} 触发间隔（天）`} rules={[{ required: true }]}><InputNumber min={0} max={365} /></Form.Item>)}<Button type="primary" onClick={() => void save()}>保存</Button></Form></Card>
}
