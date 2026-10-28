import { APP_ROUTES } from '../constants'
import type { WorkbenchMenu } from './api'

// 订单保留专属审批入口；申诉同时出现在工作流程和申诉处理入口。
export const BUSINESS_APPROVAL_PROCESS_KEYS = [
  'zsjos_sales_order_dual_approval'
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
