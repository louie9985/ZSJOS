import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { APP_ROUTES, PC_ONLY_NATIVE_ROUTES } from '../constants'

describe('PC-only native routes', () => {
  it('declares content production and review as PC-only while keeping material browsing mobile', () => {
    expect([...PC_ONLY_NATIVE_ROUTES]).toEqual([
      APP_ROUTES.CONTENT_PRODUCTION,
      APP_ROUTES.CONTENT_REVIEW
    ])
    expect(PC_ONLY_NATIVE_ROUTES.has(APP_ROUTES.MATERIAL_LIBRARY)).toBe(false)
  })

  it('blocks direct Mobile access and passes the immutable auth platform into every route host', () => {
    const host = readFileSync(new URL('./RouteHost.tsx', import.meta.url), 'utf8')
    const shell = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8')

    expect(host).toContain("authPlatform === 'MOBILE' && PC_ONLY_NATIVE_ROUTES.has")
    expect(host).toContain('请使用电脑端访问')
    expect(shell.match(/<RouteHost\b/g)).toHaveLength(2)
    expect(shell.match(/<RouteHost[^>]*authPlatform=\{authPlatform\}/g)).toHaveLength(2)
  })
})
