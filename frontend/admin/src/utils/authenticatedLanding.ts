import { isUrl } from './is.ts'

const DEFAULT_LANDING_PATH = '/zsjos/tasks/today'

export interface LandingMenu {
  path: string
  visible?: boolean
  children?: LandingMenu[]
}

const isDynamicPath = (path: string) => /(^|\/)\:[^/]+|\([^)]*\)/.test(path)

const resolvePath = (parentPath: string, childPath: string) => {
  if (!childPath) return parentPath || '/'
  if (isUrl(childPath)) return childPath
  const joined = childPath.startsWith('/') ? childPath : `${parentPath}/${childPath}`
  return `/${joined.replace(/^\/+/, '').replace(/\/{2,}/g, '/')}`
}

const collectLeafPaths = (menus: LandingMenu[], parentPath = ''): string[] => {
  const paths: string[] = []
  for (const menu of menus) {
    if (menu.visible !== true) continue
    const fullPath = resolvePath(parentPath, menu.path)
    if (menu.children?.length) {
      paths.push(...collectLeafPaths(menu.children, fullPath))
      continue
    }
    if (fullPath !== '/' && !isUrl(fullPath) && !isDynamicPath(fullPath)) {
      paths.push(fullPath)
    }
  }
  return paths
}

export const getAuthenticatedLandingPath = (
  menus: LandingMenu[],
  preferredPath = DEFAULT_LANDING_PATH
): string => {
  const leafPaths = collectLeafPaths(menus)
  return leafPaths.includes(preferredPath) ? preferredPath : leafPaths[0] || '/403'
}

export interface AuthenticatedRouteTargetOptions {
  currentPath: string
  currentFullPath: string
  explicitRedirect?: string
  defaultLandingPath: string
}

export const resolveAuthenticatedRouteTarget = ({
  currentPath,
  currentFullPath,
  explicitRedirect,
  defaultLandingPath
}: AuthenticatedRouteTargetOptions): string => {
  if (explicitRedirect && !['/', '/index'].includes(explicitRedirect)) {
    return explicitRedirect
  }
  if (currentPath === '/' || currentPath === '/index') {
    return defaultLandingPath
  }
  return currentFullPath
}

export interface AuthenticatedRouteNavigationOptions extends AuthenticatedRouteTargetOptions {
  routesJustAdded: boolean
}

export const resolveAuthenticatedRouteNavigation = (
  options: AuthenticatedRouteNavigationOptions
): string | undefined => {
  const target = resolveAuthenticatedRouteTarget(options)
  return options.routesJustAdded || options.currentFullPath !== target ? target : undefined
}

export { DEFAULT_LANDING_PATH }
