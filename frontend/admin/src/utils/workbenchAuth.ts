// The platform is fixed for this document; route changes must not switch token families.
export function resolveWorkbenchAuthPlatform(search: string, parentPath?: string): 'PC' | 'MOBILE' {
  const params = new URLSearchParams(search)
  return (params.get('embed') === 'workbench' && params.get('platform') === 'MOBILE') ||
    parentPath === '/zsjos/mobile' || parentPath?.startsWith('/zsjos/mobile/')
    ? 'MOBILE' : 'PC'
}

function parentWorkbenchPath() {
  try {
    if (window.parent !== window && window.parent.location.origin === window.location.origin) {
      return window.parent.location.pathname
    }
  } catch { /* Cross-origin parents cannot supply the Workbench authentication context. */ }
  return undefined
}

export const workbenchAuthPlatform = resolveWorkbenchAuthPlatform(window.location.search, parentWorkbenchPath())
export const isMobileWorkbench = workbenchAuthPlatform === 'MOBILE'

export function returnToMobileWorkbench() {
  if (!isMobileWorkbench) return
  const parentPath = parentWorkbenchPath()
  if (parentPath === '/zsjos/mobile' || parentPath?.startsWith('/zsjos/mobile/')) window.parent.location.reload()
  else window.location.assign('/zsjos/mobile/')
}
