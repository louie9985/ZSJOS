import type { PositioningCard } from './api'

export function positioningStatus(card: Pick<PositioningCard, 'status' | 'submissionStatus' | 'evidence' | 'evidenceRequired'>): string {
  if (['confirmed', 'superseded', 'student_agreed'].includes(card.status)) {
    return card.evidence?.length ? '学员已确认，运营凭证已上传' : card.evidenceRequired ? '确认凭证缺失' : '历史已确认，未留存凭证'
  }
  if (card.status === 'co_creating' && card.submissionStatus === 'operator_rejected') return '运营已驳回，待编导修改'
  if (card.status === 'co_creating' && card.submissionStatus === 'change_requested') return '学员要求修改，待编导修订'
  const labels: Record<string, string> = {
    co_creating: '编导待完善／提交', operator_feasibility: '待运营复核', operator_rejected: '运营已驳回，待编导修改',
    student_link_pending: '运营复核通过，待生成学员确认链接', student_confirm: '待学员确认',
    student_evidence_pending: '学员已确认，待运营上传凭证', change_requested: '学员要求修改，待编导修订',
    evidence_revision_closed: '学员已确认，已发起修订（本轮未完成凭证）',
  }
  return labels[card.status] || card.status
}
