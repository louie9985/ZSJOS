import type { WorkbenchMenu } from './services/api'
import { findAdminEmbedPath } from './services/menu'

/** Only the authorized menu tree owns cache settings; navigation projections do not. */
export function getRetainedPageMenus(menus: WorkbenchMenu[], renderablePaths: ReadonlySet<string>): WorkbenchMenu[] {
  const retained = new Map<string, WorkbenchMenu>()
  const visit = (nodes: WorkbenchMenu[]) => {
    for (const menu of nodes) {
      if (menu.noCache === false && menu.type !== 1 && menu.type !== 3
        && renderablePaths.has(menu.path)
        && menu.workbenchRenderMode !== 'admin_only' && menu.workbenchRenderMode !== 'admin_embed'
        && !findAdminEmbedPath(menus, menu.path)) retained.set(menu.path, menu)
      visit(menu.children)
    }
  }
  visit(menus)
  return [...retained.values()]
}
