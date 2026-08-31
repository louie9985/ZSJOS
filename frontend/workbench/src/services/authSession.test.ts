import { beforeEach, describe, expect, it } from 'vitest'
import { AUTH_CLIENT_IDS, AUTH_PLATFORM_SESSION_KEY, AUTH_STORAGE_KEYS } from '../constants'
import { applyAuthorizationHeader, isCurrentRefreshSession, readSharedTenantId, resolveRequestAuthPlatform, writeSharedTenantId } from './api'
import {
  getAuthAccessToken,
  initializeAuthPlatform,
  migrateLegacyAuthStorage,
  redirectToMobileEntryForPlatformReload,
  resetAuthPlatformForTest,
  shouldReloadForMobileEntry,
} from './authSession'

function memoryStorage(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
    values
  }
}

describe('authentication platform startup context', () => {
  beforeEach(() => resetAuthPlatformForTest())

  it('pins a /zsjos/mobile entry to the Mobile session for later menu routes in the same tab', () => {
    const storage = memoryStorage()
    expect(initializeAuthPlatform('/zsjos/mobile', storage)).toBe('MOBILE')
    expect(initializeAuthPlatform('/zsjos/tasks/today', storage)).toBe('MOBILE')
    expect(storage.getItem(AUTH_PLATFORM_SESSION_KEY)).toBe('MOBILE')
  })

  it('uses PC for a regular Workbench/Admin tab without a Mobile entry marker', () => {
    const storage = memoryStorage()
    expect(initializeAuthPlatform('/zsjos/tasks/today', storage)).toBe('PC')
    expect(storage.getItem(AUTH_PLATFORM_SESSION_KEY)).toBe('PC')
  })

  it('keeps the initialized platform immutable for the current page lifecycle', () => {
    const storage = memoryStorage()
    expect(initializeAuthPlatform('/zsjos/tasks/today', storage)).toBe('PC')
    expect(initializeAuthPlatform('/zsjos/mobile', storage)).toBe('PC')
    expect(storage.getItem(AUTH_PLATFORM_SESSION_KEY)).toBe('PC')
    resetAuthPlatformForTest()
    expect(initializeAuthPlatform('/zsjos/tasks/today', storage)).toBe('PC')
  })

  it('requires a full reload when an already-running PC page navigates to the Mobile entry', () => {
    expect(shouldReloadForMobileEntry('PC', '/zsjos/mobile')).toBe(true)
    expect(shouldReloadForMobileEntry('PC', '/zsjos/tasks/today')).toBe(false)
    expect(shouldReloadForMobileEntry('MOBILE', '/zsjos/mobile')).toBe(false)
  })

  it('marks the tab Mobile before replacing the document for a platform reload', () => {
    const storage = memoryStorage()
    let replaced = ''
    redirectToMobileEntryForPlatformReload('/zsjos/mobile?from=pc', storage, { replace: (url) => { replaced = String(url) } })
    expect(storage.getItem(AUTH_PLATFORM_SESSION_KEY)).toBe('MOBILE')
    expect(replaced).toBe('/zsjos/mobile?from=pc')
  })

  it('reads independent access-token slots for PC/Admin and Mobile', () => {
    const storage = memoryStorage({
      [AUTH_STORAGE_KEYS.PC.accessToken]: 'pc-access',
      [AUTH_STORAGE_KEYS.MOBILE.accessToken]: 'mobile-access'
    })
    expect(getAuthAccessToken('PC', storage)).toBe('pc-access')
    expect(getAuthAccessToken('MOBILE', storage)).toBe('mobile-access')
  })
})

