import { api, type MenuTaskSummary } from './api'

export type MenuTaskBadge = { count: number; urgent?: boolean }
export type MenuTaskBadgeResolver = (path: string) => MenuTaskBadge | undefined

export function buildMenuTaskBadgeMap(summary: MenuTaskSummary | undefined) {
  const map = new Map<string, MenuTaskBadge>()
  for (const item of summary?.items ?? []) map.set(item.menuPath, { count: item.count, urgent: item.severity === 'urgent' })
  return map
}

export function menuTaskBadgeLabel(label: string, badge: MenuTaskBadge | undefined) {
  return badge && badge.count > 0 ? `${label}（${badge.count > 99 ? '99+' : badge.count}）` : label
}

export { api }
