import { describe, expect, it } from 'vitest'
import { APP_ROUTES, AUTH_PLATFORM_SESSION_KEY, MOBILE_ROUTE_BASE, RENDERABLE_APP_ROUTES } from '../constants'
import { isMobileRoute, normalizeMobileStartup, platformHref } from './mobileRoutes'
import { initializeAuthPlatform, resetAuthPlatformForTest } from './authSession'

describe('persistent mobile route namespace', () => {
  it('round trips every supported native route without changing server route identity', () => {
    for (const path of new Set([...RENDERABLE_APP_ROUTES, ...Object.values(APP_ROUTES), '/system/user'])) {
      const href = platformHref(`${path}?id=2#detail`, 'MOBILE')
      expect(href).toBe(`${MOBILE_ROUTE_BASE}${path}?id=2#detail`)
      expect(href.slice(MOBILE_ROUTE_BASE.length)).toBe(`${path}?id=2#detail`)
      expect(platformHref(href, 'MOBILE')).toBe(href)
      expect(platformHref(path, 'PC')).toBe(path)
    }
  })

  it('preserves external and relative links and uses an exact namespace boundary', () => {
    for (const path of ['https://example.test/a', '//example.test/a', '#detail', '../list']) {
      expect(platformHref(path, 'MOBILE')).toBe(path)
    }
    expect(isMobileRoute('/zsjos/mobile-other')).toBe(false)
    expect(isMobileRoute('/other/zsjos/mobile')).toBe(false)
    expect(isMobileRoute('/zsjos/mobile/')).toBe(true)
  })

  it('identifies a copied deep link as Mobile in a fresh tab without a session marker', () => {
    resetAuthPlatformForTest()
    const values = new Map<string, string>()
    const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value) } }
    expect(initializeAuthPlatform(platformHref(APP_ROUTES.BPM_TODO, 'MOBILE'), storage)).toBe('MOBILE')
    expect(values.get(AUTH_PLATFORM_SESSION_KEY)).toBe('MOBILE')
    resetAuthPlatformForTest()
  })

  it('upgrades old Mobile tabs in place, retaining query, hash and history state', () => {
    const changes: unknown[][] = []
    const history = { state: { idx: 3 }, replaceState: (...args: unknown[]) => { changes.push(args) } }
    normalizeMobileStartup('MOBILE', { pathname: APP_ROUTES.BPM_TODO, search: '?id=5', hash: '#task' }, history)
    expect(changes).toEqual([[{ idx: 3 }, '', `${MOBILE_ROUTE_BASE}${APP_ROUTES.BPM_TODO}?id=5#task`]])
    normalizeMobileStartup('PC', { pathname: '/', search: '', hash: '' }, history)
    normalizeMobileStartup('MOBILE', { pathname: MOBILE_ROUTE_BASE, search: '', hash: '' }, history)
    expect(changes).toHaveLength(1)
  })
})
