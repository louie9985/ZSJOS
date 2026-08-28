import { describe, expect, it } from 'vitest'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'
import { AuthenticationError, buildMenuTree, type RawMenu, unwrap } from './api'
import {
  buildTwoLevelNavigation,
  canOpenLeadDetailDeepLink,
  filterRenderableMenus,
  findMenuByPath,
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

  it('keeps visible descendant directories in the server hierarchy', () => {
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
      { key: '2', label: 'Customers' },
      { key: '/crm/leads', label: 'Leads' }
    ])
    expect(navigation[0].pages[0].children.map(page => ({ key: page.key, label: page.label }))).toEqual([
      { key: '/crm/customers/list', label: 'Customer list' }
    ])
  })

  it('keeps projected page URLs absolute when navigation groups change', () => {
    const projected = buildMenuTree([
      menu({
        id: -1,
        name: '薪酬服务',
        path: '/__workbench-group/payroll',
        children: [menu({
          id: 14,
          sourceMenuId: 14,
          parentId: -1,
          name: '我的工资条',
          path: '/hrm/portal/salary/slip'
        })]
      })
    ], '/', true)

    expect(projected[0].children[0].path).toBe('/hrm/portal/salary/slip')
    expect(buildTwoLevelNavigation(projected)[0].pages[0].key).toBe('/hrm/portal/salary/slip')
  })

  it('uses the authorized tree for direct routes even when the projection hides the page', () => {
    const authorized = buildMenuTree([
      menu({ id: 14, name: '我的工资条', path: '/hrm/portal/salary/slip' })
    ])
    const navigation = buildTwoLevelNavigation([])

    expect(findMenuByPath(authorized, '/hrm/portal/salary/slip')?.id).toBe(14)
    expect(getInaccessiblePathFallback(
      navigation,
      '/hrm/portal/salary/slip',
      authorized
    )).toBeUndefined()
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

  it('keeps admin embeds, excludes admin-only menus, and preserves their server hierarchy', () => {
    const routes = filterRenderableMenus(buildMenuTree([
      menu({
        id: 10,
        name: '系统',
        path: '/system',
        children: [
          menu({ id: 11, parentId: 10, name: '用户管理', path: 'user', workbenchRenderMode: 'admin_embed' }),
          menu({ id: 12, parentId: 10, name: '菜单管理', path: 'menu', workbenchRenderMode: 'admin_only' })
        ]
      })
    ]), RENDERABLE_APP_ROUTES)

    expect(routes[0]?.children.map(child => ({ name: child.name, mode: child.workbenchRenderMode }))).toEqual([
      { name: '用户管理', mode: 'admin_embed' }
    ])
  })

  it('keeps native work-order pages under a shared admin-embed center', () => {
    const routes = filterRenderableMenus(buildMenuTree([
      menu({
        id: 79972,
        name: '工单中心',
        path: '/zsjos/work-orders',
        workbenchRenderMode: 'admin_embed',
        children: [
          menu({ id: 79973, parentId: 79972, name: '工单模板', path: 'templates', workbenchRenderMode: 'admin_only' }),
          menu({ id: 79977, parentId: 79972, name: '运行审计', path: 'audit', workbenchRenderMode: 'admin_only' }),
          menu({ id: 79961, parentId: 79972, name: '发起工单', path: 'create', workbenchRenderMode: 'native' }),
          menu({ id: 79962, parentId: 79972, name: '可接工单', path: 'available', workbenchRenderMode: 'native' }),
          menu({ id: 79963, parentId: 79972, name: '我的工单', path: 'mine', workbenchRenderMode: 'native' })
        ]
      })
    ]), RENDERABLE_APP_ROUTES)

    expect(routes[0]?.name).toBe('工单中心')
    expect(routes[0]?.children.map(child => child.path)).toEqual([
      '/zsjos/work-orders/create',
      '/zsjos/work-orders/available',
      '/zsjos/work-orders/mine'
    ])
  })

  it('covers all server-owned page routes and excludes obsolete aliases', () => {
    // 迁移基线包含 HRM 员工设置、历史工资表和账号日历等正式服务端页面。
    expect(RENDERABLE_APP_ROUTES.size).toBe(52)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/media-students')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/calendar/overview')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/my-assets')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/asset-demands')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/feedback')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/work-orders/create')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/work-orders/available')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/work-orders/mine')).toBe(true)
    expect([...RENDERABLE_APP_ROUTES]).not.toContain('/zsjos/accounts')
    expect([...RENDERABLE_APP_ROUTES]).not.toContain('/zsjos/content')
    expect([...RENDERABLE_APP_ROUTES]).not.toContain('/zsjos/positioning')
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/appeals')).toBe(true)
    expect([...RENDERABLE_APP_ROUTES] as string[]).not.toContain('/zsjos/leads/qualification-exceptions')
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/lead-aging-pool')).toBe(true)
    expect(RENDERABLE_APP_ROUTES.has('/zsjos/sales-orders/team')).toBe(true)
    expect([...RENDERABLE_APP_ROUTES]).not.toContain('/zsjos/leads/appeals')
    expect([...RENDERABLE_APP_ROUTES]).not.toContain('/zsjos/opportunity-public-sea')
    expect([...RENDERABLE_APP_ROUTES]).not.toContain('/zsjos/sales-order-supervisor-confirmations')
  })

  it('keeps an authorized hidden page routable without adding it to navigation', () => {
    const routes = filterRenderableMenus(buildMenuTree([menu({
      id: 1,
      name: 'Workbench',
      path: '/zsjos',
      children: [menu({ id: 2, parentId: 1, name: 'Lead management', path: 'leads/manage', visible: false })]
    })]), RENDERABLE_APP_ROUTES)
    const navigation = buildTwoLevelNavigation(routes)

    expect(findMenuByPath(routes, APP_ROUTES.LEAD_MANAGEMENT)?.name).toBe('Lead management')
    expect(navigation[0]?.pages).toEqual([])
    expect(getInaccessiblePathFallback(navigation, APP_ROUTES.LEAD_MANAGEMENT, routes)).toBeUndefined()
  })

  it('allows an object-authorized Lead overview deep link even when every history tab is hidden', () => {
    expect(canOpenLeadDetailDeepLink(
      APP_ROUTES.LEAD_MANAGEMENT,
      '?leadId=42',
      ['zsjos:sales-order:review']
    )).toBe(true)
    expect(canOpenLeadDetailDeepLink(
      APP_ROUTES.LEAD_MANAGEMENT,
      '?leadId=42',
      ['zsjos:lead-detail:follow-up-read']
    )).toBe(true)
    expect(canOpenLeadDetailDeepLink(
      APP_ROUTES.LEAD_MANAGEMENT,
      '?leadId=42',
      ['zsjos:lead:appeal:create']
    )).toBe(false)
    expect(canOpenLeadDetailDeepLink(
      APP_ROUTES.LEAD_MANAGEMENT,
      '',
      ['zsjos:sales-order:review']
    )).toBe(false)
  })

  it('exposes the unified Lead management page when the server marks it visible', () => {
    const routes = filterRenderableMenus(buildMenuTree([menu({
      id: 6735,
      name: 'Workbench',
      path: '/zsjos',
      children: [menu({ id: 6770, parentId: 6735, name: '客资管理', path: 'leads/manage', visible: true })]
    })]), RENDERABLE_APP_ROUTES)

    expect(buildTwoLevelNavigation(routes)[0]?.pages.map(page => page.key)).toContain('/zsjos/leads/manage')
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
    expect(getInaccessiblePathFallback(navigation, APP_ROUTES.USER_PROFILE)).toBeUndefined()
  })
})