describe('legacy authentication migration', () => {
  it('moves a complete current PC-slot Mobile session without overwriting an existing Mobile session', () => {
    const storage = memoryStorage({
      [AUTH_STORAGE_KEYS.PC.accessToken]: 'legacy-mobile-access',
      [AUTH_STORAGE_KEYS.PC.refreshToken]: 'legacy-mobile-refresh',
      [AUTH_STORAGE_KEYS.PC.clientId]: AUTH_CLIENT_IDS.MOBILE,
      [AUTH_STORAGE_KEYS.MOBILE.accessToken]: 'current-mobile-access',
      [AUTH_STORAGE_KEYS.MOBILE.refreshToken]: 'current-mobile-refresh',
      [AUTH_STORAGE_KEYS.MOBILE.clientId]: AUTH_CLIENT_IDS.MOBILE
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.accessToken)).toBe('current-mobile-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.refreshToken)).toBe('current-mobile-refresh')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBeNull()
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.refreshToken)).toBeNull()
  })

  it('keeps a complete legacy PC session for Vue Admin compatibility', () => {
    const storage = memoryStorage({
      zsjos_access_token: 'legacy-pc-access',
      zsjos_refresh_token: 'legacy-pc-refresh',
      zsjos_client_id: AUTH_CLIENT_IDS.PC
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBe('legacy-pc-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.refreshToken)).toBe('legacy-pc-refresh')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.clientId)).toBe(AUTH_CLIENT_IDS.PC)
    expect(storage.getItem('zsjos_access_token')).toBeNull()
  })

  it('treats a complete legacy session without clientId as PC', () => {
    const storage = memoryStorage({
      zsjos_access_token: 'legacy-access',
      zsjos_refresh_token: 'legacy-refresh'
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBe('legacy-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.refreshToken)).toBe('legacy-refresh')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.clientId)).toBe(AUTH_CLIENT_IDS.PC)
  })

  it('moves a complete legacy Mobile session to Mobile and clears legacy keys', () => {
    const storage = memoryStorage({
      zsjos_access_token: 'legacy-mobile-access',
      zsjos_refresh_token: 'legacy-mobile-refresh',
      zsjos_client_id: AUTH_CLIENT_IDS.MOBILE
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.accessToken)).toBe('legacy-mobile-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.refreshToken)).toBe('legacy-mobile-refresh')
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.clientId)).toBe(AUTH_CLIENT_IDS.MOBILE)
    expect(storage.getItem('zsjos_access_token')).toBeNull()
  })

  it('does not combine fields from different token families', () => {
    const storage = memoryStorage({
      [AUTH_STORAGE_KEYS.PC.accessToken]: 'pc-access-only',
      zsjos_refresh_token: 'legacy-refresh-only',
      zsjos_client_id: AUTH_CLIENT_IDS.PC
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBeNull()
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.refreshToken)).toBeNull()
    expect(storage.getItem('zsjos_refresh_token')).toBeNull()
  })

  it('clears partial or unknown-client sessions and requires re-login', () => {
    const storage = memoryStorage({
      [AUTH_STORAGE_KEYS.MOBILE.accessToken]: 'mobile-access-only',
      zsjos_access_token: 'legacy-access',
      zsjos_refresh_token: 'legacy-refresh',
      zsjos_client_id: 'unknown-client'
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.accessToken)).toBeNull()
    expect(storage.getItem('zsjos_access_token')).toBeNull()
    expect(storage.getItem('zsjos_refresh_token')).toBeNull()
  })

  it('is idempotent for already separated PC and Mobile sessions', () => {
    const storage = memoryStorage({
      [AUTH_STORAGE_KEYS.PC.accessToken]: 'pc-access',
      [AUTH_STORAGE_KEYS.PC.refreshToken]: 'pc-refresh',
      [AUTH_STORAGE_KEYS.PC.clientId]: AUTH_CLIENT_IDS.PC,
      [AUTH_STORAGE_KEYS.MOBILE.accessToken]: 'mobile-access',
      [AUTH_STORAGE_KEYS.MOBILE.refreshToken]: 'mobile-refresh',
      [AUTH_STORAGE_KEYS.MOBILE.clientId]: AUTH_CLIENT_IDS.MOBILE
    })
    migrateLegacyAuthStorage(storage)
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBe('pc-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.accessToken)).toBe('mobile-access')
  })
})

describe('refresh session generation', () => {
  it('accepts a result only while the refresh token still identifies the same session', () => {
    expect(isCurrentRefreshSession('refresh-old', 'refresh-old')).toBe(true)
    expect(isCurrentRefreshSession('refresh-old', 'refresh-new')).toBe(false)
  })

  it('treats a newly created or cleared session as stale for an older refresh', () => {
    expect(isCurrentRefreshSession(null, 'refresh-new')).toBe(false)
    expect(isCurrentRefreshSession('refresh-old', null)).toBe(false)
  })
})

describe('request authentication platform capture', () => {
  it('keeps an already captured request platform when the current page platform changes', () => {
    expect(resolveRequestAuthPlatform('PC', 'MOBILE')).toBe('PC')
    expect(resolveRequestAuthPlatform(undefined, 'MOBILE')).toBe('MOBILE')
  })

  it('removes a stale Authorization header before applying the selected platform token', () => {
    const deleted: string[] = []
    const headers: { Authorization?: string; delete: (name: string) => void } = {
      Authorization: 'Bearer pc-token',
      delete: (name: string) => { deleted.push(name); delete headers.Authorization }
    }
    applyAuthorizationHeader(headers, null)
    expect(deleted).toEqual(['Authorization'])
    expect(headers.Authorization).toBeUndefined()
    applyAuthorizationHeader(headers, 'mobile-token')
    expect(headers.Authorization).toBe('Bearer mobile-token')
  })
})

describe('shared tenant storage', () => {
  it('reads the Vue Admin web-storage-cache envelope', () => {
    const storage = { getItem: () => JSON.stringify({ c: Date.now(), e: 253402300799999, v: '8' }) }
    expect(readSharedTenantId(storage)).toBe('8')
  })

  it('rejects missing, expired, and non-positive tenant identifiers', () => {
    expect(readSharedTenantId({ getItem: () => null })).toBeUndefined()
    expect(readSharedTenantId({ getItem: () => JSON.stringify({ c: 1, e: 2, v: '8' }) })).toBeUndefined()
    expect(readSharedTenantId({ getItem: () => '0' })).toBeUndefined()
  })

  it('writes a value that Vue Admin can decode', () => {
    let stored = ''
    writeSharedTenantId('12', { setItem: (_key, value) => { stored = value } })
    const envelope = JSON.parse(stored) as { v: string }
    expect(JSON.parse(envelope.v)).toBe(12)
  })
})
