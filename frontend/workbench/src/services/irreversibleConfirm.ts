export const irreversibleConfirmTitle = (action: string) => `确认执行「${action}」操作吗？`

export const IRREVERSIBLE_CONFIRM_DESCRIPTION = '该操作无法撤回。'

export type AssignmentConfirmMode = 'append' | 'replace' | 'remove'

const assignmentModeLabels: Record<AssignmentConfirmMode, string> = {
  append: '追加',
  replace: '替换',
  remove: '解除'
}

export const assignmentConfirmAction = (
  mode: AssignmentConfirmMode,
  subject: { batchCount: number } | { name: string }
) => 'batchCount' in subject
  ? `批量${assignmentModeLabels[mode]} ${subject.batchCount} 名员工的派单关系`
  : `${assignmentModeLabels[mode]}员工「${subject.name}」的派单关系`
