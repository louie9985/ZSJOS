import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Form, Input, Space, Typography, message } from 'antd'
import { api, type LeadAppeal, type LeadAppealEvidence } from '../services/api'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import LeadAppealEvidenceUpload from './LeadAppealEvidenceUpload'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'

export default function LeadAppealDecisionForm({ item, onDecided, onDirtyChange, onBusyChange }: {
  item: LeadAppeal
  onDecided: () => Promise<void>
  onDirtyChange?: (dirty: boolean) => void
  onBusyChange?: (busy: boolean) => void
}) {
  const [reason, setReason] = useState('')
  const [evidence, setEvidence] = useState<DeferredUploadItem<LeadAppealEvidence>[]>([])
  const [decision, setDecision] = useState<'overturn' | 'uphold'>()
  const [error, setError] = useState('')
  const [completed, setCompleted] = useState(false)
  const intent = useRef<'overturn' | 'uphold'>(undefined)
  const { submitting, run, resetIntent } = useSubmissionGuard()
  useEffect(() => { onBusyChange?.(submitting); return () => onBusyChange?.(false) }, [submitting, onBusyChange])
  useEffect(() => { onDirtyChange?.(!completed && (Boolean(reason.trim()) || evidence.length > 0 || submitting)) }, [reason, evidence.length, submitting, completed, onDirtyChange])
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])
  const submit = async () => {
    if (!decision || !item.taskId || !reason.trim()) return
    const next = decision
    setDecision(undefined); setError('')
    await run(async ({ idempotencyKey, complete }) => {
      const result = await uploadDeferredFiles(evidence, api.uploadLeadAppealImage, setEvidence)
      if (result.failed) { setError('有裁决附件上传失败，请重试失败项'); return }
      await api.decideLeadAppeal(item.id, next, { taskId: item.taskId!, reason: reason.trim(),
        attachments: result.items.map(file => ({ infraFileId: file.uploaded!.infraFileId })), idempotencyKey })
      complete(); setCompleted(true); onDirtyChange?.(false)
      message.success(next === 'overturn' ? '已改判有效' : '已维持无效')
      await onDecided()
    }).catch(cause => setError(cause instanceof Error ? cause.message : '裁决提交失败，请重试'))
  }
  if (completed) return <Alert type="success" showIcon message="裁决已提交" description="正在显示最新处理结果；若读取失败，请重试刷新。" />
  return <section className="appeal-decision-form" aria-label="本轮裁决">
    <Typography.Title level={5}>本轮裁决</Typography.Title>
    {error && <Alert type="error" showIcon message={error} action={<Button disabled={submitting} onClick={() => void onDecided().catch(() => {})}>刷新申诉状态</Button>} />}
    <Form layout="vertical">
      <Form.Item label="裁决理由" required><Input.TextArea aria-label="裁决理由" value={reason} disabled={submitting}
        onChange={event => setReason(event.target.value)} rows={4} maxLength={1000} showCount placeholder="填写核实依据与处理意见" /></Form.Item>
      <Form.Item label="裁决附件（选填）"><LeadAppealEvidenceUpload value={evidence} onChange={setEvidence} disabled={submitting} /></Form.Item>
    </Form>
    <div className="appeal-decision-footer"><Typography.Text type="secondary">当前处理：第 {item.roundNo} 轮申诉</Typography.Text>
      <Space>{(['uphold', 'overturn'] as const).map(value => <IrreversiblePopconfirm key={value}
        action={`${item.leadNo} · 第 ${item.roundNo} 轮申诉${value === 'overturn' ? '改判有效' : '维持无效'}`}
        danger={value === 'uphold'} open={decision === value} onOpenChange={open => { if (!open) setDecision(undefined) }} onConfirm={() => { void submit() }}>
        <Button type={value === 'overturn' ? 'primary' : 'default'} danger={value === 'uphold'} loading={submitting} onClick={() => {
          if (!reason.trim()) { message.warning('裁决理由为必填项'); return }
          if (intent.current && intent.current !== value) resetIntent()
          intent.current = value
          setDecision(value)
        }}>{value === 'overturn' ? '改判有效' : '维持无效'}</Button>
      </IrreversiblePopconfirm>)}</Space>
    </div>
  </section>
}
