import { useState } from 'react'
import { Alert, Button, Input, Modal } from 'antd'
import { api, type ManagedLead } from '../services/api'
import { hasPermission } from '../services/managementAccess'

export function canCancelLeadNextFollowUp(lead: Pick<ManagedLead, 'status' | 'nextFollowUpAt'>, permissions: string[]) {
  return lead.status === 'won' && Boolean(lead.nextFollowUpAt)
    && hasPermission(permissions, 'zsjos:lead-follow-up:cancel-next-time')
}

export function LeadNextFollowUpCancelDialog({ leadId, onClose, onChanged }: {
  leadId: number
  onClose: () => void
  onChanged: () => void | Promise<void>
}) {
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async () => {
    if (saving || !reason.trim()) return
    setSaving(true)
    setError('')
    try {
      await api.cancelLeadNextFollowUp(leadId, reason.trim())
      onClose()
      await onChanged()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '取消失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  return <Modal title="确认取消下次跟进任务" open confirmLoading={saving}
      okText="确认取消" cancelText="返回" okButtonProps={{ disabled: !reason.trim() }}
      closable={!saving} mask={{ closable: !saving }} keyboard={!saving}
      cancelButtonProps={{ disabled: saving }}
      onCancel={() => { if (!saving) onClose() }} onOk={() => void submit()}>
      <p>请确认该客资没有继续跟进的必要，再取消下次跟进任务</p>
      <Input.TextArea aria-label="取消原因" placeholder="请填写取消原因" maxLength={500}
        value={reason} onChange={event => setReason(event.target.value)} disabled={saving} />
      {error && <Alert type="error" showIcon message={error} />}
    </Modal>
}

export default function LeadNextFollowUpCancelButton({ lead, permissions, onChanged }: {
  lead: ManagedLead
  permissions: string[]
  onChanged: () => void | Promise<void>
}) {
  const [open, setOpen] = useState(false)
  if (!canCancelLeadNextFollowUp(lead, permissions)) return null
  return <>
    <Button onClick={() => setOpen(true)}>取消跟进时间</Button>
    {open && <LeadNextFollowUpCancelDialog leadId={lead.id} onClose={() => setOpen(false)} onChanged={onChanged} />}
  </>
}
