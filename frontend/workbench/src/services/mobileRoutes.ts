import { MOBILE_ROUTE_BASE, type AuthPlatform } from '../constants'

export function isMobileRoute(pathname: string) {
  return pathname === MOBILE_ROUTE_BASE || pathname.startsWith(`${MOBILE_ROUTE_BASE}/`)
}

// Keep the complete server route under the namespace to avoid collisions between modules.
export function platformHref(path: string, platform: AuthPlatform) {
  if (platform !== 'MOBILE' || !path.startsWith('/') || path.startsWith('//')) return path
  const pathname = path.split(/[?#]/, 1)[0]
  if (isMobileRoute(pathname)) return path
  return `${MOBILE_ROUTE_BASE}${path}`
}

export function normalizeMobileStartup(platform: AuthPlatform, location: Pick<Location, 'pathname' | 'search' | 'hash'>,
  history: Pick<History, 'state' | 'replaceState'>) {
  if (platform !== 'MOBILE' || isMobileRoute(location.pathname)) return
  // Preserve old tabs' Mobile context and the full deep link before the router mounts.
  history.replaceState(history.state, '', platformHref(`${location.pathname}${location.search}${location.hash}`, platform))
}
