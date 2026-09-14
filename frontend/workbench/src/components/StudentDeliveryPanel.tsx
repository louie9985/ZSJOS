import { Alert, Button, Card, Form, Input, InputNumber, Radio, Space, Tag, message } from 'antd'
import { useEffect, useState } from 'react'
import { api, type StudentDeliveryDefer, type StudentDeliveryPlan } from '../services/api'

export type StudentDeliveryPanelProps = { accountId: number; submittedBy: number; onChanged?: () => void }

export function StudentDeliveryPanel({ accountId, submittedBy, onChanged }: StudentDeliveryPanelProps) {
  const [plan, setPlan] = useState<StudentDeliveryPlan | null>()
  const [stageId, setStageId] = useState<number>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [deferDays, setDeferDays] = useState<1 | 2 | 3>(1)
  const [reason, setReason] = useState('')
  const [values, setValues] = useState({ deliveryContent: '', deliveryCompleted: '是', diagnosis: '', improvement: '' })
  useEffect(() => { void api.studentDelivery.plan(accountId).then(value => { setPlan(value || undefined); setStageId(value?.stages.find(stage => stage.status === 'PENDING' || stage.status === 'OVERDUE')?.id) }).catch(cause => setError(cause instanceof Error ? cause.message : '加载交付计划失败')) }, [accountId])
  const submit = async () => {
    if (!stageId) return
    setLoading(true); setError('')
    if (!values.deliveryContent.trim() || !values.diagnosis.trim()) { setError('请填写交付内容和诊断结论'); setLoading(false); return }
    try { await api.studentDelivery.submit({ stageId, submittedBy, fieldValuesJson: JSON.stringify(values) }); message.success('交付确认已提交'); onChanged?.() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '提交失败') }
    finally { setLoading(false) }
  }
  const defer = async () => {
    if (!stageId || !reason.trim()) { setError('请填写延期原因'); return }
    setLoading(true); setError('')
    try { const result: StudentDeliveryDefer = await api.studentDelivery.defer({ stageId, requestedBy: submittedBy, requestedDays: deferDays, reason }); message.success(`已申请延期 ${result.requestedDays} 天`); onChanged?.() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '延期申请失败') }
    finally { setLoading(false) }
  }
  return <Card title="交付确认" loading={loading}>
    {error && <Alert type="error" showIcon message={error} />}
    <Space direction="vertical" style={{ width: '100%' }}>
      <div>{plan ? <Tag color={plan.status === 'ACTIVE' ? 'blue' : undefined}>{plan.status}</Tag> : '暂无交付周期计划'}</div>
      {plan?.stages?.map(stage => <div key={stage.id}><Tag color={stage.status === 'COMPLETED' ? 'green' : stage.status === 'PENDING' || stage.status === 'OVERDUE' ? 'orange' : undefined}>{stage.stageCode}</Tag><span>{stage.status}{stage.dueAt ? ` · 截止 ${stage.dueAt}` : ''}</span></div>)}
      <Input.TextArea placeholder="本期确定交付内容确认" value={values.deliveryContent} onChange={e => setValues(v => ({ ...v, deliveryContent: e.target.value }))} />
      <Radio.Group value={values.deliveryCompleted} onChange={e => setValues(v => ({ ...v, deliveryCompleted: e.target.value }))} options={[{ value: '是', label: '交付已完成' }, { value: '否', label: '交付未完成' }]} />
      <Input.TextArea placeholder="一句话诊断结论" value={values.diagnosis} onChange={e => setValues(v => ({ ...v, diagnosis: e.target.value }))} />
      <Input.TextArea placeholder="改进措施" value={values.improvement} onChange={e => setValues(v => ({ ...v, improvement: e.target.value }))} />
      <Button type="primary" disabled={!stageId} onClick={() => void submit()}>提交本期交付确认</Button>
      <Form layout="inline"><Form.Item label="延期天数"><InputNumber min={1} max={3} value={deferDays} onChange={v => setDeferDays((v || 1) as 1 | 2 | 3)} /></Form.Item><Form.Item label="原因"><Input value={reason} onChange={e => setReason(e.target.value)} /></Form.Item><Button disabled={!stageId} onClick={() => void defer()}>申请延期</Button></Form>
    </Space>
  </Card>
}
