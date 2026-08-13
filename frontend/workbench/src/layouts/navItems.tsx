import type { ReactNode } from 'react'
import type { MenuProps } from 'antd'
import type { PrimaryNavigationItem } from '../services/menu'
import BackendMenuIcon from './BackendMenuIcon'

type MenuItem = Required<MenuProps>['items'][number]

/**
 * 一级 + 二级导航 → antd Menu items。
 *
 * single-sider / mini-float / top-only / 移动端抽屉四处共用：它们都需要把两级
 * 导航压进一棵 Menu 树，规则一致。
 *
 * 关键：`pages` 为空的一级项必须渲染成**叶子项**，不能是 SubMenu。
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
  return navigation.map(primary => {
    const icon = <BackendMenuIcon icon={primary.icon}/>
    // 自身即页面：叶子项，key 用 path
    if (primary.pages.length === 0) {
      return { key: primary.menu.path, label: primary.label, title: primary.label, icon }
    }
    const pages = primary.pages.map(page => ({
      key: page.key,
      label: page.label,
      title: page.label,
      ...(childIcons ? { icon: <BackendMenuIcon icon={page.icon}/> } : {})
    }))
    return {
      key: primary.key,
      // 展开标记只加在真有二级的项上
      label: expandSuffix ? <span>{primary.label}{expandSuffix}</span> : primary.label,
      title: primary.label,
      icon,
      ...(popupClassName ? { popupClassName } : {}),
      children: groupChildren
        ? [{ type: 'group' as const, key: `${primary.key}-group`, label: primary.label, children: pages }]
        : pages
    }
  })
}
