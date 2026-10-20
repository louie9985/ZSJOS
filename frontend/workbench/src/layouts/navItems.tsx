import type { ReactNode } from 'react'
import type { MenuProps } from 'antd'
import type { PrimaryNavigationItem, SecondaryNavigationItem } from '../services/menu'
import type { WorkbenchMenu } from '../services/api'
import BackendMenuIcon from './BackendMenuIcon'
import { Badge } from 'antd'
import type { MenuTaskBadge, MenuTaskBadgeResolver } from '../services/menuTaskBadge'

type MenuItem = Required<MenuProps>['items'][number]

export function withMenuLabelBadge(label: string, badge: MenuTaskBadge | undefined) {
  return badge && badge.count > 0
    ? <span className="menu-task-label"><span className="menu-task-text">{label}</span><Badge className="menu-task-badge" count={badge.count} overflowCount={99}/></span>
    : label
}

/**
 * 服务端递归导航 → antd Menu items。
 *
 * single-sider / mini-float / top-only / 移动端抽屉四处共用：它们都需要把服务端
 * 递归导航压进一棵 Menu 树，规则一致。
 *
 * 关键：没有可见子节点的菜单必须渲染成叶子项，不能是 SubMenu。
 * 一级节点在后台菜单里既可能是分组容器，也可能自身就是页面（此时 children 为空）。
 * 渲染成 SubMenu 会得到一个空弹层，且 Menu 的 onClick 只对叶子项触发，
 * 该一级页面在这些模式下完全点不到。key 用 `menu.path`，与 selectedKeys
 * （取 currentMenu.path）对齐，也与 findPageByPath 对一级页面的判定一致。
 */
export function buildNavMenuItems(
  navigation: PrimaryNavigationItem[],
  options: {
    childIcons?: boolean
    expandSuffix?: ReactNode
    /** 把一级名作为分组头包进二级列表。mini-float 的浮层用：否则浮层里只有页面名。 */
    groupChildren?: boolean
    /** 透传给 SubMenu 弹层的 class，供浮层单独取样式 */
    popupClassName?: string
    badgeResolver?: MenuTaskBadgeResolver
  } = {}
): MenuItem[] {
  const { childIcons = true, expandSuffix, groupChildren = false, popupClassName, badgeResolver } = options
  const buildChildren = (nodes: SecondaryNavigationItem[]): MenuItem[] => nodes.map(node => {
    const badge = badgeResolver?.(node.menu.path)
    const icon = childIcons ? <BackendMenuIcon icon={node.icon}/> : undefined
    if (node.children.length === 0) {
      return { key: node.menu.path, label: withMenuLabelBadge(node.label, badge), title: node.label, ...(icon ? { icon } : {}) }
    }
    const childCount = node.children.reduce((sum, child) => sum + (badgeResolver?.(child.menu.path)?.count ?? 0), 0)
    const nodeBadge = badge ?? (childCount > 0 ? { count: childCount } : undefined)
    return {
      key: node.key,
      label: withMenuLabelBadge(node.label, nodeBadge),
      title: node.label,
      ...(childIcons ? { icon: <BackendMenuIcon icon={node.icon}/> } : {}),
      ...(popupClassName ? { popupClassName } : {}),
      children: buildChildren(node.children)
    }
  })

  return navigation.map(primary => {
    const primaryBadge = badgeResolver?.(primary.menu.path)
    // 自身即页面：叶子项，key 用 path
    if (primary.pages.length === 0) {
      return {
        key: primary.menu.path,
        label: withMenuLabelBadge(primary.label, primaryBadge),
        title: primary.label,
        icon: <BackendMenuIcon icon={primary.icon}/>
      }
    }
    const pages = buildChildren(primary.pages)
    const primaryCount = primary.pages.reduce((sum, page) => sum + (badgeResolver?.(page.menu.path)?.count ?? 0), 0)
    const badge = primaryBadge ?? (primaryCount > 0 ? { count: primaryCount } : undefined)
    const children = groupChildren
      ? [{ type: 'group' as const, key: `${primary.key}-group`, label: primary.label, children: pages }]
      : pages
    return {
      key: primary.key,
      // 展开标记只加在真有二级的项上
      label: expandSuffix
        ? <span>{withMenuLabelBadge(primary.label, badge)}{expandSuffix}</span>
        : withMenuLabelBadge(primary.label, badge),
      title: primary.label,
      icon: <BackendMenuIcon icon={primary.icon}/>,
      ...(popupClassName ? { popupClassName } : {}),
      children
    }
  })
}

type HierarchicalMenuOptions = {
  childIcons?: boolean
  expandSuffix?: ReactNode
  popupClassName?: string
  badgeResolver?: MenuTaskBadgeResolver
}

function buildHierarchicalItems(menus: WorkbenchMenu[], options: HierarchicalMenuOptions): MenuItem[] {
  const { childIcons = true, expandSuffix, popupClassName, badgeResolver } = options
  return menus.filter(menu => !menu.hidden).map(menu => {
    const children = buildHierarchicalItems(menu.children, options)
    const badge = badgeResolver?.(menu.path)
    if (children.length === 0) {
      return {
        key: menu.path,
        label: withMenuLabelBadge(menu.name, badge),
        title: menu.name,
        ...(childIcons ? { icon: <BackendMenuIcon icon={menu.icon}/> } : {})
      }
    }
    const childCount = menu.children.reduce((sum, child) => sum + (badgeResolver?.(child.path)?.count ?? 0), 0)
    const menuBadge = badge ?? (childCount > 0 ? { count: childCount } : undefined)
    return {
      key: menu.path,
      label: expandSuffix ? <span>{withMenuLabelBadge(menu.name, menuBadge)}{expandSuffix}</span> : withMenuLabelBadge(menu.name, menuBadge),
      title: menu.name,
      ...(childIcons ? { icon: <BackendMenuIcon icon={menu.icon}/> } : {}),
      ...(popupClassName ? { popupClassName } : {}),
      children
    }
  })
}

/** 保留服务端目录层级，用于侧栏、顶栏和移动端的下拉菜单。 */
export function buildHierarchicalNavMenuItems(
  navigation: PrimaryNavigationItem[],
  options: HierarchicalMenuOptions = {}
): MenuItem[] {
  return navigation.map(primary => {
    const children = buildHierarchicalItems(primary.menu.children, options)
    const badge = options.badgeResolver?.(primary.menu.path)
    if (children.length === 0) {
      return { key: primary.menu.path, label: withMenuLabelBadge(primary.label, badge), title: primary.label, icon: <BackendMenuIcon icon={primary.icon}/> }
    }
    return {
      key: primary.key,
      label: options.expandSuffix ? <span>{withMenuLabelBadge(primary.label, badge)}{options.expandSuffix}</span> : withMenuLabelBadge(primary.label, badge),
      title: primary.label,
      icon: <BackendMenuIcon icon={primary.icon}/>,
      ...(options.popupClassName ? { popupClassName: options.popupClassName } : {}),
      children
    }
  })
}

/** 把某个一级菜单的子树转换为带下拉目录的二级菜单项。 */
export function buildHierarchicalSecondaryItems(
  menu: WorkbenchMenu | undefined,
  options: HierarchicalMenuOptions = {}
): MenuItem[] {
  return menu ? buildHierarchicalItems(menu.children, options) : []
}
