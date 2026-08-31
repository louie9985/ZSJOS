import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'

describe('announcement surface guards', () => {
  it('registers the server-owned Workbench route', () => {
    expect(APP_ROUTES.ANNOUNCEMENTS).toBe('/messages/notice')
    expect(RENDERABLE_APP_ROUTES.has(APP_ROUTES.ANNOUNCEMENTS)).toBe(true)
  })

  it('removes executable rich-text content and hardens external links', () => {
    const source = readFileSync(new URL('./SafeRichText.tsx', import.meta.url), 'utf8')
    expect(source).toContain("querySelectorAll('script,iframe,object,embed,form')")
    expect(source).toContain("startsWith('on')")
    expect(source).toContain("new Set(['http:', 'https:', 'mailto:'])")
    expect(source).toContain("link.rel = 'noopener noreferrer'")
  })
})
