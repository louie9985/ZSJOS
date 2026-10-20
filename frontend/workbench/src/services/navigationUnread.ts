import { APP_ROUTES } from '../constants'
import type { MenuTaskBadge, MenuTaskBadgeResolver } from './menuTaskBadge'

export type NavigationUnreadCounts = {
  announcement: number
  message: number
}

function positive(value: number | undefined): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : 0
}

export function navigationUnreadCount(path: string, counts: NavigationUnreadCounts) {
  if (path === APP_ROUTES.ANNOUNCEMENTS) return positive(counts.announcement)
  if (path === APP_ROUTES.UNREAD_MESSAGES) return positive(counts.message)
  if (path === APP_ROUTES.MESSAGE_CENTER) return positive(counts.announcement) + positive(counts.message)
  return 0
}

export function createNavigationBadgeResolver(
  taskResolver: MenuTaskBadgeResolver,
  counts: NavigationUnreadCounts
): MenuTaskBadgeResolver {
  return path => {
    const task = taskResolver(path)
    const unreadCount = navigationUnreadCount(path, counts)
    if (!task && unreadCount === 0) return undefined
    return {
      count: (task?.count ?? 0) + unreadCount,
      ...(task?.urgent === undefined ? {} : { urgent: task.urgent })
    }
  }
}

export function badgeCountText(badge: MenuTaskBadge | undefined) {
  if (!badge || badge.count <= 0) return ''
  return badge.count > 99 ? '99+' : String(badge.count)
}
