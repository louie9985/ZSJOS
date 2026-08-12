import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Form, Image, Input, Modal, Skeleton, Space, Tag, Timeline, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type LeadAppeal, type LeadAppealEvidence, type ManagedLead } from '../services/api'
import { formatTimestamp } from '../services/time'
import LeadAppealEvidenceUpload from './LeadAppealEvidenceUpload'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import { invalidReasonSnapshotLabel } from '../services/leadManagement'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'

const STAGE_LABELS = { sales_manager: '销售主管复核', quality: '质控复核', chairman: '董事长终审' }
const STATUS_LABELS = {
  sales_manager_reviewing: '销售主管复核中', quality_reviewing: '质控复核中', chairman_reviewing: '董事长终审中',
  overturned: '已改判有效', upheld: '维持无效', withdrawn: '已撤回'
}

function Evidence({ items }: { items?: LeadAppealEvidence[] }) {
  if (!items?.length) return null
  return <Image.PreviewGroup><div className="appeal-evidence-grid">{items.map(item =>
    <Image key={item.infraFileId} src={item.fileUrl} alt={item.originalName}/>)}</div></Image.PreviewGroup>
}

export default function LeadAppealPanel({ lead, audience, onChanged }: {
  lead: ManagedLead
  audience: 'submitter' | 'owner'
  onChanged: () => void
}) {
  const [items, setItems] = useState<LeadAppeal[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [evidence, setEvidence] = useState<DeferredUploadItem<LeadAppealEvidence>[]>([])
  const { submitting: saving, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [confirmOpen, setConfirmOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems(await api.leadAppeals(lead.id)) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '申诉记录加载失败') }
    finally { setLoading(false) }
  }, [lead.id])
  useEffect(() => { void load() }, [load])

  const latest = items.at(-1)
  const canSubmit = audience === 'submitter' && lead.relationTypes.includes('submitter') && lead.status === 'invalid'
    && (!latest || (latest.status === 'upheld' && latest.roundNo < 3))
  const nextRound = latest ? latest.roundNo + 1 : 1

  const openAppeal = () => { resetIntent(); setConfirmOpen(false); setOpen(true) }
  const closeAppeal = () => { setConfirmOpen(false); setOpen(false) }

  const submit = async () => {
    setConfirmOpen(false)
    await runSubmission(async ({ idempotencyKey, complete }) => {
      const uploadResult = await uploadDeferredFiles(evidence, api.uploadLeadAppealImage, setEvidence)
      if (uploadResult.failed) { message.error('有申诉图片上传失败，请重试失败项'); return }
      await api.submitLeadAppeal(lead.id, { reason: reason.trim(), attachments: uploadResult.items.filter(item => item.uploaded).map(item => ({ infraFileId: item.uploaded!.infraFileId })), idempotencyKey })
      complete()
      message.success(`第 ${nextRound} 次申诉已提交`)
      setOpen(false); setReason(''); setEvidence([]); await load(); onChanged()
    }).catch(submitError => message.error(submitError instanceof Error ? submitError.message : '申诉提交失败'))
  }

  const prepareSubmit = () => {
    if (!reason.trim()) { message.warning('请填写申诉理由'); return }
    setConfirmOpen(true)
  }

  if (loading) return <Skeleton active paragraph={{ rows: 6 }}/>
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void load()}>重试</Button>}/>
  return <div className="lead-appeal-panel">
    <div className="lead-appeal-panel-header">
      <div><Typography.Title level={5}>申诉记录</Typography.Title><Typography.Text type="secondary">最多三次，每次维持无效后由提交人手动发起下一轮。</Typography.Text></div>
      {canSubmit && <Button type="primary" onClick={openAppeal}>发起第 {nextRound} 次申诉</Button>}
    </div>
    {!items.length ? <Empty description={canSubmit ? '尚未发起申诉' : '暂无申诉记录'}/> : <Timeline items={items.map(item => ({
      color: item.status === 'overturned' ? 'green' : item.status === 'upheld' ? 'red' : 'blue',
      children: <section className="appeal-timeline-item">
        <Space wrap><Typography.Text strong>第 {item.roundNo} 次申诉</Typography.Text><Tag>{STAGE_LABELS[item.reviewStage]}</Tag><Tag color={item.status === 'overturned' ? 'success' : item.status === 'upheld' ? 'error' : 'processing'}>{STATUS_LABELS[item.status]}</Tag></Space>
        <Typography.Paragraph type="secondary">提交人：{item.applicantUserName || `用户 #${item.applicantUserId}`} · {formatTimestamp(item.submittedAt)}</Typography.Paragraph>
        <Typography.Text type="secondary">原无效结论</Typography.Text>
        <Typography.Paragraph>{[invalidReasonSnapshotLabel(item.invalidReasonSnapshot), item.invalidDescriptionSnapshot].filter(Boolean).join('：') || '-'}</Typography.Paragraph>
        <Evidence items={item.invalidEvidenceSnapshot}/>
        <Typography.Text type="secondary">申诉理由</Typography.Text><Typography.Paragraph>{item.reason}</Typography.Paragraph><Evidence items={item.evidence}/>
        {item.decisionReason && <><Typography.Text type="secondary">裁决意见</Typography.Text><Typography.Paragraph>{item.decisionReason}</Typography.Paragraph><Evidence items={item.decisionEvidence}/><Typography.Text type="secondary">处理人：{item.reviewerUserName || `用户 #${item.reviewerUserId}`} · {formatTimestamp(item.decidedAt)}</Typography.Text></>}
      </section>
    }))}/>} 
    {latest?.roundNo === 3 && latest.status === 'upheld' && <Alert type="error" showIcon message="董事长已终审维持无效，该客资不再允许申诉。"/>}
    <Modal title={`发起第 ${nextRound} 次申诉`} open={open} onCancel={closeAppeal} footer={<Space><Button onClick={closeAppeal}>取消</Button><IrreversiblePopconfirm action={`提交客资「${lead.submittedName}」的第 ${nextRound} 次申诉`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" loading={saving} onClick={prepareSubmit}>提交申诉</Button></IrreversiblePopconfirm></Space>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert type="info" showIcon message={nextRound === 1 ? '本轮由销售直属部门负责人处理' : nextRound === 2 ? '本轮由质控部门处理' : '本轮由董事长最终裁决'}/>
        <Form.Item label="申诉理由" required><Input.TextArea value={reason} onChange={event => setReason(event.target.value)} rows={5} maxLength={1000} showCount placeholder="填写申诉理由"/></Form.Item>
        <Form.Item label={`申诉附件${evidence.some(item => item.status === 'uploading') ? '（上传中）' : ''}`}><LeadAppealEvidenceUpload value={evidence} onChange={setEvidence} disabled={saving}/></Form.Item>
      </Space>
    </Modal>
  </div>
}
