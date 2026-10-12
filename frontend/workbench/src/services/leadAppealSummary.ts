import type { ManagedLead } from './api'

const statusLabels: Record<string, string> = {
  sales_manager_reviewing: '待负责人主管审核', quality_reviewing: '待质控审核', chairman_reviewing: '待董事长终审',
  overturned: '已改判有效', upheld: '维持无效', withdrawn: '已撤回',
}
export function isLeadAppealPending(lead: Pick<ManagedLead, 'appealSummary'>) {
  return ['sales_manager_reviewing', 'quality_reviewing', 'chairman_reviewing'].includes(lead.appealSummary?.status ?? '')
}
export function leadAppealSummaryLabel(lead: Pick<ManagedLead, 'appealSummary'>) {
  const summary = lead.appealSummary
  return summary ? `第 ${summary.roundNo} 轮 · ${statusLabels[summary.status] || '未知申诉状态'}` : '暂无申诉'
}
