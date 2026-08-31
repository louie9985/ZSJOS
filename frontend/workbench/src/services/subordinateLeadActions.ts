import type { ManagedLead } from './api'

export type SupervisorLeadBatchAction = 'transfer' | 'restore' | 'recycle' | 'claimPool' | 'publicSea'

export const SUPERVISOR_LEAD_ACTION_LABELS: Record<SupervisorLeadBatchAction, string> = {
  transfer: '转派',
  restore: '恢复',
  recycle: '回收',
  claimPool: '释放至抢单池',
  publicSea: '释放至公海池',
}

const ASSIGNMENT_STATUS_LABELS: Record<string, string> = {
  unassigned: '未分配',
  pending_acceptance: '待接单',
  owned: '已归属',
  public_pool: '抢单池',
  recycle_pending: '回收待处理',
  closed: '已关闭',
}

export function subordinateAssignmentStatusLabel(status?: string) {
  if (!status) return '未知分配状态（空）'
  return ASSIGNMENT_STATUS_LABELS[status] || `未知分配状态（${status}）`
}

export function isSupervisorLeadActionAllowed(
  action: SupervisorLeadBatchAction,
  lead: Pick<ManagedLead, 'status' | 'assignmentStatus' | 'closedAt'>,
) {
  const ownedOrRecyclePending = lead.assignmentStatus === 'owned' || lead.assignmentStatus === 'recycle_pending'
  if (action === 'transfer') {
    return ['submitted', 'suspended', 'valid', 'converted'].includes(lead.status) && ownedOrRecyclePending
  }
  if (action === 'restore') return lead.status === 'suspended' && lead.assignmentStatus === 'owned'
  if (action === 'recycle') {
    return ['submitted', 'suspended'].includes(lead.status) && lead.assignmentStatus === 'owned'
  }
  if (action === 'claimPool') {
    return ['submitted', 'suspended'].includes(lead.status) && ownedOrRecyclePending
  }
  return ['valid', 'converted'].includes(lead.status) && lead.assignmentStatus === 'owned' && !lead.closedAt
}

export function supervisorLeadSelectionEligibility(
  action: SupervisorLeadBatchAction,
  selectedCount: number,
  leads: Array<Pick<ManagedLead, 'status' | 'assignmentStatus' | 'closedAt'>>,
) {
  const unavailableCount = selectedCount - leads.filter(lead => isSupervisorLeadActionAllowed(action, lead)).length
  return {
    allowed: selectedCount > 0 && unavailableCount === 0,
    unavailableCount,
    message: selectedCount === 0
      ? '请先选择客资'
      : unavailableCount > 0
        ? `已选 ${selectedCount} 条，其中 ${unavailableCount} 条不支持${SUPERVISOR_LEAD_ACTION_LABELS[action]}，请调整选择`
        : `已选 ${selectedCount} 条，全部符合${SUPERVISOR_LEAD_ACTION_LABELS[action]}的基础状态条件`,
  }
}
