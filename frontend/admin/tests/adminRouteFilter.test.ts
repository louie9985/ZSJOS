import assert from 'node:assert/strict'
import test from 'node:test'
import { filterAdminRoutes } from '../src/utils/adminRouteFilter.ts'

test('removes native Workbench routes that Vue Admin cannot render', () => {
  const routes = filterAdminRoutes([
    { path: '/native', component: 'zsjos-workbench', workbenchRenderMode: 'native' },
    { path: '/admin', component: 'zsjos/material/index', workbenchRenderMode: 'admin_embed' },
    { path: '/dual', component: 'zsjos/leadSubmission/index', workbenchRenderMode: 'native' }
  ])
  assert.deepEqual(
    routes.map((route) => route.path),
    ['/admin', '/dual']
  )
})

test('keeps a parent when at least one child is Vue-renderable', () => {
  const routes = filterAdminRoutes([
    {
      path: '/zsjos',
      children: [
        { path: 'native', component: 'zsjos-workbench', workbenchRenderMode: 'native' },
        { path: 'manage', component: 'zsjos/material/index', workbenchRenderMode: 'admin_embed' }
      ]
    }
  ])
  assert.equal(routes.length, 1)
  assert.deepEqual(
    routes[0].children?.map((route) => route.path),
    ['manage']
  )
})

test('drops an empty directory after all unsupported children are removed', () => {
  assert.deepEqual(
    filterAdminRoutes([
      {
        path: '/zsjos',
        children: [{ path: 'native', component: 'zsjos-workbench', workbenchRenderMode: 'native' }]
      }
    ]),
    []
  )
})

test('removes admin_only Workbench pages, which Vue Admin cannot resolve either', () => {
  const routes = filterAdminRoutes([
    { path: '/admin', component: 'zsjos/material/index', workbenchRenderMode: 'admin_embed' },
    { path: '/approvals', component: 'zsjos-workbench', workbenchRenderMode: 'admin_only' }
  ])
  assert.deepEqual(
    routes.map((route) => route.path),
    ['/admin']
  )
})

test('keeps the admin_embed page renderable when a Workbench-only child is removed', () => {
  const routes = filterAdminRoutes([
    {
      path: '/zsjos',
      children: [
        {
          path: 'manage',
          component: 'zsjos/material/index',
          workbenchRenderMode: 'admin_embed',
          children: [
            { path: 'approvals', component: 'zsjos-workbench', workbenchRenderMode: 'admin_only' }
          ]
        }
      ]
    }
  ])
  assert.deepEqual(routes[0].children?.map((route) => route.path), ['manage'])
  assert.deepEqual(routes[0].children?.[0].children, [])
})
