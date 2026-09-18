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

/**
 * 跨流程通用的少数流程变量名 → 中文。
 *
 * <p>真正的业务字段由后端 Provider 下发 label，前端不对业务语义做映射，
 * 否则新增流程就要改前端。这里只放发起人、时间这类每个流程都有的东西。
 *
 * <p><b>命中不到时不要退回 key</b>：流程变量绝大多数是发起时写的路由变量
 * （{@code hasDepartmentLeader}、{@code chairmanAssignee} 之类），只服务于网关条件
 * 与多任务候选人计算，审批人既看不懂也不该看到。调用方应据此丢弃该项。
 */
export const BPM_VARIABLE_LABELS: Record<string, string> = {
  startUserId: '发起人',
  externalStartUserName: '发起人',
  startTime: '发起时间',
  processDefinitionName: '流程名称',
  assignee: '审批人',
  coll_userList: '抄送人',
  reason: '原因',
  description: '说明',
  submittedAt: '提交时间',
  applicantUserId: '申请人'
}

/** 已中文化的变量名；映射不到时返回 undefined（调用方应丢弃该项，而非显示英文 key）。 */
export function bpmVariableLabel(key: string) {
  return BPM_VARIABLE_LABELS[key]
}

/** 文本里有没有中文——用于判断后端下发的 label 是否已本地化。 */
export function hasChinese(text: string) {
  return /[一-龥]/.test(text)
}
