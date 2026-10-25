import { Badge, Tooltip } from 'antd'
import type { ManagedLead } from '../services/api'
import { formatTimestamp } from '../services/time'

export function leadTaskReminderText(lead: Pick<ManagedLead, 'pendingTasks'>) {
  return (lead.pendingTasks ?? []).map(task => `${task.overdue ? '已逾期：' : ''}${task.title}`).join('；')
}

export default function LeadTaskIndicator({ lead, compact = false }: { lead: Pick<ManagedLead, 'pendingTasks'>; compact?: boolean }) {
  const tasks = lead.pendingTasks ?? []
  if (!tasks.length) return null
  const label = `待处理 ${tasks.length} 项：${leadTaskReminderText(lead)}`
  return <Tooltip title={<div>{tasks.map(task => <div key={task.id}>
    {task.overdue ? '已逾期：' : ''}{task.title} · {task.dueAt ? `截止 ${formatTimestamp(task.dueAt)}` : '未设置截止时间'}
  </div>)}</div>} trigger={['hover', 'focus']}>
    <span className={`lead-task-indicator${compact ? ' is-compact' : ''}`} tabIndex={compact ? 0 : undefined} aria-label={label}>
      <Badge status="error" />
      {!compact && <span className="lead-task-reason">{leadTaskReminderText(lead)}{tasks.length > 1 ? `（${tasks.length} 项）` : ''}</span>}
    </span>
  </Tooltip>
}
