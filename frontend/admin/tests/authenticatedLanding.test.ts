import assert from 'node:assert/strict'
import test from 'node:test'
import {
  getAuthenticatedLandingPath,
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
  const options = { defaultLandingPath: '/zsjos/tasks/today' }
  assert.equal(
    resolveAuthenticatedRouteTarget({ currentPath: '/', ...options }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({ currentPath: '/index', ...options }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({ currentPath: '/system/user', ...options }),
    '/system/user'
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
      currentPath: '/zsjos/tasks/today',
      currentPathAuthorized: false,
      ...options
    }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({
      currentPath: '/',
      explicitRedirect: '/zsjos/tasks/today',
      explicitRedirectAuthorized: false,
      ...options
    }),
    options.defaultLandingPath
  )
  assert.equal(
    resolveAuthenticatedRouteTarget({ currentPath: '/', defaultLandingPath: '/403' }),
    '/403'
  )
})
