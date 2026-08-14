export const IMPERSONATION_STORAGE_KEY = 'zsjos.impersonation.session'

export interface StoredImpersonationSession {
  id: number
  targetNameSnapshot: string
  startedAt: string
  reason: string
}

export const getStoredImpersonation = <T extends StoredImpersonationSession>(): T | undefined => {
  const value = sessionStorage.getItem(IMPERSONATION_STORAGE_KEY)
  if (!value) return undefined
  try {
    return JSON.parse(value) as T
  } catch {
    sessionStorage.removeItem(IMPERSONATION_STORAGE_KEY)
    return undefined
  }
}

export const storeImpersonation = (session?: StoredImpersonationSession) => {
  if (!session) sessionStorage.removeItem(IMPERSONATION_STORAGE_KEY)
  else sessionStorage.setItem(IMPERSONATION_STORAGE_KEY, JSON.stringify(session))
}
