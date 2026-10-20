import { isAxiosError } from 'axios'
import { AUTH_EXPIRED_EVENT, http, readSharedTenantId } from './api'
import { getAuthAccessToken, getAuthPlatform } from './authSession'
import { getStoredImpersonation, IMPERSONATION_CHANGE_EVENT } from './impersonation'

export function performanceSessionIdentity() {
  const platform = getAuthPlatform(), impersonation = getStoredImpersonation(localStorage, () => {})
  // Opaque authentication identity stays in memory; never logged or persisted as a cache key.
  return JSON.stringify([platform, getAuthAccessToken(platform), readSharedTenantId(), impersonation?.id, impersonation?.targetUserId])
}
export function subscribePerformanceSession(listener: () => void) {
  for (const event of ['storage', 'focus', AUTH_EXPIRED_EVENT, IMPERSONATION_CHANGE_EVENT]) window.addEventListener(event, listener)
  const interceptor = http.interceptors.response.use(response => { listener(); return response }, error => { listener(); return Promise.reject(error) })
  return () => {
    for (const event of ['storage', 'focus', AUTH_EXPIRED_EVENT, IMPERSONATION_CHANGE_EVENT]) window.removeEventListener(event, listener)
    http.interceptors.response.eject(interceptor)
  }
}

export function isPerformanceAccessDenied(error: unknown) {
  if (isAxiosError(error) && [401, 403].includes(error.response?.status ?? 0)) return true
  return error !== null && typeof error === 'object' && 'code' in error && [401, 403, 1_900_090_001].includes(Number(error.code))
}
