import { useState } from 'react'
import { Alert, Button, Form, Input, Modal, Space, Typography, message } from 'antd'
import type { LeadAppealEvidence, ManagedLead } from '../services/api'
import { overturnLeadValid, uploadOverturnImage } from '../services/supervisorLeadOverturn'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import LeadAppealEvidenceUpload from './LeadAppealEvidenceUpload'

export default function SupervisorLeadOverturnModal({ lead, token, onClose, onChanged }: {
  lead: ManagedLead; token: string; onClose: () => void; onChanged: () => void
}) {
  const [reason, setReason] = useState('')
  const [evidence, setEvidence] = useState<DeferredUploadItem<LeadAppealEvidence>[]>([])
  const [error, setError] = useState('')
  const { submitting, run, resetIntent } = useSubmissionGuard()
  const submit = async () => {
    if (!reason.trim()) { setError('请填写改判理由'); return }
    setError('')
    await run(async ({ idempotencyKey, complete }) => {
      const result = await uploadDeferredFiles(evidence, uploadOverturnImage, setEvidence)
      if (result.failed) { setError('有图片上传失败，请重试失败项'); return }
      await overturnLeadValid(lead.id, { reason: reason.trim(), qualificationToken: token, idempotencyKey,
        attachments: result.items.flatMap(item => item.uploaded ? [{ infraFileId: item.uploaded.infraFileId }] : []) })
      complete(); message.success('主管已将客资改判有效'); onClose(); onChanged()
    }).catch(cause => setError(cause instanceof Error ? cause.message : '改判失败，请重试'))
  }
  return <Modal open title="主管直接改判有效" onCancel={() => { if (!submitting) onClose() }}
    maskClosable={!submitting} closable={!submitting}
    footer={<Space><Button disabled={submitting} onClick={onClose}>取消</Button><Button type="primary" loading={submitting} onClick={() => void submit()}>确认改判有效</Button></Space>}>
    <Space direction="vertical" style={{ width: '100%' }}>
      <Typography.Text>客资编号：{lead.leadNo || '客资编号暂未生成'}</Typography.Text>
      <Typography.Text>原无效原因：{lead.invalidReasonLabelSnapshot || '未记录'}</Typography.Text>
      {lead.invalidDescription && <Typography.Paragraph>{lead.invalidDescription}</Typography.Paragraph>}
      <Alert type="info" showIcon message="确认后恢复为有效，保留原销售归属并恢复商机。原判定记录保留，不自动恢复已取消的跟进提醒。"/>
      {error && <Alert type="error" showIcon message={error}/>}
      <Form.Item label="改判理由" required style={{ marginBottom: 0 }}>
        <Input.TextArea rows={4} maxLength={1000} showCount disabled={submitting} value={reason}
          onChange={event => { resetIntent(); setReason(event.target.value); if (error === '请填写改判理由') setError('') }}/>
      </Form.Item>
      <Form.Item label="图片证据（选填）">
        <LeadAppealEvidenceUpload value={evidence} disabled={submitting}
          onChange={items => { resetIntent(); setEvidence(items) }}/>
      </Form.Item>
    </Space>
  </Modal>
}
