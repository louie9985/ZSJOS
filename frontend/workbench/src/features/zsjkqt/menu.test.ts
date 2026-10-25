import { describe, expect, it } from 'vitest'
import { filterAdminRoutes } from '../../../../admin/src/utils/adminRouteFilter'
import { filterRenderableMenus, findAdminEmbedPath } from '../../services/menu'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../../constants'
import type { WorkbenchMenu } from '../../services/api'

describe('HRM attendance menu runtime boundary', () => {
  const flags = { hidden: false, noCache: false, alwaysShow: true }
  const menus: WorkbenchMenu[] = [{ ...flags, id: 1, parentId: 0, name: 'HRM', path: '/hrm', workbenchRenderMode: 'admin_embed', children: [
    { ...flags, id: 2, parentId: 1, name: '中世健考勤通', path: APP_ROUTES.ZSJKQT, component: 'zsjos-workbench', workbenchRenderMode: 'native', children: [] },
    { ...flags, id: 3, parentId: 1, name: '员工管理', path: '/hrm/employee/list', component: 'hrm/employee/list/index', workbenchRenderMode: 'admin_embed', children: [] }
  ] }]
  it('renders the configured native child without inheriting the parent iframe', () => {
    expect(filterRenderableMenus(menus, RENDERABLE_APP_ROUTES)[0].children.map(m => m.path)).toContain(APP_ROUTES.ZSJKQT)
    expect(findAdminEmbedPath(menus, APP_ROUTES.ZSJKQT)).toBeUndefined()
    expect(findAdminEmbedPath(menus, '/hrm/employee/list')).toBe('/hrm/employee/list')
  })
  it('filters only the React-only child in Vue Admin and does not invent unauthorized menus', () => {
    expect(filterAdminRoutes(menus)[0].children.map(m => m.id)).toEqual([3])
    expect(filterRenderableMenus([], RENDERABLE_APP_ROUTES)).toEqual([])
  })
})
