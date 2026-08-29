import {
  AUTH_CLIENT_IDS,
  AUTH_PLATFORM_SESSION_KEY,
  AUTH_STORAGE_KEYS,
  STORAGE_KEYS,
  type AuthPlatform,
} from '../constants'

type ReadableStorage = Pick<Storage, 'getItem'>
type WritableStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

const LEGACY_AUTH_KEYS = {
  accessToken: 'zsjos_access_token',
  refreshToken: 'zsjos_refresh_token',
  clientId: 'zsjos_client_id',
  expiresTime: 'zsjos_expires_time',
} as const

type AuthKeyFamily = (typeof AUTH_STORAGE_KEYS)[AuthPlatform] | typeof LEGACY_AUTH_KEYS

type StoredAuthSession = {
  accessToken: string
  refreshToken: string
  clientId?: string
  expiresTime?: string
}

let initializedPlatform: AuthPlatform | undefined

export function isMobileEntryPath(pathname: string) {
  return /(?:^|\/)zsjos\/mobile(?:\/|$)/i.test(pathname)
}

export function initializeAuthPlatform(
  pathname = window.location.pathname,
  storage: Pick<Storage, 'getItem' | 'setItem'> = sessionStorage,
) {
  if (initializedPlatform) return initializedPlatform
  if (isMobileEntryPath(pathname)) {
    storage.setItem(AUTH_PLATFORM_SESSION_KEY, 'MOBILE')
    initializedPlatform = 'MOBILE'
    return initializedPlatform
  }
  initializedPlatform = storage.getItem(AUTH_PLATFORM_SESSION_KEY) === 'MOBILE' ? 'MOBILE' : 'PC'
  storage.setItem(AUTH_PLATFORM_SESSION_KEY, initializedPlatform)
  return initializedPlatform
}

export function getAuthPlatform() {
  if (!initializedPlatform) return initializeAuthPlatform()
  return initializedPlatform
}

export function resetAuthPlatformForTest() {
  initializedPlatform = undefined
}

export function shouldReloadForMobileEntry(platform: AuthPlatform, pathname: string) {
  return platform === 'PC' && isMobileEntryPath(pathname)
}

export function redirectToMobileEntryForPlatformReload(
  pathname: string,
  storage: Pick<Storage, 'setItem'> = sessionStorage,
  locationLike: Pick<Location, 'replace'> = window.location,
) {
  storage.setItem(AUTH_PLATFORM_SESSION_KEY, 'MOBILE')
  locationLike.replace(pathname)
}

export const getAuthStorageKeys = (platform: AuthPlatform) => AUTH_STORAGE_KEYS[platform]

export const getAuthAccessToken = (
  platform: AuthPlatform,
  storage: ReadableStorage = localStorage,
) => storage.getItem(getAuthStorageKeys(platform).accessToken)

export const clearAuthStorage = (
  platform: AuthPlatform,
  storage: Pick<Storage, 'removeItem'> = localStorage,
) => {
  const keys = getAuthStorageKeys(platform)
  storage.removeItem(keys.accessToken)
  storage.removeItem(keys.refreshToken)
  storage.removeItem(keys.clientId)
  storage.removeItem(keys.expiresTime)
  if (platform === 'PC') {
    storage.removeItem(LEGACY_AUTH_KEYS.accessToken)
    storage.removeItem(LEGACY_AUTH_KEYS.refreshToken)
    storage.removeItem(LEGACY_AUTH_KEYS.clientId)
    storage.removeItem(LEGACY_AUTH_KEYS.expiresTime)
    storage.removeItem(STORAGE_KEYS.IMPERSONATION)
  }
}

function readFamily(storage: ReadableStorage, keys: AuthKeyFamily): Partial<StoredAuthSession> {
  return {
    accessToken: storage.getItem(keys.accessToken) || undefined,
    refreshToken: storage.getItem(keys.refreshToken) || undefined,
    clientId: storage.getItem(keys.clientId) || undefined,
    expiresTime: storage.getItem(keys.expiresTime) || undefined,
  }
}

function hasAnySessionField(session: Partial<StoredAuthSession>) {
  return Boolean(session.accessToken || session.refreshToken || session.clientId || session.expiresTime)
}

function classifyCompleteSession(session: Partial<StoredAuthSession>): AuthPlatform | undefined {
  if (!session.accessToken || !session.refreshToken) return undefined
  if (!session.clientId || session.clientId === AUTH_CLIENT_IDS.PC) return 'PC'
  if (session.clientId === AUTH_CLIENT_IDS.MOBILE) return 'MOBILE'
  return undefined
}

function isValidCompleteSession(session: Partial<StoredAuthSession>) {
  return Boolean(classifyCompleteSession(session))
}

function writeSession(storage: WritableStorage, platform: AuthPlatform, session: StoredAuthSession) {
  const keys = AUTH_STORAGE_KEYS[platform]
  storage.setItem(keys.accessToken, session.accessToken)
  storage.setItem(keys.refreshToken, session.refreshToken)
  storage.setItem(keys.clientId, AUTH_CLIENT_IDS[platform])
  if (session.expiresTime) storage.setItem(keys.expiresTime, session.expiresTime)
}

function clearFamily(storage: Pick<Storage, 'removeItem'>, keys: AuthKeyFamily) {
  storage.removeItem(keys.accessToken)
  storage.removeItem(keys.refreshToken)
  storage.removeItem(keys.clientId)
  storage.removeItem(keys.expiresTime)
}

export const migrateLegacyAuthStorage = (storage: WritableStorage = localStorage) => {
  const currentPc = readFamily(storage, AUTH_STORAGE_KEYS.PC)
  const legacy = readFamily(storage, LEGACY_AUTH_KEYS)
  const currentMobile = readFamily(storage, AUTH_STORAGE_KEYS.MOBILE)
  const currentPcPlatform = classifyCompleteSession(currentPc)
  const legacyPlatform = classifyCompleteSession(legacy)

  if (hasAnySessionField(currentMobile) && !isValidCompleteSession(currentMobile)) {
    clearFamily(storage, AUTH_STORAGE_KEYS.MOBILE)
  }

  if (hasAnySessionField(currentPc) && currentPcPlatform !== 'PC') {
    if (currentPcPlatform === 'MOBILE' && !hasAnySessionField(currentMobile)) {
      writeSession(storage, 'MOBILE', currentPc as StoredAuthSession)
    }
    clearFamily(storage, AUTH_STORAGE_KEYS.PC)
  } else if (currentPcPlatform === 'PC') {
    storage.setItem(AUTH_STORAGE_KEYS.PC.clientId, AUTH_CLIENT_IDS.PC)
  }

  if (!hasAnySessionField(legacy)) return
  if (legacyPlatform === 'MOBILE') {
    if (!hasAnySessionField(currentMobile)) writeSession(storage, 'MOBILE', legacy as StoredAuthSession)
    clearFamily(storage, LEGACY_AUTH_KEYS)
    return
  }
  if (legacyPlatform === 'PC') {
    if (!hasAnySessionField(readFamily(storage, AUTH_STORAGE_KEYS.PC))) {
      writeSession(storage, 'PC', legacy as StoredAuthSession)
    }
    clearFamily(storage, LEGACY_AUTH_KEYS)
    return
  }
  clearFamily(storage, LEGACY_AUTH_KEYS)
}

export function resolveAdminEmbedPresentation(
  platform: AuthPlatform,
  renderMode?: string,
): 'frame' | 'mobile-blocked' | 'routes' {
  if (renderMode !== 'admin_embed') return 'routes'
  return platform === 'PC' ? 'frame' : 'mobile-blocked'
}
