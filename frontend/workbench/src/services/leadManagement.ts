export function mergeUniqueLeads<T extends { id: number }>(current: T[], incoming: T[]): T[] {
  const byId = new Map(current.map(item => [item.id, item]))
  incoming.forEach(item => byId.set(item.id, item))
  return Array.from(byId.values())
}

export function sumStatusCounts(counts: Record<string, number>): number {
  return Object.values(counts).reduce((total, count) => total + count, 0)
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

export function canJudgeLeadQualification(
  lead: Pick<import('./api').ManagedLead, 'handlingStage'>,
  audience: 'submitter' | 'owner',
  hasPermission: boolean
): boolean {
  return audience === 'owner' && hasPermission && lead.handlingStage === 'qualification_pending'
}

export function applyInvalidRemarkTemplate(_currentRemark: string, templateLabel: string): string {
  return templateLabel
}
