import { describe, expect, it } from 'vitest'
import { AUTH_PLATFORM_SESSION_KEY, AUTH_STORAGE_KEYS, resolveAuthPlatform } from '../constants'
import { getAuthAccessToken, isCurrentRefreshSession, migrateLegacyAuthStorage, readSharedTenantId, writeSharedTenantId } from './api'

function memoryStorage(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
    values
  }
}

describe('authentication platform', () => {
  it('pins a /zsjos/mobile entry to the Mobile session for later menu routes in the same tab', () => {
    const storage = memoryStorage()
    expect(resolveAuthPlatform('/zsjos/mobile', storage)).toBe('MOBILE')
    expect(resolveAuthPlatform('/zsjos/tasks/today', storage)).toBe('MOBILE')
    expect(storage.getItem(AUTH_PLATFORM_SESSION_KEY)).toBe('MOBILE')
  })

  it('uses PC for a regular Workbench/Admin tab without a Mobile entry marker', () => {
    const storage = memoryStorage()
    expect(resolveAuthPlatform('/zsjos/tasks/today', storage)).toBe('PC')
    expect(storage.getItem(AUTH_PLATFORM_SESSION_KEY)).toBe('PC')
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
  it('moves an unprefixed Mobile session without overwriting an existing Mobile session', () => {
    const storage = memoryStorage({
      [AUTH_STORAGE_KEYS.PC.accessToken]: 'legacy-mobile-access',
      [AUTH_STORAGE_KEYS.PC.refreshToken]: 'legacy-mobile-refresh',
      [AUTH_STORAGE_KEYS.PC.clientId]: 'zsjos-mobile',
      [AUTH_STORAGE_KEYS.MOBILE.accessToken]: 'current-mobile-access',
      [AUTH_STORAGE_KEYS.MOBILE.refreshToken]: 'current-mobile-refresh',
      [AUTH_STORAGE_KEYS.MOBILE.clientId]: 'zsjos-mobile'
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.accessToken)).toBe('current-mobile-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.MOBILE.refreshToken)).toBe('current-mobile-refresh')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBeNull()
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.refreshToken)).toBeNull()
  })

  it('keeps a complete unprefixed PC session for Vue Admin compatibility', () => {
    const storage = memoryStorage({
      zsjos_access_token: 'legacy-pc-access',
      zsjos_refresh_token: 'legacy-pc-refresh',
      zsjos_client_id: 'zsjos-pc'
    })
    migrateLegacyAuthStorage(storage)
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.accessToken)).toBe('legacy-pc-access')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.refreshToken)).toBe('legacy-pc-refresh')
    expect(storage.getItem(AUTH_STORAGE_KEYS.PC.clientId)).toBe('zsjos-pc')
    expect(storage.getItem('zsjos_access_token')).toBeNull()
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
