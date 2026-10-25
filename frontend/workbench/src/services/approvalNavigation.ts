import { APP_ROUTES } from '../constants'
import type { WorkbenchMenu } from './api'

// 专属业务入口承接的审批不计入通用审批中心。
export const BUSINESS_APPROVAL_PROCESS_KEYS = [
  'zsjos_sales_order_dual_approval',
  'zsjos_lead_appeal_review'
].join(',')

export function approvalOpenPaths(menus: WorkbenchMenu[], activePath: string): string[] {
  const ancestors = (nodes: WorkbenchMenu[], path: string): string[] => {
    for (const node of nodes.filter(menu => !menu.hidden)) {
      if (node.path === path) return [node.path]
      const child = ancestors(node.children, path)
      if (child.length) return [node.path, ...child]
    }
    return []
  }
  return [...new Set([
    ...ancestors(menus, APP_ROUTES.BPM_TODO).slice(0, -1),
    ...ancestors(menus, activePath).slice(0, -1)
  ])]
}
