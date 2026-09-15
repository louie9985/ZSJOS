type AdminMenuRoute = {
  component?: string
  children?: AdminMenuRoute[]
  workbenchRenderMode?: string
}

/**
 * 过滤仅由 React Workbench 渲染、Vue Admin 无法加载的服务端菜单。
 *
 * `zsjos-workbench` 是 React 侧本地组件注册表的标记，Vue Admin 永远解析不出组件。
 * 不按 `workbenchRenderMode` 区分：`native` 与 `admin_only` 都只说明该菜单归 Workbench 所有，
 * 与 Vue 能否加载无关。带子节点的父菜单被无条件移除，否则它会退化成只有 RouterView 的空壳。
 */
export const filterAdminRoutes = <T extends AdminMenuRoute>(routes: T[]): T[] =>
  routes.flatMap((route) => {
    if (route.component === 'zsjos-workbench') {
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
