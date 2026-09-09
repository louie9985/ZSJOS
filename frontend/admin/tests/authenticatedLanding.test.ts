import assert from 'node:assert/strict'
import test from 'node:test'
import {
  getAuthenticatedLandingPath,
  resolveAuthenticatedRouteNavigation,
  resolveAuthenticatedRouteTarget
} from '../src/utils/authenticatedLanding.ts'

const menu = (path: string, options: Record<string, unknown> = {}) => ({
  path,
  visible: true,
  ...options
})

test('prefers the authorized Workbench Today Tasks page', () => {
  assert.equal(
    getAuthenticatedLandingPath([
      menu('/system', { children: [menu('user')] }),
      menu('/zsjos', { children: [menu('tasks/today')] })
    ]),
    '/zsjos/tasks/today'
  )
})

test('falls back to the first authorized internal leaf page', () => {
  assert.equal(
    getAuthenticatedLandingPath([
      menu('/docs', { path: 'https://example.com' }),
      menu('/system', { children: [menu('user')] }),
      menu('/detail/:id'),
      menu('/hidden', { visible: false })
    ]),
    '/system/user'
  )
})

test('returns 403 when no authorized internal page exists', () => {
  assert.equal(
    getAuthenticatedLandingPath([
      menu('/folder', { visible: false }),
      menu('https://example.com'),
      menu('/detail/:id')
    ]),
    '/403'
  )
})

test('skips external URLs supported by the shared router URL matcher', () => {
  assert.equal(
    getAuthenticatedLandingPath([
      menu('www.example.com'),
      menu('user', { path: '/system', children: [menu('user')] })
    ]),
    '/system/user'
  )
  assert.equal(
    getAuthenticatedLandingPath([
      menu('operator@example.com'),
      menu('/system', { children: [menu('user')] })
    ]),
    '/system/user'
  )
})

test('resolves default landing for root paths while preserving explicit deep links', () => {
  const options = { currentFullPath: '/', defaultLandingPath: '/zsjos/tasks/today' }
  assert.equal(
    resolveAuthenticatedRouteTarget({ currentPath: '/', ...options }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({
      currentPath: '/index',
      ...options,
      currentFullPath: '/index'
    }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({
      currentPath: '/system/user',
      ...options,
      currentFullPath: '/system/user?page=2#profile'
    }),
    '/system/user?page=2#profile'
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({
      currentPath: '/',
      explicitRedirect: '/zsjos/leads/manage',
      ...options
    }),
    '/zsjos/leads/manage'
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({ currentPath: '/', explicitRedirect: '/index', ...options }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({
      currentPath: '/',
      currentFullPath: '/',
      defaultLandingPath: '/403'
    }),
    '/403'
  )
})

test('requests navigation after route registration or when the target changes', () => {
  const options = {
    currentPath: '/system/user',
    currentFullPath: '/system/user?page=2#profile',
    defaultLandingPath: '/zsjos/tasks/today'
  }
  assert.equal(
    resolveAuthenticatedRouteNavigation({ ...options, routesJustAdded: true }),
    options.currentFullPath
  )
  assert.equal(
    resolveAuthenticatedRouteNavigation({ ...options, routesJustAdded: false }),
    undefined
  )
  assert.equal(
    resolveAuthenticatedRouteNavigation({
      ...options,
      currentPath: '/',
      currentFullPath: '/',
      routesJustAdded: false
    }),
    options.defaultLandingPath
  )
})
