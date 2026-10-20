export type NavigationOrder = { groups: { parentKey: string; keys: string[] }[] }
export type NavigationNode = { id?: number | string; sourceMenuId?: number; layoutKey?: string; name: string; children?: NavigationNode[]; visible?: boolean; hidden?: boolean }
export const navigationKey = (node: NavigationNode): string => node.layoutKey ? 'group:' + node.layoutKey : 'menu:' + (node.sourceMenuId ?? node.id)
export const navigationParentKey = (parent: string, node: NavigationNode): string => parent === 'root' ? navigationKey(node) : parent + '/' + navigationKey(node)
export function applyNavigationOrder<T extends NavigationNode>(nodes: T[], preference: NavigationOrder, parent = 'root'): T[] {
  const order = preference.groups.find(group => group.parentKey === parent)?.keys || []
  const ranks = new Map(order.map((key, index) => [key, index]))
  return nodes.map(node => ({ ...node, children: node.children ? applyNavigationOrder(node.children, preference, navigationParentKey(parent, node)) : undefined } as T))
    .sort((a, b) => (ranks.get(navigationKey(a)) ?? Number.MAX_SAFE_INTEGER) - (ranks.get(navigationKey(b)) ?? Number.MAX_SAFE_INTEGER))
}
export function visibleNavigation<T extends NavigationNode>(nodes: T[]): T[] {
  return nodes.filter(node => node.visible !== false && node.hidden !== true).map(node => ({ ...node, children: node.children ? visibleNavigation(node.children) : undefined } as T))
}
export function setSiblingOrder(preference: NavigationOrder, parentKey: string, keys: string[]): NavigationOrder {
  return { groups: [...preference.groups.filter(group => group.parentKey !== parentKey), { parentKey, keys }] }
}
export function findNavigationLevel<T extends NavigationNode>(nodes: T[], path: string[]): T[] {
  let current: NavigationNode[] = nodes
  for (const key of path) current = current.find(node => navigationKey(node) === key)?.children || []
  return current as T[]
}
export const NAVIGATION_QUERY = 'zsjos:navigation-preference:query'
export const NAVIGATION_UPDATE = 'zsjos:navigation-preference:update'

