export const IMPERSONATION_STORAGE_KEY = 'zsjos.impersonation.session'
export const IMPERSONATION_CHANGE_EVENT = 'zsjos-impersonation-change'
export const IMPERSONATION_SESSION_INVALID_CODE = 1_900_007_002

export interface StoredImpersonationSession {
  id: number
  targetNameSnapshot: string
  startedAt: string
  reason: string
  status: 'active' | 'ended' | 'expired'
}

const notifyChange = () => window.dispatchEvent(new Event(IMPERSONATION_CHANGE_EVENT))

export const clearStoredImpersonation = () => {
  if (sessionStorage.getItem(IMPERSONATION_STORAGE_KEY) === null) return false
  sessionStorage.removeItem(IMPERSONATION_STORAGE_KEY)
  notifyChange()
  return true
}

export const getStoredImpersonation = <T extends StoredImpersonationSession>(): T | undefined => {
  const value = sessionStorage.getItem(IMPERSONATION_STORAGE_KEY)
  if (!value) return undefined
  try {
    const session = JSON.parse(value) as Partial<T>
    if (
      !Number.isSafeInteger(session.id) ||
      Number(session.id) <= 0 ||
      session.status !== 'active'
    ) {
      clearStoredImpersonation()
      return undefined
    }
    return session as T
  } catch {
    clearStoredImpersonation()
    return undefined
  }
}

export const storeImpersonation = (session?: StoredImpersonationSession) => {
  if (!session) {
    clearStoredImpersonation()
    return
  }
  sessionStorage.setItem(IMPERSONATION_STORAGE_KEY, JSON.stringify(session))
  notifyChange()
}

export const handleImpersonationInvalid = (code: number) => {
  if (code !== IMPERSONATION_SESSION_INVALID_CODE) return false
  clearStoredImpersonation()
  return true
}
