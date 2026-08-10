import type { WorkbenchMenu } from './api'

export type SecondaryNavigationItem = {
  key: string
  label: string
  icon?: string
  menu: WorkbenchMenu
}

export type PrimaryNavigationItem = {
  key: string
  label: string
  icon?: string
  menu: WorkbenchMenu
  pages: SecondaryNavigationItem[]
}

const isExternalPath = (path: string) => /^https?:\/\//i.test(path)

function collectVisibleLeaves(menus: WorkbenchMenu[]): SecondaryNavigationItem[] {
  return menus.flatMap(menu => {
    if (menu.hidden) return []
    const visibleChildren = menu.children.filter(child => !child.hidden)
    if (visibleChildren.length === 0) {
      return [{ key: menu.path, label: menu.name, icon: menu.icon, menu }]
    }
    return collectVisibleLeaves(visibleChildren)
  })
}

export function buildTwoLevelNavigation(menus: WorkbenchMenu[]): PrimaryNavigationItem[] {
  return menus
    .filter(menu => !menu.hidden)
    .map(menu => ({
      key: String(menu.id),
      label: menu.name,
      icon: menu.icon,
      menu,
      pages: collectVisibleLeaves(menu.children.filter(child => !child.hidden))
    }))
}

export function findPrimaryByPath(items: PrimaryNavigationItem[], path: string) {
  return items.find(item => item.menu.path === path || item.pages.some(page => page.key === path))
}

export function findPageByPath(items: PrimaryNavigationItem[], path: string) {
  for (const item of items) {
    if (item.menu.path === path && item.pages.length === 0) return item.menu
    const page = item.pages.find(candidate => candidate.key === path)
    if (page) return page.menu
  }
}

export function getPrimaryTarget(item: PrimaryNavigationItem, includeExternal = true) {
  const internalPage = item.pages.find(page => !isExternalPath(page.key))
  if (internalPage) return internalPage.key
  if (item.pages.length > 0) return includeExternal ? item.pages[0].key : undefined
  if (!isExternalPath(item.menu.path) || includeExternal) return item.menu.path
}

export function getInitialTarget(items: PrimaryNavigationItem[]) {
  for (const item of items) {
    const target = getPrimaryTarget(item, false)
    if (target) return target
  }
}
