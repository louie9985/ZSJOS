import { Alert, App, Button, Empty, Form, Input, InputNumber, Modal, Radio, Skeleton, Space, Tag } from 'antd'
import { useEffect, useState } from 'react'
import { api, type StudentDeliveryPlan } from '../services/api'
import { formatTimestamp } from '../services/time'

export type StudentDeliveryPanelProps = {
  accountId: number
  submittedBy: number
  canQuery: boolean
  canSubmit: boolean
  canDefer: boolean
  onChanged?: () => void
}
const stageLabels: Record<string, string> = { PENDING: '待交付', OVERDUE: '已逾期', COMPLETED: '已完成', DEFER_PENDING: '延期审批中', WAITING: '待开始' }

export function StudentDeliveryPanel({ accountId, submittedBy, canQuery, canSubmit, canDefer, onChanged }: StudentDeliveryPanelProps) {
  const { message } = App.useApp()
  const [plan, setPlan] = useState<StudentDeliveryPlan | null>(null)
  const [open, setOpen] = useState<'delivery' | 'defer'>()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [deliveryForm] = Form.useForm()
  const [deferForm] = Form.useForm()
  useEffect(() => {
    let active = true
    setPlan(null); setError('')
    if (!canQuery || !open) return
    setLoading(true)
    void api.studentDelivery.plan(accountId).then(value => { if (active) setPlan(value || null) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '加载交付计划失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [accountId, canQuery, open, reload])
  useEffect(() => { setOpen(undefined); deliveryForm.resetFields(); deferForm.resetFields() }, [accountId, deliveryForm, deferForm])
  const stage = plan?.stages.find(item => item.status === 'PENDING' || (open === 'defer' && item.status === 'OVERDUE'))
  const submit = async () => {
    if (!stage || saving) return
    const form = open === 'delivery' ? deliveryForm : deferForm
    let values: Record<string, unknown>
    try { values = await form.validateFields() } catch { return }
    setSaving(true); setError('')
    try {
      if (open === 'delivery') {
        await api.studentDelivery.submit({ stageId: stage.id, submittedBy, fieldValuesJson: JSON.stringify(values) })
        message.success('交付确认已提交')
      } else {
        await api.studentDelivery.defer({ stageId: stage.id, requestedBy: submittedBy, requestedDays: Number(values.requestedDays), reason: String(values.reason).trim() })
        message.success('延期申请已提交审批，通过后生效')
      }
      form.resetFields(); setOpen(undefined); onChanged?.()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '提交失败，请重试') }
    finally { setSaving(false) }
  }
  const summary = loading ? <Skeleton active paragraph={{ rows: 2 }} /> : !plan ? <Empty description="暂无交付周期计划" /> : <Space orientation="vertical">
    {plan.stages.map(item => <div key={item.id}><Tag>{item.stageCode}</Tag>{stageLabels[item.status] || item.status}{item.dueAt ? ` · 截止 ${formatTimestamp(item.dueAt)}` : ''}</div>)}
    {!stage && <Alert type="info" showIcon title="当前没有可提交的交付阶段；延期审批中的阶段需等待审批结果。" />}
  </Space>
  return <>
    {canQuery && canDefer && <Button onClick={() => setOpen('defer')}>申请延期</Button>}
    {canQuery && canSubmit && <Button onClick={() => setOpen('delivery')}>填写交付确认</Button>}
    <Modal title={open === 'delivery' ? '交付确认' : '申请延期'} open={Boolean(open)} onCancel={() => { if (!saving) setOpen(undefined) }} onOk={() => void submit()} okText={open === 'delivery' ? '提交交付确认' : '提交审批'} confirmLoading={saving} okButtonProps={{ disabled: loading || !stage }} cancelButtonProps={{ disabled: saving }} closable={!saving} maskClosable={!saving} destroyOnHidden>
      {error && <Alert type="error" showIcon title={error} action={<Button size="small" onClick={() => setReload(value => value + 1)}>重新加载</Button>} />}
      {summary}
      {!loading && stage && (open === 'delivery' ? <Form form={deliveryForm} layout="vertical" initialValues={{ deliveryCompleted: '是' }}>
        <Form.Item label="本期交付内容" name="deliveryContent" rules={[{ required: true, whitespace: true, message: '请填写交付内容' }]}><Input.TextArea rows={3} /></Form.Item>
        <Form.Item label="交付结果" name="deliveryCompleted" rules={[{ required: true }]}><Radio.Group options={[{ value: '是', label: '交付已完成' }, { value: '否', label: '交付未完成' }]} /></Form.Item>
        <Form.Item label="一句话诊断结论" name="diagnosis" rules={[{ required: true, whitespace: true, message: '请填写诊断结论' }]}><Input.TextArea rows={3} /></Form.Item>
        <Form.Item label="改进措施" name="improvement"><Input.TextArea rows={3} /></Form.Item>
      </Form> : <Form form={deferForm} layout="vertical">
        <Form.Item label="延期天数" name="requestedDays" extra="自由填写延期天数，审批通过后从原截止时间顺延。" rules={[{ required: true, message: '请填写延期天数' }, { type: 'integer', min: 1, message: '请输入正整数天数' }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
        <Form.Item label="延期原因" name="reason" rules={[{ required: true, whitespace: true, message: '请填写延期原因' }, { max: 1000, message: '原因不能超过 1000 字' }]}><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item>
      </Form>)}
    </Modal>
  </>
}
