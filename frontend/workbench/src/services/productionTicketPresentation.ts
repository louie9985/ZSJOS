import { formatTimestamp } from './time'
export function ticketSnapshotText(value: unknown): string {
  if (value === undefined || value === null || value === '') return '未填写'
  if (Array.isArray(value)) return value.map(ticketSnapshotText).join('、')
  if (typeof value === 'object') {
    const snapshot = value as Record<string, unknown>
    return typeof snapshot.label === 'string' && snapshot.label ? snapshot.label : '历史显示内容未记录'
  }
  return String(value)
}
export const ticketStatuses: Record<string, string> = { pending_accept: '待接单', public_pool: '待抢单', accepted: '待开始', in_production: '制作中', submitted: '待核对', checking: '核对中', rejected: '待返工', completed: '已完成', cancelled: '已取消', assignment_rejected: '已拒接' }
export const ticketGroups = [{ key: 'todo', label: '待处理' }, { key: 'producing', label: '制作中' }, { key: 'review', label: '待核对' }, { key: 'completed', label: '已完成' }, { key: 'all', label: '全部' }]
export const groupStatuses: Record<string, string[]> = { todo: ['pending_accept', 'accepted', 'rejected'], producing: ['in_production'], review: ['submitted', 'checking'], completed: ['completed'] }
export function deadlinePresentation(deadline: number | undefined, status: string, now: number) {
  const date = formatTimestamp(deadline, '未设置截止时间')
  if (['completed', 'cancelled', 'assignment_rejected'].includes(status)) return { date, text: '已结束', tone: 'neutral' }
  if (!deadline || !Number.isFinite(deadline)) return { date, text: '暂无截止要求', tone: 'neutral' }
  const delta = deadline - now
  const minutes = Math.max(1, Math.ceil(Math.abs(delta) / 60000))
  const days = Math.floor(minutes / 1440), hours = Math.floor(minutes % 1440 / 60)
  const duration = `${days ? `${days}天 ` : ''}${hours ? `${hours}小时 ` : ''}${minutes % 60 || (!days && !hours) ? `${minutes % 60 || 1}分钟` : ''}`.trim()
  return { date, text: `${delta <= 0 ? '已超时' : '剩余'} ${duration}`, tone: delta <= 0 ? 'error' : delta <= 86400000 ? 'warning' : 'primary' }
}
export const ticketActionLabels: Record<string, string> = { ACCEPT_TICKET: '接单', REJECT_TICKET_ASSIGNMENT: '拒绝接单', CLAIM_TICKET: '抢单', START_TICKET: '开始制作', SUBMIT_TICKET: '提交成品', START_TICKET_CHECK: '开始核对', APPROVE_TICKET: '通过', REJECT_TICKET: '返工', REACCEPT_TICKET: '重新接单' }
export const ticketNextStep: Record<string, string> = { pending_accept: '阅读制作要求，确认后接单。', public_pool: '确认需求与交付时间后抢单。', accepted: '确认素材齐备，开始制作。', in_production: '按要求制作，完成后提交成品链接。', submitted: '等待运营核对成品。', checking: '核对交付要求，确认通过或说明返工原因。', rejected: '阅读返工原因，重新接单后修改。', completed: '成品已通过，工单已完成。', cancelled: '工单已取消。' }
export const historyLabels: Record<string, string> = { create: '发起工单', reject: '拒绝接单', 'production-pending-accept': '指定派单', 'production-pool': '进入抢单池', 'production-accept': '接单', 'production-start': '开始制作', 'production-submit': '提交成品', 'production-check': '开始核对', 'production-approve': '核对通过', 'production-return': '退回返工', 'production-reject': '拒绝接单', 'production-sync': '状态更新' }
