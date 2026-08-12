import { describe, expect, it } from 'vitest'
import { AuthenticationError, buildMenuTree, type RawMenu, unwrap } from './api'
import {
  buildTwoLevelNavigation,
  filterRenderableMenus,
  findPageByPath,
  findPrimaryByPath,
  getInaccessiblePathFallback,
  getInitialTarget,
  getPrimaryTarget
} from './menu'

const menu = (values: Partial<RawMenu> & Pick<RawMenu, 'id' | 'name'>): RawMenu => ({
  parentId: 0,
  visible: true,
  keepAlive: true,
  ...values
})

describe('workbench menu conversion', () => {
  it('rejects backend business errors instead of treating them as data', () => {
    expect(() => unwrap({ data: { code: 400, msg: 'tenant required', data: null } }))
      .toThrow('tenant required')
  })

  it('classifies the backend business 401 as an authentication error', () => {
    expect(() => unwrap({ data: { code: 401, msg: '账号未登录', data: null } }))
      .toThrow(AuthenticationError)
  })

  it('keeps backend roots and flattens visible descendant pages in tree order', () => {
    const routes = buildMenuTree([
      menu({
        id: 1,
        name: 'CRM',
        path: '/crm',
        icon: 'ep:user',
        children: [
          menu({
            id: 2,
            parentId: 1,
            name: 'Customers',
            path: 'customers',
            children: [menu({ id: 3, parentId: 2, name: 'Customer list', path: 'list' })]
          }),
          menu({ id: 4, parentId: 1, name: 'Leads', path: 'leads' })
        ]
      })
    ])

    const navigation = buildTwoLevelNavigation(routes)
    expect(navigation).toHaveLength(1)
    expect(navigation[0]).toMatchObject({ key: '1', label: 'CRM', icon: 'ep:user' })
    expect(navigation[0].pages.map(page => ({ key: page.key, label: page.label }))).toEqual([
      { key: '/crm/customers/list', label: 'Customer list' },
      { key: '/crm/leads', label: 'Leads' }
    ])
  })

  it('removes hidden roots and hidden descendant subtrees', () => {
    const routes = buildMenuTree([
      menu({
        id: 1,
        name: 'Hidden root',
        path: '/hidden',
        visible: false,
        children: [menu({ id: 2, parentId: 1, name: 'Child', path: 'child' })]
      }),
      menu({
        id: 3,
        name: 'Visible root',
        path: '/visible',
        children: [
          menu({
            id: 4,
            parentId: 3,
            name: 'Hidden directory',
            path: 'private',
            visible: false,
            children: [menu({ id: 5, parentId: 4, name: 'Private page', path: 'page' })]
          }),
          menu({ id: 6, parentId: 3, name: 'Public page', path: 'public' })
        ]
      })
    ])

    const navigation = buildTwoLevelNavigation(routes)
    expect(navigation.map(item => item.label)).toEqual(['Visible root'])
    expect(navigation[0].pages.map(page => page.key)).toEqual(['/visible/public'])
  })

  it('keeps directories for locally registered pages and removes Vue-only leaves', () => {
    const routes = buildMenuTree([
      menu({
        id: 1,
        name: 'Workbench',
        path: '/zsjos',
        children: [
          menu({ id: 2, parentId: 1, name: 'Work plans', path: 'work-plans' }),
          menu({ id: 3, parentId: 1, name: 'Plan config', path: 'work-plan-config' })
        ]
      }),
      menu({ id: 4, name: 'System', path: '/system' })
    ])

    const filtered = filterRenderableMenus(routes, new Set(['/zsjos/work-plans']))

    expect(filtered).toHaveLength(1)
    expect(filtered[0].name).toBe('Workbench')
    expect(filtered[0].children.map(child => child.name)).toEqual(['Work plans'])
  })

  it('supports a root that is itself a page', () => {
    const navigation = buildTwoLevelNavigation(buildMenuTree([
      menu({ id: 1, name: 'Dashboard', path: '/dashboard', component: 'dashboard/index' })
    ]))

    expect(navigation[0].pages).toEqual([])
    expect(getPrimaryTarget(navigation[0])).toBe('/dashboard')
    expect(findPrimaryByPath(navigation, '/dashboard')).toBe(navigation[0])
    expect(findPageByPath(navigation, '/dashboard')?.name).toBe('Dashboard')
  })

  it('resolves deep links back to their primary and page', () => {
    const navigation = buildTwoLevelNavigation(buildMenuTree([
      menu({
        id: 1,
        name: 'CRM',
        path: '/crm',
        children: [menu({ id: 2, parentId: 1, name: 'Leads', path: 'leads' })]
      })
    ]))

    expect(findPrimaryByPath(navigation, '/crm/leads')?.label).toBe('CRM')
    expect(findPageByPath(navigation, '/crm/leads')?.name).toBe('Leads')
  })

  it('skips external links for automatic landing but keeps them clickable', () => {
    const navigation = buildTwoLevelNavigation(buildMenuTree([
      menu({ id: 1, name: 'Docs', path: 'https://example.com' }),
      menu({
        id: 2,
        name: 'CRM',
        path: '/crm',
        children: [menu({ id: 3, parentId: 2, name: 'Leads', path: 'leads' })]
      })
    ]))

    expect(getPrimaryTarget(navigation[0])).toBe('https://example.com')
    expect(getInitialTarget(navigation)).toBe('/crm/leads')
  })

  it('replaces an inaccessible route with the first authorized internal page', () => {
    const navigation = buildTwoLevelNavigation(buildMenuTree([
      menu({
        id: 1,
        name: 'Workbench',
        path: '/zsjos',
        children: [menu({ id: 2, parentId: 1, name: 'Appeals', path: 'appeals' })]
      })
    ]))

    expect(getInaccessiblePathFallback(navigation, '/zsjos/tasks/today')).toBe('/zsjos/appeals')
    expect(getInaccessiblePathFallback(navigation, '/zsjos/appeals')).toBeUndefined()
    expect(getInaccessiblePathFallback(navigation, '/')).toBeUndefined()
  })
})
