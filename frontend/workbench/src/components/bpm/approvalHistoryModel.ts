import type { BpmApprovalNode, BpmApprovalNodeTask, BpmComment, BpmTask } from '../../services/api'

export type HistoryTask = BpmApprovalNodeTask & { name?: string; parentTaskId?: string }
/** Identity, not labels, links repeated nodes and sign subtasks to their historical occurrence. */
export function approvalHistory(nodes: BpmApprovalNode[], records: BpmTask[], comments: BpmComment[] = []) {
  // Migration writes a dedicated audit comment on the exact task, including older instances with stale task status.
  const skippedTaskIds = new Set(comments.filter(comment => comment.type === '10').map(comment => comment.taskId))
  const applyMigrationStatus = (task: HistoryTask): HistoryTask =>
    skippedTaskIds.has(task.id) ? { ...task, status: -2 } : task
  const byId = new Map<string, HistoryTask>()
  const visit = (task: BpmTask, parentTaskId?: string) => {
    if (byId.has(task.id)) return
    byId.set(task.id, applyMigrationStatus({ ...task, parentTaskId: task.parentTaskId ?? parentTaskId }))
    task.children?.forEach(child => visit(child, task.id))
  }
  records.forEach(task => visit(task))
  const used = new Set<string>()
  const groups = nodes.map(node => {
    const tasks: HistoryTask[] = []
    for (const task of node.tasks ?? []) {
      if (used.has(task.id)) continue
      const extra = byId.get(task.id)
      tasks.push(applyMigrationStatus({ ...extra, ...Object.fromEntries(Object.entries(task).filter(([, value]) => value != null)) } as HistoryTask))
      used.add(task.id)
    }
    const status = tasks.length && tasks.every(task => task.status === -2) ? -2 : node.status
    return { ...node, status, tasks }
  })
  // Explicit node memberships win over parent fallback, regardless of node ordering.
  for (const node of groups) {
    for (let index = 0; index < node.tasks.length; index++) {
      for (const task of byId.values()) {
        if (!used.has(task.id) && task.parentTaskId === node.tasks[index].id) {
          node.tasks.push(task); used.add(task.id)
        }
      }
    }
  }
  return { nodes: groups, extra: [...byId.values()].filter(task => !used.has(task.id)) }
}

export function approvalStatus(status: number | null | undefined, label: (value: number) => string) {
  return status == null || !Number.isFinite(status) ? '状态未返回' : label(status)
}
