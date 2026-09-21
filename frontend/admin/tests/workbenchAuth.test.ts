import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../src/utils/jsencrypt', () => ({ encrypt: (value: string) => value, decrypt: (value: string) => value }))

class MemoryStorage {
  values = new Map<string, string>()
  get length() { return this.values.size }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  getItem(key: string) { return this.values.get(key) ?? null }
  setItem(key: string, value: string) { this.values.set(key, String(value)) }
  removeItem(key: string) { this.values.delete(key) }
  clear() { this.values.clear() }
}
let storage: MemoryStorage
function setup(search: string, parentPath?: string) {
  storage = new MemoryStorage()
  const location = { search, origin: 'https://fixture.test', pathname: '/admin-embed/system/user', assign: vi.fn(), reload: vi.fn() }
  const windowMock: Record<string, unknown> = { location, localStorage: storage }
  windowMock.parent = parentPath ? { location: { ...location, pathname: parentPath } } : windowMock
  vi.stubGlobal('Storage', MemoryStorage)
  vi.stubGlobal('localStorage', storage)
  vi.stubGlobal('window', windowMock)
  for (const [key, value] of Object.entries({ ACCESS_TOKEN: 'pc-access', REFRESH_TOKEN: 'pc-refresh', CLIENT_ID: 'zsjos-pc', MOBILE_ACCESS_TOKEN: 'mobile-access', MOBILE_REFRESH_TOKEN: 'mobile-refresh', MOBILE_CLIENT_ID: 'zsjos-mobile', zsjos_access_token: 'legacy-access' })) storage.setItem(key, value)
}

beforeEach(() => { vi.resetModules() })
afterEach(() => { vi.unstubAllGlobals() })

describe('Vue Admin Mobile embed authentication isolation', () => {
  it('reads/writes/clears only Mobile tokens, including refresh responses without clientId', async () => {
    setup('?embed=workbench&platform=MOBILE')
    const auth = await import('../src/utils/auth')
    expect(auth.getAccessToken()).toBe('mobile-access')
    expect(auth.getRefreshToken()).toBe('mobile-refresh')
    expect(auth.getClientId()).toBe('zsjos-mobile')
    auth.setToken({ accessToken: 'mobile-new', refreshToken: 'mobile-refresh-new' })
    expect(storage.getItem('MOBILE_CLIENT_ID')).toBe('zsjos-mobile')
    expect(() => auth.setToken({ accessToken: 'wrong', refreshToken: 'wrong', clientId: 'zsjos-pc' })).toThrow()
    auth.removeToken()
    expect(auth.getAccessToken()).toBeUndefined()
    expect(auth.getRefreshToken()).toBeUndefined()
    expect(storage.getItem('ACCESS_TOKEN')).toBe('pc-access')
    expect(storage.getItem('REFRESH_TOKEN')).toBe('pc-refresh')
    expect(storage.getItem('zsjos_access_token')).toBe('legacy-access')
  })

  it('recovers Mobile from the same-origin parent after iframe navigation removes the query', async () => {
    setup('', '/zsjos/mobile/system/user')
    const context = await import('../src/utils/workbenchAuth')
    expect(context.isMobileWorkbench).toBe(true)
    window.location.search = '?embed=workbench&platform=PC'
    expect(context.isMobileWorkbench).toBe(true)
    const cache = await import('../src/hooks/web/useCache')
    expect(cache.CACHE_KEY.USER).toBe('mobileUser')
    expect(cache.CACHE_KEY.ROLE_ROUTERS).toBe('mobileRoleRouters')
    expect(cache.CACHE_KEY.VisitTenantId).toBe('mobileVisitTenantId')
    expect(cache.CACHE_KEY.TenantId).toBe('tenantId')
  })

  it('preserves standalone and embedded PC compatibility', async () => {
    setup('?embed=workbench')
    const auth = await import('../src/utils/auth')
    expect(auth.getAccessToken()).toBe('pc-access')
    expect(auth.getClientId()).toBe('zsjos-pc')
    auth.removeToken()
    expect(storage.getItem('MOBILE_ACCESS_TOKEN')).toBe('mobile-access')
    const { CACHE_KEY } = await import('../src/hooks/web/useCache')
    expect(CACHE_KEY.USER).toBe('user')
  })

  it('does not interpret a generic platform query or unrelated parent as Mobile', async () => {
    setup('?platform=MOBILE', '/system/user')
    const context = await import('../src/utils/workbenchAuth')
    expect(context.isMobileWorkbench).toBe(false)
    expect(context.resolveWorkbenchAuthPlatform('', '/zsjos/mobile-other')).toBe('PC')
  })
})
