import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Skeleton, Space, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type LeadAppeal, type LeadAppealContext, type LeadAppealEvidence, type ManagedLead } from '../services/api'
import LeadAppealEvidenceUpload from './LeadAppealEvidenceUpload'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'
import LeadAppealAttachments from './LeadAppealAttachments'
import LeadAppealDecisionForm from './LeadAppealDecisionForm'
import LeadAppealProcess from './LeadAppealProcess'
import { canDecideAppeal } from '../services/leadAppealReview'

export type AppealReviewContext = { context: LeadAppealContext; appealId: number; reload: () => Promise<LeadAppealContext>; read: () => Promise<LeadAppealContext>; onBusyChange?: (busy: boolean) => void }

export function canSubmitLeadAppeal(
  context?: { canSubmit: boolean; nextRoundNo: number | null }
) {
  return context?.canSubmit === true && context.nextRoundNo != null
}

export default function LeadAppealPanel({ lead, onChanged, readOnly = false, review, onDirtyChange }: {
  lead: ManagedLead
  onChanged: () => void
  readOnly?: boolean
  review?: AppealReviewContext
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [items, setItems] = useState<LeadAppeal[]>([])
  const [context, setContext] = useState<Awaited<ReturnType<typeof api.leadAppealContext>>>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [evidence, setEvidence] = useState<DeferredUploadItem<LeadAppealEvidence>[]>([])
  const { submitting: saving, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [decisionDirty, setDecisionDirty] = useState(false)
  const [decisionSaving, setDecisionSaving] = useState(false)
  useEffect(() => { review?.onBusyChange?.(decisionSaving) }, [decisionSaving, review?.onBusyChange])
  const dirty = decisionDirty || Boolean(reason.trim()) || evidence.length > 0 || saving
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { const result = await api.leadAppealContext(lead.id); setContext(result); setItems(result.records) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '申诉记录加载失败') }
    finally { setLoading(false) }
  }, [lead.id])
  useEffect(() => {
    if (review) { setContext(review.context); setItems(review.context.records); setLoading(false); setError('') }
    else void load()
  }, [load, review?.context])

  const latest = items.filter(item => item.currentCycle).at(-1)
  const canSubmit = !readOnly && canSubmitLeadAppeal(context)
  const nextRound = context?.nextRoundNo ?? 1

  const openAppeal = () => { resetIntent(); setConfirmOpen(false); setOpen(true) }
  const closeAppeal = () => {
    if (saving || ((reason.trim() || evidence.length) && !window.confirm('放弃尚未提交的申诉内容？'))) return
    setConfirmOpen(false); setOpen(false); setReason(''); setEvidence([])
  }

  const submit = async () => {
    setConfirmOpen(false)
    await runSubmission(async ({ idempotencyKey, complete }) => {
      const uploadResult = await uploadDeferredFiles(evidence, api.uploadLeadAppealImage, setEvidence)
      if (uploadResult.failed) { message.error('有申诉图片上传失败，请重试失败项'); return }
      await api.submitLeadAppeal(lead.id, {
        invalidEventId: context!.invalidEventId, reason: reason.trim(), attachments: uploadResult.items.filter(item => item.uploaded).map(item => ({ infraFileId: item.uploaded!.infraFileId })), idempotencyKey })
      complete()
      message.success(`第 ${nextRound} 次申诉已提交`)
      setOpen(false); setReason(''); setEvidence([]); await load(); onChanged()
    }).catch(submitError => message.error(submitError instanceof Error ? submitError.message : '申诉提交失败'))
  }

  const prepareSubmit = () => {
    if (!reason.trim()) { message.warning('请填写申诉理由'); return }
    setConfirmOpen(true)
  }

  const reloadContext = () => review ? review.read() : api.leadAppealContext(lead.id)
  const attachments = (item: LeadAppeal, field: 'evidence' | 'invalidEvidenceSnapshot' | 'decisionEvidence') =>
    <LeadAppealAttachments items={item[field]} reload={async () => (await reloadContext()).records.find(row => row.id === item.id)?.[field] ?? []} />
  if (loading) return <Skeleton active paragraph={{ rows: 6 }}/>
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void load()}>重试</Button>}/>
  return <div className="lead-appeal-panel">
    <div className="lead-appeal-panel-header">
      <div><Typography.Title level={5}>申诉记录</Typography.Title><Typography.Text type="secondary">每次无效判定独立申诉，周期内最多三轮；维持无效后手动发起下一轮。</Typography.Text></div>
      {canSubmit && <Button type="primary" onClick={openAppeal}>发起第 {nextRound} 次申诉</Button>}
    </div>
    {!items.length ? <Empty description={canSubmit ? '尚未发起申诉' : '暂无申诉记录'}/> : <LeadAppealProcess items={items} targetId={review?.appealId} attachments={attachments}
      decision={item => canDecideAppeal(item, review?.appealId) && !readOnly ? <LeadAppealDecisionForm key={item.id} item={item} onDirtyChange={setDecisionDirty} onBusyChange={setDecisionSaving}
        onDecided={async () => { setDecisionDirty(false); await review!.reload() }} /> : null} />}
    {latest?.roundNo === 3 && latest.status === 'upheld' && <Alert type="error" showIcon message="董事长已终审维持无效，本次无效判定不再允许申诉。"/>}
    <Modal title={`发起第 ${nextRound} 次申诉`} open={open} onCancel={closeAppeal} footer={<Space><Button onClick={closeAppeal}>取消</Button><IrreversiblePopconfirm action={`提交客资「${lead.submittedName}」的第 ${nextRound} 次申诉`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" loading={saving} onClick={prepareSubmit}>提交申诉</Button></IrreversiblePopconfirm></Space>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert type="info" showIcon message={nextRound === 1 ? '本轮由销售直属部门负责人处理' : nextRound === 2 ? '本轮由质控部门处理' : '本轮由董事长最终裁决'}/>
        <Form.Item label="申诉理由" required><Input.TextArea value={reason} onChange={event => setReason(event.target.value)} rows={5} maxLength={1000} showCount placeholder="填写申诉理由"/></Form.Item>
        <Form.Item label={`申诉附件${evidence.some(item => item.status === 'uploading') ? '（上传中）' : ''}`}><LeadAppealEvidenceUpload value={evidence} onChange={setEvidence} disabled={saving}/></Form.Item>
      </Space>
    </Modal>
  </div>
}
