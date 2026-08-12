export const irreversibleConfirmTitle = (action: string) => `确认执行「${action}」操作吗？`

export const IRREVERSIBLE_CONFIRM_DESCRIPTION = '该操作无法撤回。'

export type RelationConfirmMode = 'append' | 'replace' | 'remove'

const relationModeLabels: Record<RelationConfirmMode, string> = {
  append: '追加',
  replace: '替换',
  remove: '解除'
}

export const relationConfirmAction = (
  mode: RelationConfirmMode,
  relationName: string,
  subject: { batchCount: number } | { name: string }
) =>
  'batchCount' in subject
    ? `批量${relationModeLabels[mode]} ${subject.batchCount} 名员工的${relationName}`
    : `${relationModeLabels[mode]}员工「${subject.name}」的${relationName}`

export const filterPublishConfirmAction = (audience: string) =>
  `发布「${audience}」筛选方案到工作台`

export const filterRollbackConfirmAction = (audience: string, versionNo: number) =>
  `将「${audience}」筛选方案回滚至 V${versionNo} 并重新发布`
