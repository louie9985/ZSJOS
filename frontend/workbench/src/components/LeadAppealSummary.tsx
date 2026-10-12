import { Alert, Button, Space, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import type { ManagedLead } from '../services/api'
import { APP_ROUTES, LEAD_QUALIFICATION_STATUS_LABELS } from '../constants'
import { formatTimestamp } from '../services/time'
import { isLeadAppealPending, leadAppealSummaryLabel } from '../services/leadAppealSummary'

export function LeadAppealPendingTag({ lead }: { lead: ManagedLead }) {
  return isLeadAppealPending(lead) ? <Tag color="orange" title={leadAppealSummaryLabel(lead)}>申诉中</Tag> : null
}

export default function LeadAppealSummary({ lead, onView }: { lead: ManagedLead; onView?: () => void }) {
  const navigate = useNavigate()
  if (!isLeadAppealPending(lead) || !lead.appealSummary) return null
  const summary = lead.appealSummary
  return <Alert type="warning" showIcon message={`申诉处理中 · ${leadAppealSummaryLabel(lead)}`}
    description={<Space direction="vertical" size="small">
      <span>发起于 {formatTimestamp(summary.submittedAt)}</span>
      <span>当前判定：{LEAD_QUALIFICATION_STATUS_LABELS[lead.qualificationStatus ?? ''] || '未记录'}{lead.invalidReasonLabelSnapshot ? ` · ${lead.invalidReasonLabelSnapshot}` : ''}</span>
      <span>申诉期间请通过申诉流程处理，暂不可直接改判。</span>
      <Space wrap>
        {onView && <Button size="small" onClick={onView}>查看申诉</Button>}
        {summary.canHandle && <Button size="small" type="primary" onClick={() => navigate(APP_ROUTES.LEAD_APPEALS, { state: { appealId: summary.id, leadId: lead.id } })}>去处理申诉</Button>}
      </Space>
    </Space>} />
}
