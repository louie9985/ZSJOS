import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const mainSource = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8')

describe('authenticated workbench entry', () => {
  it('keeps a restored session on the current route but redirects after a fresh login', () => {
    expect(mainSource).toContain('const [loginRedirectPending, setLoginRedirectPending] = useState(false)')
    expect(mainSource).toContain('getAuthenticatedHomeTarget(authorizedMenus)')
    expect(mainSource).toContain('if (publicLoginRedirect || loginRedirectPending) {')
    expect(mainSource).toContain('navigateRef.current(publicLoginRedirect || homeTarget || fallbackTarget || \'/\', { replace: true })')
    expect(mainSource).toContain('setLoginRedirectPending(true)')
    expect(mainSource).toContain('setLoginRedirectPending(false)')
    expect(mainSource).not.toContain('publicLoginRedirect || !standalonePath')
  })
})
