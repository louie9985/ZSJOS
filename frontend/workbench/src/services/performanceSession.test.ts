import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AUTH_STORAGE_KEYS, STORAGE_KEYS } from '../constants'
import { resetAuthPlatformForTest } from './authSession'
import { AUTH_EXPIRED_EVENT, http } from './api'
import { IMPERSONATION_CHANGE_EVENT } from './impersonation'
import { isPerformanceAccessDenied, performanceSessionIdentity, subscribePerformanceSession } from './performanceSession'

function storage() {
  const values = new Map<string, string>()
  return { getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) }, removeItem: (key: string) => { values.delete(key) } }
}
beforeEach(() => {
  resetAuthPlatformForTest()
  vi.stubGlobal('localStorage', storage()); vi.stubGlobal('sessionStorage', storage())
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { pathname: '/zsjos/sales-performance' } }))
})
afterEach(() => { resetAuthPlatformForTest(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('statistics authentication context', () => {
  it('changes identity on account, tenant, logout and impersonation while normalizing the tenant', () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.PC.accessToken, 'fixture-account-a')
    localStorage.setItem(STORAGE_KEYS.TENANT_ID, '1')
    const original = performanceSessionIdentity()
    localStorage.setItem(STORAGE_KEYS.TENANT_ID, JSON.stringify({ v: '1' }))
    expect(performanceSessionIdentity()).toBe(original)
    localStorage.setItem(STORAGE_KEYS.TENANT_ID, '2')
    expect(performanceSessionIdentity()).not.toBe(original)
    localStorage.setItem(STORAGE_KEYS.TENANT_ID, '1')
    localStorage.setItem(AUTH_STORAGE_KEYS.PC.accessToken, 'fixture-account-b')
    expect(performanceSessionIdentity()).not.toBe(original)
    localStorage.setItem(AUTH_STORAGE_KEYS.PC.accessToken, 'fixture-account-a')
    localStorage.setItem(STORAGE_KEYS.IMPERSONATION, JSON.stringify({ id: 1, administratorUserId: 1,
      targetUserId: 2, administratorNameSnapshot: 'fixture', targetNameSnapshot: 'fixture', reason: 'test',
      status: 'active', startedAt: '2026-10-08', lastActiveAt: '2026-10-08' }))
    expect(performanceSessionIdentity()).not.toBe(original)
    localStorage.removeItem(STORAGE_KEYS.IMPERSONATION)
    expect(performanceSessionIdentity()).toBe(original)
    localStorage.removeItem(AUTH_STORAGE_KEYS.PC.accessToken)
    expect(performanceSessionIdentity()).not.toBe(original)
  })
  it('subscribes to context signals only while the statistics page is mounted', () => {
    const use = vi.spyOn(http.interceptors.response, 'use'), eject = vi.spyOn(http.interceptors.response, 'eject')
    const listener = vi.fn(), dispose = subscribePerformanceSession(listener)
    for (const event of ['storage', 'focus', AUTH_EXPIRED_EVENT, IMPERSONATION_CHANGE_EVENT]) window.dispatchEvent(new Event(event))
    expect(listener).toHaveBeenCalledTimes(4); expect(use).toHaveBeenCalledTimes(1)
    dispose(); window.dispatchEvent(new Event('storage'))
    expect(listener).toHaveBeenCalledTimes(4); expect(eject).toHaveBeenCalledWith(use.mock.results[0].value)
  })
  it('distinguishes authorization denial from retryable service/network failure', () => {
    for (const code of [401, 403, 1900090001]) expect(isPerformanceAccessDenied({ code })).toBe(true)
    expect(isPerformanceAccessDenied({ isAxiosError: true, code: 'ERR_BAD_REQUEST', response: { status: 403 } })).toBe(true)
    for (const error of [null, new Error('network'), { code: 500 }]) expect(isPerformanceAccessDenied(error)).toBe(false)
  })
})
