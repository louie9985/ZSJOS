import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { expectSourceNotToContainTokens, expectSourceToContainTokens } from '../test/sourceGuard'

const mainSource = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8')

describe('authenticated workbench entry', () => {
  it('keeps a restored session on the current route but redirects after a fresh login', () => {
    expectSourceToContainTokens(mainSource, 'const [loginRedirectPending, setLoginRedirectPending] = useState(false)')
    expect(mainSource).toContain('getAuthenticatedHomeTarget(authorizedMenus)')
    expectSourceToContainTokens(mainSource, 'if (publicLoginRedirect || loginRedirectPending) {')
    expectSourceToContainTokens(mainSource, 'navigateRef.current(publicLoginRedirect || homeTarget || fallbackTarget || \'/\', { replace: true })')
    expect(mainSource).toContain('setLoginRedirectPending(true)')
    expect(mainSource).toContain('setLoginRedirectPending(false)')
    expectSourceNotToContainTokens(mainSource, 'publicLoginRedirect || !standalonePath')
  })
})
