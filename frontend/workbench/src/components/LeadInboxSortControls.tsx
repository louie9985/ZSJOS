import { Select, Typography } from 'antd'
import type { LeadSortField } from '../services/api'
import './LeadInboxSortControls.css'

type SortOrder = 'ascend' | 'descend'

// These choices represent the existing server sorting protocol, not business dictionary values.
const fields: Array<{ value: LeadSortField | 'default'; label: string }> = [
  { value: 'default', label: '默认排序' },
  { value: 'lastActivityAt', label: '最近活动时间' },
  { value: 'updateTime', label: '更新时间' },
  { value: 'submittedAt', label: '提交时间' },
  { value: 'createTime', label: '创建时间' },
  { value: 'ownershipStartedAt', label: '归属开始时间' },
  { value: 'lastFollowUpAt', label: '最近跟进时间' },
  { value: 'noProgressWarnedAt', label: '无进展提醒时间' },
  { value: 'pendingExpiresAt', label: '待处理到期时间' },
  { value: 'publicPoolAt', label: '进入公海时间' },
  { value: 'countedAt', label: '计入业绩时间' },
  { value: 'currentAssignmentFirstFollowUpAt', label: '本次分配首次跟进时间' },
  { value: 'currentAssignmentFirstFollowUpDeadlineAt', label: '本次分配首次跟进截止时间' },
  { value: 'qualificationStartedAt', label: '判定开始时间' },
  { value: 'qualificationDeadlineAt', label: '判定截止时间' },
  { value: 'suspendedAt', label: '挂起时间' },
  { value: 'appealDeadlineAt', label: '申诉截止时间' },
  { value: 'closedAt', label: '关闭时间' },
  { value: 'nextFollowUpAt', label: '下次跟进时间' },
  { value: 'qualifiedAt', label: '判定完成时间' },
  { value: 'convertedAt', label: '成交时间' },
  { value: 'leadNo', label: '客资编号' },
]

export default function LeadInboxSortControls({ field, order, submissionOnly = false, onChange }: {
  field?: LeadSortField
  order?: SortOrder
  submissionOnly?: boolean
  onChange: (field: LeadSortField | undefined, order: SortOrder | undefined) => void
}) {
  const hint = !field
    ? submissionOnly ? '客资提交时间，从新到旧' : '待办优先，再按提交时间从新到旧'
    : field === 'leadNo' ? order === 'ascend' ? '编号从小到大' : '编号从大到小'
      : fields.some(option => option.value === field)
        ? order === 'ascend' ? '时间从旧到新' : '时间从新到旧'
        : order === 'ascend' ? '按当前字段升序排列' : '按当前字段降序排列'
  return <div className="lead-inbox-sort-controls">
    <div className="lead-inbox-sort-fields">
      <label><Typography.Text type="secondary">排序字段</Typography.Text>
        <Select<LeadSortField | 'default'> aria-label="客资排序字段" value={field || 'default'} options={field && !fields.some(option => option.value === field) ? [...fields, { value: field, label: '当前表格排序字段' }] : fields}
          onChange={value => onChange(value === 'default' ? undefined : value, value === 'default' ? undefined : order || 'descend')} />
      </label>
      <label><Typography.Text type="secondary">排序方向</Typography.Text>
        <Select aria-label="客资排序方向" disabled={!field} value={field ? order : undefined} placeholder="默认"
          options={[{ value: 'ascend', label: '升序 ↑' }, { value: 'descend', label: '降序 ↓' }]}
          onChange={value => onChange(field, value)} />
      </label>
    </div>
    <Typography.Text type="secondary" className="lead-inbox-sort-hint">{hint}</Typography.Text>
  </div>
}
