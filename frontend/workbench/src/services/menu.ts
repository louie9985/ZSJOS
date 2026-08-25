import type { WorkbenchMenu } from './api'
import { APP_ROUTES } from '../constants'

export type SecondaryNavigationItem = {
  key: string
  label: string
  icon?: string
  menu: WorkbenchMenu
  children: SecondaryNavigationItem[]
}

export type PrimaryNavigationItem = {
  key: string
  label: string
  icon?: string
  menu: WorkbenchMenu
  pages: SecondaryNavigationItem[]
}

const isExternalPath = (path: string) => /^https?:\/\//i.test(path)

export function filterRenderableMenus(menus: WorkbenchMenu[], renderablePaths: ReadonlySet<string>): WorkbenchMenu[] {
  return menus.flatMap(menu => {
    const children = filterRenderableMenus(menu.children, renderablePaths)
    if (children.length === 0 && !renderablePaths.has(menu.path)) return []
    return [{ ...menu, children }]
  })
}

export function findMenuByPath(menus: WorkbenchMenu[], path: string): WorkbenchMenu | undefined {
  for (const menu of menus) {
    if (menu.path === path) return menu
    const child = findMenuByPath(menu.children, path)
    if (child) return child
  }
}

function buildNavigationNodes(menus: WorkbenchMenu[]): SecondaryNavigationItem[] {
  return menus
    .filter(menu => !menu.hidden)
    .map(menu => {
      const children = buildNavigationNodes(menu.children)
      return {
        key: children.length > 0 ? String(menu.id) : menu.path,
        label: menu.name,
        icon: menu.icon,
        menu,
        children
      }
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
      pages: buildNavigationNodes(menu.children)
    }))
}

function findNavigationNodeByPath(items: SecondaryNavigationItem[], path: string): SecondaryNavigationItem | undefined {
  for (const item of items) {
    if (item.menu.path === path) return item
    const child = findNavigationNodeByPath(item.children, path)
    if (child) return child
  }
}

export function findPrimaryByPath(items: PrimaryNavigationItem[], path: string) {
  return items.find(item => item.menu.path === path || findNavigationNodeByPath(item.pages, path))
}

export function findPageByPath(items: PrimaryNavigationItem[], path: string) {
  for (const item of items) {
    if (item.menu.path === path && item.pages.length === 0) return item.menu
    const page = findNavigationNodeByPath(item.pages, path)
    if (page) return page.menu
  }
}

export function getPrimaryTarget(item: PrimaryNavigationItem, includeExternal = true) {
  const findFirstTarget = (nodes: SecondaryNavigationItem[], allowExternal: boolean): string | undefined => {
    for (const node of nodes) {
      const childTarget = findFirstTarget(node.children, allowExternal)
      if (childTarget) return childTarget
      if (node.children.length === 0 && (allowExternal || !isExternalPath(node.menu.path))) return node.menu.path
    }
  }
  const pageTarget = findFirstTarget(item.pages, false)
  if (pageTarget) return pageTarget
  if (includeExternal) {
    const externalTarget = findFirstTarget(item.pages, true)
    if (externalTarget) return externalTarget
  }
  if (item.pages.length > 0) return
  if (!isExternalPath(item.menu.path) || includeExternal) return item.menu.path
}

export function getNavigationOpenKeys(items: PrimaryNavigationItem[], path: string): string[] {
  const findKeys = (nodes: SecondaryNavigationItem[]): string[] | undefined => {
    for (const node of nodes) {
      if (node.menu.path === path) return []
      const childKeys = findKeys(node.children)
      if (childKeys) return node.children.length > 0 ? [node.key, ...childKeys] : childKeys
    }
  }
  for (const item of items) {
    if (item.menu.path === path) return []
    const childKeys = findKeys(item.pages)
    if (childKeys) return item.pages.length > 0 ? [item.key, ...childKeys] : childKeys
  }
  return []
}

export function getInitialTarget(items: PrimaryNavigationItem[]) {
  for (const item of items) {
    const target = getPrimaryTarget(item, false)
    if (target) return target
  }
}

export function getInaccessiblePathFallback(items: PrimaryNavigationItem[], path: string, authorizedMenus?: WorkbenchMenu[]) {
  if (path === '/' || path === APP_ROUTES.USER_PROFILE || findPageByPath(items, path)
    || (authorizedMenus && findMenuByPath(authorizedMenus, path))) return
  return getInitialTarget(items)
}

const LEAD_DETAIL_ENTRY_PERMISSIONS = new Set([
  'zsjos:lead:query',
  'zsjos:subordinate-sales:query',
  'zsjos:student:query-my',
  'zsjos:sales-order:query',
  'zsjos:sales-order:review',
  'zsjos:lead-detail:follow-up-read',
  'zsjos:lead-detail:appeal-read',
  'zsjos:lead-detail:complaint-read',
  'zsjos:lead-detail:order-read'
])

export function canOpenLeadDetailDeepLink(path: string, search: string, permissions: string[]): boolean {
  if (path !== APP_ROUTES.LEAD_MANAGEMENT
      || !Number(new URLSearchParams(search).get('leadId'))) return false
  return permissions.some(permission => LEAD_DETAIL_ENTRY_PERMISSIONS.has(permission))
}
