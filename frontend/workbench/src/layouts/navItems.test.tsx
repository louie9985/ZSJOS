import { describe, expect, it } from 'vitest'
import { buildHierarchicalNavMenuItems, buildHierarchicalSecondaryItems, buildNavMenuItems } from './navItems'
import type { PrimaryNavigationItem } from '../services/menu'
import { getNavigationOpenKeys } from '../services/menu'
import type { WorkbenchMenu } from '../services/api'

const menu = (id: number, name: string, path: string): WorkbenchMenu =>
  ({ id, name, path, children: [] } as unknown as WorkbenchMenu)

const primary = (id: number, name: string, path: string, pages: Array<[string, string]>): PrimaryNavigationItem => ({
  key: String(id),
  label: name,
  icon: 'mdi:home',
  menu: menu(id, name, path),
  pages: pages.map(([label, page]) => ({ key: page, label, icon: 'mdi:file', menu: menu(0, label, page), children: [] }))
})

// any-casts below: MenuItem is a wide union, narrowing each variant adds no signal
const items = (nav: PrimaryNavigationItem[], opts?: Parameters<typeof buildNavMenuItems>[1]) =>
  buildNavMenuItems(nav, opts) as any[]

describe('buildNavMenuItems', () => {
  it('renders a primary with pages as a submenu keyed by primary key', () => {
    const [item] = items([primary(1, '客资', '/lead', [['我的客资', '/lead/mine']])])
    expect(item.key).toBe('1')
    expect(item.children).toHaveLength(1)
    expect(item.children[0].key).toBe('/lead/mine')
  })

  it('renders a primary without pages as a leaf keyed by its own path', () => {
    // a submenu here would produce an empty popup, and Menu#onClick never fires
    // for submenu titles — the page would be unreachable
    const [item] = items([primary(2, '工作台', '/dashboard', [])])
    expect(item.key).toBe('/dashboard')
    expect(item.children).toBeUndefined()
  })

  it('keeps the plain label as title so collapsed tooltips stay readable', () => {
    const [item] = items([primary(1, '客资', '/lead', [['我的客资', '/lead/mine']])], {
      expandSuffix: <span>v</span>
    })
    expect(item.title).toBe('客资')
    expect(typeof item.label).toBe('object')
  })

  it('omits the expand suffix on leaf primaries', () => {
    const [item] = items([primary(2, '工作台', '/dashboard', [])], { expandSuffix: <span>v</span> })
    expect(item.label).toBe('工作台')
  })

  it('wraps children in a group carrying the primary label when groupChildren is set', () => {
    const [item] = items([primary(1, '客资', '/lead', [['我的客资', '/lead/mine']])], { groupChildren: true })
    expect(item.children).toHaveLength(1)
    expect(item.children[0].type).toBe('group')
    expect(item.children[0].label).toBe('客资')
    // leaf keys must survive the extra nesting or selectedKeys stops matching
    expect(item.children[0].children[0].key).toBe('/lead/mine')
  })

  it('drops child icons when childIcons is false', () => {
    const [item] = items([primary(1, '客资', '/lead', [['我的客资', '/lead/mine']])], { childIcons: false })
    expect(item.children[0].icon).toBeUndefined()
    // the primary keeps its icon regardless
    expect(item.icon).toBeDefined()
  })

  it('passes popupClassName through only for submenus', () => {
    const built = items(
      [primary(1, '客资', '/lead', [['我的客资', '/lead/mine']]), primary(2, '工作台', '/dashboard', [])],
      { popupClassName: 'mini-flyout' }
    )
    expect(built[0].popupClassName).toBe('mini-flyout')
    expect(built[1].popupClassName).toBeUndefined()
  })

  it('renders deep directories as nested submenus instead of flattening them', () => {
    const navigation = primary(1, '运营管理', '/ops', [])
    navigation.pages = [{
      key: '2',
      label: '运营台账',
      icon: 'mdi:folder',
      menu: menu(2, '运营台账', '/ops/ledger'),
      children: [{
        key: '/ops/ledger/items',
        label: '运营列表',
        icon: 'mdi:file',
        menu: menu(3, '运营列表', '/ops/ledger/items'),
        children: []
      }]
    }]

    const [item] = items([navigation])
    expect(item.children[0].key).toBe('2')
    expect(item.children[0].children[0].key).toBe('/ops/ledger/items')
  })
})

