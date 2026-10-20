import { describe, expect, it } from 'vitest'
import { APP_ROUTES } from '../constants'
import { createNavigationBadgeResolver, navigationUnreadCount, badgeCountText } from './navigationUnread'

describe('navigation unread badges', () => {
  const counts = { announcement: 3, message: 12 }

  it('maps announcement, message and parent paths to the authoritative counts', () => {
    expect(navigationUnreadCount(APP_ROUTES.ANNOUNCEMENTS, counts)).toBe(3)
    expect(navigationUnreadCount(APP_ROUTES.UNREAD_MESSAGES, counts)).toBe(12)
    expect(navigationUnreadCount(APP_ROUTES.MESSAGE_CENTER, counts)).toBe(15)
    expect(navigationUnreadCount(APP_ROUTES.ALL_MESSAGES, counts)).toBe(0)
  })

  it('merges notification counts with existing business task badges', () => {
    const resolver = createNavigationBadgeResolver(path => path === APP_ROUTES.UNREAD_MESSAGES
      ? { count: 2, urgent: true }
      : undefined, counts)
    expect(resolver(APP_ROUTES.UNREAD_MESSAGES)).toEqual({ count: 14, urgent: true })
    expect(resolver(APP_ROUTES.ANNOUNCEMENTS)).toEqual({ count: 3 })
    expect(resolver(APP_ROUTES.ALL_MESSAGES)).toBeUndefined()
  })

  it('does not create badges for zero counts and caps display text at 99+', () => {
    const resolver = createNavigationBadgeResolver(() => undefined, { announcement: 0, message: 0 })
    expect(resolver(APP_ROUTES.MESSAGE_CENTER)).toBeUndefined()
    expect(badgeCountText(undefined)).toBe('')
    expect(badgeCountText({ count: 99 })).toBe('99')
    expect(badgeCountText({ count: 100 })).toBe('99+')
  })
})
