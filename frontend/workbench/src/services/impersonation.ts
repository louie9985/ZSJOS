import { STORAGE_KEYS } from '../constants'
import type { Timestamp } from './time'

export const IMPERSONATION_CHANGE_EVENT = 'zsjos-impersonation-change'
export const IMPERSONATION_SESSION_INVALID_CODE = 1_900_007_002

export type ImpersonationSession = {
  id: number
  administratorUserId: number
  administratorNameSnapshot: string
  targetUserId: number
  targetNameSnapshot: string
  reason: string
  status: 'active' | 'ended' | 'expired'
  startedAt: Timestamp
  lastActiveAt: Timestamp
}

const parseActiveSession = (stored: string | null): ImpersonationSession | undefined => {
  if (!stored) return undefined
  try {
    const session = JSON.parse(stored) as Partial<ImpersonationSession>
    return Number.isSafeInteger(session.id) && Number(session.id) > 0
      && Number.isSafeInteger(session.administratorUserId) && Number(session.administratorUserId) > 0
      && Number.isSafeInteger(session.targetUserId) && Number(session.targetUserId) > 0
      && typeof session.administratorNameSnapshot === 'string'
      && typeof session.targetNameSnapshot === 'string'
      && typeof session.reason === 'string'
      && typeof session.startedAt === 'string'
      && typeof session.lastActiveAt === 'string'
      && session.status === 'active'
      ? session as ImpersonationSession
      : undefined
  } catch {
    return undefined
  }
}

const notifyChange = () => window.dispatchEvent(new Event(IMPERSONATION_CHANGE_EVENT))

export const clearStoredImpersonation = (storage: Storage = localStorage, notify: () => void = notifyChange) => {
  if (storage.getItem(STORAGE_KEYS.IMPERSONATION) === null) return false
  storage.removeItem(STORAGE_KEYS.IMPERSONATION)
  notify()
  return true
}

export const getStoredImpersonation = (storage: Storage = localStorage, notify: () => void = notifyChange) => {
  const stored = storage.getItem(STORAGE_KEYS.IMPERSONATION)
  const session = parseActiveSession(stored)
  if (stored && !session) clearStoredImpersonation(storage, notify)
  return session
}

export const storeImpersonation = (session?: ImpersonationSession) => {
  if (!session) {
    clearStoredImpersonation()
    return undefined
  }
  const activeSession = parseActiveSession(JSON.stringify(session))
  if (!activeSession) {
    clearStoredImpersonation()
    return undefined
  }
  localStorage.setItem(STORAGE_KEYS.IMPERSONATION, JSON.stringify(activeSession))
  notifyChange()
  return activeSession
}

export const resolveImpersonationSessionHeader = (url: string | undefined, stored: string | null, expectedOrigin?: string) => {
  if (!url) return undefined
  let pathname = url
  if (/^[a-z][a-z\d+.-]*:\/\//i.test(url)) {
    const parsed = new URL(url)
    if (!expectedOrigin || parsed.origin !== expectedOrigin) return undefined
    pathname = parsed.pathname
  }
  if (!pathname.includes('/zsjos/') || pathname.includes('/zsjos/impersonation/')) return undefined
  return parseActiveSession(stored)?.id
}

export const handleImpersonationInvalid = (code: number, expectedSessionId?: number, storage?: Storage, notify: () => void = notifyChange) => {
  if (code !== IMPERSONATION_SESSION_INVALID_CODE) return false
  const targetStorage = storage || localStorage
  if (expectedSessionId != null && parseActiveSession(targetStorage.getItem(STORAGE_KEYS.IMPERSONATION))?.id !== expectedSessionId) return false
  clearStoredImpersonation(targetStorage, notify)
  return true
}
