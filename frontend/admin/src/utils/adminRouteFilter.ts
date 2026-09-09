type AdminMenuRoute = {
  component?: string
  children?: AdminMenuRoute[]
  workbenchRenderMode?: string
}

/** 过滤仅由 React Workbench 原生渲染、Vue Admin 无法加载的服务端菜单。 */
export const filterAdminRoutes = <T extends AdminMenuRoute>(routes: T[]): T[] =>
  routes.flatMap((route) => {
    if (route.workbenchRenderMode === 'native' && route.component === 'zsjos-workbench') {
      return []
    }
    if (!route.children) {
      return [route]
    }
    const children = filterAdminRoutes(route.children)
    if (children.length === 0 && !route.component) {
      return []
    }
    return [{ ...route, children } as T]
  })
