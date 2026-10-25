import { describe, expect, it } from 'vitest'
import { APP_ROUTES } from '../constants'
import { createNavigationBadgeResolver, navigationUnreadCount, badgeCountText } from './navigationUnread'
import type { WorkbenchMenu } from './api'
import { approvalOpenPaths } from './approvalNavigation'

const tree = [{ id: 1, path: '/flow', children: [{ id: 2, path: '/approval', children: [
  { id: 3, path: APP_ROUTES.BPM_TODO, children: [] },
  { id: 4, path: APP_ROUTES.BPM_DONE, children: [] }
] }] }] as unknown as WorkbenchMenu[]

it('aggregates nested workflow badges and excludes hidden subtrees', () => {
  const task = (path: string) => path === APP_ROUTES.BPM_TODO ? { count: 7 } : undefined
  const resolve = createNavigationBadgeResolver(task, { announcement: 0, message: 0 }, tree)
  expect(resolve('/flow')?.count).toBe(7)
  expect(resolve('/approval')?.count).toBe(7)
  expect(resolve(APP_ROUTES.BPM_DONE)).toBeUndefined()
  const hidden = [{ ...tree[0], children: [{ ...tree[0].children[0], hidden: true }] }]
  expect(createNavigationBadgeResolver(task, { announcement: 0, message: 0 }, hidden)('/flow')).toBeUndefined()
})

it('opens approval ancestors on unrelated and done pages', () => {
  expect(approvalOpenPaths(tree, '/calendar')).toEqual(['/flow', '/approval'])
  expect(approvalOpenPaths(tree, APP_ROUTES.BPM_DONE)).toEqual(['/flow', '/approval'])
  expect(approvalOpenPaths([], APP_ROUTES.BPM_TODO)).toEqual([])
})

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
