import type { Timestamp } from './time'

export function mergeUniqueLeads<T extends { id: number }>(current: T[], incoming: T[]): T[] {
  const byId = new Map(current.map(item => [item.id, item]))
  incoming.forEach(item => byId.set(item.id, item))
  return Array.from(byId.values())
}

export function pinLeadFirst<T extends { id: number }>(items: T[], lead: T): T[] {
  return [lead, ...items.filter(item => item.id !== lead.id)]
}

export function prioritizeLeads<T extends { id: number }>(items: T[], priorityIds: number[]): T[] {
  if (!priorityIds.length) return items
  const priority = new Map(priorityIds.map((id, index) => [id, index]))
  return [...items].sort((left, right) => {
    const leftIndex = priority.get(left.id)
    const rightIndex = priority.get(right.id)
    if (leftIndex === undefined && rightIndex === undefined) return 0
    if (leftIndex === undefined) return 1
    if (rightIndex === undefined) return -1
    return leftIndex - rightIndex
  })
}

export function resolveLeadSelection<T extends { id: number }>(
  items: T[],
  options: { preferredId?: number; currentId?: number; requestedId?: number; preserveRequestedId?: boolean }
): number | undefined {
  const availableIds = new Set(items.map(item => item.id))
  if (options.preserveRequestedId && options.requestedId !== undefined) return options.requestedId
  const candidates = [options.preferredId, options.currentId, options.requestedId]
  return candidates.find(id => id !== undefined && availableIds.has(id)) ?? items[0]?.id
}

export function sumStatusCounts(counts: Record<string, number>): number {
  return Object.values(counts).reduce((total, count) => total + count, 0)
}

export function dictionaryDisplayLabel(
  options: Array<{ value: string; label: string }>,
  value?: string,
  loadFailed = false
): string {
  if (!value) return '-'
  const label = options.find(item => item.value === value)?.label
  if (label) return label
  return loadFailed ? '标签加载失败' : '标签未配置'
}

export function snapshotOrDictionaryDisplayLabel(
  snapshot: string | undefined,
  options: Array<{ value: string; label: string }>,
  value?: string,
  loadFailed = false
): string {
  return snapshot?.trim() || dictionaryDisplayLabel(options, value, loadFailed)
}

export function resolvedDisplayLabel(label?: string, value?: string): string {
  if (!value) return '-'
  return label || '标签未配置'
}

export function protocolDisplayLabel(
  labels: Record<string, string>,
  value: string | undefined,
  unknownLabel: string
): string {
  if (!value) return '-'
  return labels[value] || unknownLabel
}

const PROTOCOL_KEY_PATTERN = /^[a-z][a-z0-9_.-]*$/i

export function snapshotDisplayLabel(label?: string, value?: string): string {
  if (!label || PROTOCOL_KEY_PATTERN.test(label) || label === value) return '标签未配置'
  return label
}

export function invalidReasonSnapshotLabel(snapshot?: string): string {
  if (!snapshot || PROTOCOL_KEY_PATTERN.test(snapshot)) return '标签未配置'
  return snapshot
}

export function defaultInboxStage(
  groups: Array<{ key: string; sections: Array<{ options: Array<{ key: string }> }> }>,
  groupKey: string
): string {
  const group = groups.find(item => item.key === groupKey)
  return group?.sections[0]?.options[0]?.key || 'all'
}

export function tryStartLeadPageRequest(
  activeRequests: Set<string>,
  version: number,
  pageNo: number
): string | undefined {
  const key = `${version}:${pageNo}`
  if (activeRequests.has(key)) return undefined
  activeRequests.add(key)
  return key
}

export function isLeadInboxUnauthorized(message: string): boolean {
  return message.includes('403') || message.includes('无权') || message.includes('权限')
}

export function hasNextLeadInboxPage(pageNo: number, pageSize: number, total: number): boolean {
  return pageNo * pageSize < total
}

export function canJudgeLeadQualification(
  lead: Pick<import('./api').ManagedLead, 'qualificationStatus' | 'followUpStatus' | 'operationalStatus'>,
  audience: 'submitter' | 'owner',
  hasPermission: boolean
): boolean {
  return audience === 'owner' && hasPermission && lead.qualificationStatus === 'pending'
    && lead.followUpStatus === 'following' && lead.operationalStatus === 'active'
}

export function leadPendingTaskAlert(
  lead: Pick<import('./api').ManagedLead,
    'handlingStage' | 'operationalStatus' | 'currentAssignmentFirstFollowUpDeadlineAt' | 'qualificationDeadlineAt'>
): { message: string; deadline: Timestamp } | undefined {
  if (lead.operationalStatus !== 'active') return undefined
  if (lead.handlingStage === 'first_follow_pending' && lead.currentAssignmentFirstFollowUpDeadlineAt) {
    return { message: '待完成首次跟进', deadline: lead.currentAssignmentFirstFollowUpDeadlineAt }
  }
  if (lead.handlingStage === 'qualification_pending' && lead.qualificationDeadlineAt) {
    return { message: '待完成有效性判定', deadline: lead.qualificationDeadlineAt }
  }
  return undefined
}

export function applyInvalidRemarkTemplate(_currentRemark: string, templateLabel: string): string {
  return templateLabel
}
