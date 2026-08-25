import type { ReactNode } from 'react'
import type { MenuProps } from 'antd'
import type { PrimaryNavigationItem, SecondaryNavigationItem } from '../services/menu'
import type { WorkbenchMenu } from '../services/api'
import BackendMenuIcon from './BackendMenuIcon'

type MenuItem = Required<MenuProps>['items'][number]

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
  } = {}
): MenuItem[] {
  const { childIcons = true, expandSuffix, groupChildren = false, popupClassName } = options
  const buildChildren = (nodes: SecondaryNavigationItem[]): MenuItem[] => nodes.map(node => {
    const icon = childIcons ? <BackendMenuIcon icon={node.icon}/> : undefined
    if (node.children.length === 0) {
      return { key: node.menu.path, label: node.label, title: node.label, ...(icon ? { icon } : {}) }
    }
    return {
      key: node.key,
      label: node.label,
      title: node.label,
      ...(icon ? { icon } : {}),
      ...(popupClassName ? { popupClassName } : {}),
      children: buildChildren(node.children)
    }
  })

  return navigation.map(primary => {
    const icon = <BackendMenuIcon icon={primary.icon}/>
    // 自身即页面：叶子项，key 用 path
    if (primary.pages.length === 0) {
      return { key: primary.menu.path, label: primary.label, title: primary.label, icon }
    }
    const pages = buildChildren(primary.pages)
    const children = groupChildren
      ? [{ type: 'group' as const, key: `${primary.key}-group`, label: primary.label, children: pages }]
      : pages
    return {
      key: primary.key,
      // 展开标记只加在真有二级的项上
      label: expandSuffix ? <span>{primary.label}{expandSuffix}</span> : primary.label,
      title: primary.label,
      icon,
      ...(popupClassName ? { popupClassName } : {}),
      children
    }
  })
}

type HierarchicalMenuOptions = {
  childIcons?: boolean
  expandSuffix?: ReactNode
  popupClassName?: string
}

function buildHierarchicalItems(menus: WorkbenchMenu[], options: HierarchicalMenuOptions): MenuItem[] {
  const { childIcons = true, expandSuffix, popupClassName } = options
  return menus.filter(menu => !menu.hidden).map(menu => {
    const children = buildHierarchicalItems(menu.children, options)
    const icon = childIcons ? <BackendMenuIcon icon={menu.icon}/> : undefined
    if (children.length === 0) {
      return { key: menu.path, label: menu.name, title: menu.name, ...(icon ? { icon } : {}) }
    }
    return {
      key: menu.path,
      label: expandSuffix ? <span>{menu.name}{expandSuffix}</span> : menu.name,
      title: menu.name,
      ...(icon ? { icon } : {}),
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
    const icon = <BackendMenuIcon icon={primary.icon}/>
    if (children.length === 0) {
      return { key: primary.menu.path, label: primary.label, title: primary.label, icon }
    }
    return {
      key: primary.key,
      label: options.expandSuffix ? <span>{primary.label}{options.expandSuffix}</span> : primary.label,
      title: primary.label,
      icon,
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
