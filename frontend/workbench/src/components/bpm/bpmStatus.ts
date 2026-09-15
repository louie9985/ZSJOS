/** BPM 任务与流程实例状态，取值与后端 BpmTaskStatusEnum 一致。 */
const TASK_STATUS_LABELS: Record<number, string> = {
  [-2]: '已跳过',
  [-1]: '未开始',
  0: '待审批',
  1: '审批中',
  2: '已通过',
  3: '已拒绝',
  4: '已取消',
  5: '已退回',
  7: '通过中'
}

const TASK_STATUS_COLORS: Record<number, string> = {
  [-2]: 'default',
  [-1]: 'default',
  0: 'processing',
  1: 'processing',
  2: 'success',
  3: 'error',
  4: 'default',
  5: 'warning',
  7: 'processing'
}

export function bpmStatusLabel(status?: number) {
  return status === undefined ? '未知状态' : TASK_STATUS_LABELS[status] || `状态 ${status}`
}

export function bpmStatusColor(status?: number) {
  return status === undefined ? 'default' : TASK_STATUS_COLORS[status] || 'default'
}