describe('hierarchical menu builders', () => {
  it('keeps server directory levels in the merged sider tree', () => {
    const root = menu(1, '工作台', '/zsjos')
    root.children = [
      { ...menu(2, '配置', '/zsjos/config'), parentId: 1, children: [
        { ...menu(3, '项目', '/zsjos/config/items'), parentId: 2 }
      ] }
    ]
    const navigation = [{
      key: '1', label: '工作台', icon: 'mdi:home', menu: root, pages: []
    }] as unknown as PrimaryNavigationItem[]

    const [item] = buildHierarchicalNavMenuItems(navigation) as any[]
    expect(item.children[0].key).toBe('/zsjos/config')
    expect(item.children[0].children[0].key).toBe('/zsjos/config/items')
  })

  it('builds secondary menu from the active server root without flattening', () => {
    const root = menu(1, '工作台', '/zsjos')
    root.children = [{
      ...menu(2, '配置', '/zsjos/config'),
      parentId: 1,
      children: [{ ...menu(3, '项目', '/zsjos/config/items'), parentId: 2 }]
    }]
    const [item] = buildHierarchicalSecondaryItems(root) as any[]
    expect(item.key).toBe('/zsjos/config')
    expect(item.children[0].key).toBe('/zsjos/config/items')
  })
})

describe('mobile drawer open-key recovery', () => {
  // 抽屉用 getNavigationOpenKeys 求当前页的完整祖先链作为受控 openKeys；
  // 该链必须与 buildNavMenuItems 产出的 Menu 节点 key 一致，否则展开树点不到页面。
  it('returns the full ancestor chain for a deep leaf as a keyed tree matching the menu items', () => {
    // 移动抽屉收到的是 buildTwoLevelNavigation 的 navigation：一级必有 pages，
    // 二级容器在自身的 children 里继续挂深层页面。
    const config = menu(2, '配置', '/zsjos/config')
    config.children = [{ ...menu(3, '项目', '/zsjos/config/items'), parentId: 2, children: [] }]
    const navigation = [{
      key: '1', label: '工作台', icon: 'mdi:home', menu: menu(1, '工作台', '/zsjos'),
      pages: [{
        key: '2', label: '配置', icon: 'mdi:folder', menu: config,
        children: [{ key: '/zsjos/config/items', label: '项目', icon: 'mdi:file', menu: config.children[0], children: [] }]
      }]
    }] as unknown as PrimaryNavigationItem[]

    const built = buildNavMenuItems(navigation) as any[]
    const openKeys = getNavigationOpenKeys(navigation, '/zsjos/config/items')

    // 容器节点用 menu.id、叶子用 menu.path，与 buildNavMenuItems 的 key 规则一致
    expect(built[0].key).toBe('1')
    expect(built[0].children[0].key).toBe('2')
    expect(built[0].children[0].children[0].key).toBe('/zsjos/config/items')
    expect(openKeys).toEqual(['1', '2'])
  })

  it('returns an empty chain for an unknown or root path', () => {
    const root = menu(1, '工作台', '/zsjos')
    root.children = [{ ...menu(2, '配置', '/zsjos/config'), parentId: 1 }]
    const navigation = [{
      key: '1', label: '工作台', icon: 'mdi:home', menu: root, pages: []
    }] as unknown as PrimaryNavigationItem[]

    expect(getNavigationOpenKeys(navigation, '/does/not/exist')).toEqual([])
    expect(getNavigationOpenKeys(navigation, '/zsjos')).toEqual([])
  })
})
