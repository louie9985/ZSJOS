import assert from 'node:assert/strict'
import test from 'node:test'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { resolveAuthenticatedRouteNavigation } from '../src/utils/authenticatedLanding.ts'
import { parseRouteLocation } from '../src/utils/routeParams.ts'

const notFoundRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'NotFound',
  component: {}
}

const createAuthenticatedRouter = (dynamicRoutes: RouteRecordRaw[]) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [notFoundRoute]
  })
  let initialized = false
  let guardRuns = 0

  router.beforeEach((to) => {
    guardRuns += 1
    let routesJustAdded = false
    if (!initialized) {
      dynamicRoutes.forEach((route) => router.addRoute(route))
      initialized = true
      routesJustAdded = true
    }
    const target = resolveAuthenticatedRouteNavigation({
      currentPath: to.path,
      currentFullPath: to.fullPath,
      defaultLandingPath: '/system/user',
      routesJustAdded
    })
    return target ? { ...parseRouteLocation(target), replace: true } : true
  })

  return { router, getGuardRuns: () => guardRuns }
}

test('rematches a refreshed dynamic route after authorized routes are registered', async () => {
  const { router, getGuardRuns } = createAuthenticatedRouter([
    { path: '/system/user', name: 'SystemUser', component: {} }
  ])

  await router.push('/system/user?page=2#profile')

  assert.equal(router.currentRoute.value.name, 'SystemUser')
  assert.equal(router.currentRoute.value.fullPath, '/system/user?page=2#profile')
  assert.equal(getGuardRuns(), 2)
})

test('keeps a genuinely unknown route on the 404 matcher without looping', async () => {
  const { router, getGuardRuns } = createAuthenticatedRouter([
    { path: '/system/user', name: 'SystemUser', component: {} }
  ])

  await router.push('/missing?page=2#details')

  assert.equal(router.currentRoute.value.name, 'NotFound')
  assert.equal(router.currentRoute.value.fullPath, '/missing?page=2#details')
  assert.equal(getGuardRuns(), 2)
})
