import type { LeadAppeal } from './api'

export const appealStatusLabels: Record<LeadAppeal['status'], string> = {
  sales_manager_reviewing: '负责人主管复核中', quality_reviewing: '质控复核中', chairman_reviewing: '董事长终审中',
  overturned: '已改判有效', upheld: '维持无效', withdrawn: '已撤回',
}
export const appealStageLabels: Record<LeadAppeal['reviewStage'], string> = {
  sales_manager: '负责人主管复核', quality: '质控复核', chairman: '董事长终审',
}
export function canDecideAppeal(item?: LeadAppeal, targetId?: number) {
  return Boolean(item && item.id === targetId && item.currentCycle === true && item.taskId && item.status.endsWith('_reviewing'))
}
export function selectAppealRecord(items: LeadAppeal[], targetId?: number) {
  if (targetId != null) return items.find(item => item.id === targetId)
  return items.filter(item => item.currentCycle).at(-1) ?? items.at(-1)
}
export function groupAppealCycles(items: LeadAppeal[]) {
  const groups = new Map<string, { key: string; current: boolean; items: LeadAppeal[] }>()
  for (const item of items) {
    // Legacy uncorrelated records share an explicitly labelled history group, never an invented cycle.
    const key = String(item.invalidEventId ?? 0)
    const group = groups.get(key) ?? { key, current: item.currentCycle === true, items: [] }
    group.items.push(item)
    groups.set(key, group)
  }
  return [...groups.values()].sort((a, b) => Number(b.current) - Number(a.current))
}
