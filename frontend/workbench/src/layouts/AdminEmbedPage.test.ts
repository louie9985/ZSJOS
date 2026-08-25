import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import {
  ADMIN_EMBED_MESSAGE,
  buildAdminEmbedUrl,
  isAdminEmbedResponse
} from './AdminEmbedPage'

describe('global Admin embed frame', () => {
  it('builds the initial same-origin embed URL without authentication data', () => {
    const url = buildAdminEmbedUrl('/system/user')

    expect(url).toBe('/admin-embed/system/user?embed=workbench')
    expect(url).not.toMatch(/token|authorization/i)
  })

  it('accepts only Admin response messages with a route path', () => {
    expect(isAdminEmbedResponse({
      type: ADMIN_EMBED_MESSAGE.READY,
      path: '/system/user'
    })).toBe(true)
    expect(isAdminEmbedResponse({
      type: ADMIN_EMBED_MESSAGE.ROUTE_CHANGED,
      path: '/system/role'
    })).toBe(true)
    expect(isAdminEmbedResponse({
      type: ADMIN_EMBED_MESSAGE.NAVIGATE,
      path: '/system/user'
    })).toBe(false)
    expect(isAdminEmbedResponse({ type: ADMIN_EMBED_MESSAGE.READY, path: 1 })).toBe(false)
  })

  it('keeps one iframe host and the same protocol in both frontends', () => {
    const frame = readFileSync(new URL('./AdminEmbedPage.tsx', import.meta.url), 'utf8')
    const shell = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8')
    const routeHost = readFileSync(new URL('./RouteHost.tsx', import.meta.url), 'utf8')
    const adminBridge = readFileSync(
      new URL('../../../admin/src/utils/workbenchEmbedBridge.ts', import.meta.url),
      'utf8'
    )

    expect(frame.match(/<iframe\b/g)).toHaveLength(1)
    expect(frame).toContain('onLoad={handleFrameLoad}')
    expect(shell.match(/<AdminEmbedFrame\b/g)).toHaveLength(1)
    expect(routeHost).not.toContain('AdminEmbedPage')
    Object.values(ADMIN_EMBED_MESSAGE).forEach(type => expect(adminBridge).toContain(type))
  })
})
