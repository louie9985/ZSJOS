import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const mainSource = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8')

describe('authenticated workbench entry', () => {
  it('redirects a restored or newly authenticated session after permission loading', () => {
    expect(mainSource).toContain('getAuthenticatedHomeTarget(authorizedMenus)')
    expect(mainSource).toContain('navigateRef.current(homeTarget || fallbackTarget || \'/\', { replace: true })')
    expect(mainSource).toContain('navigateRef.current = navigate')
    expect(mainSource).toContain('}, [authPlatform, logged, permissionAttempt])')
    expect(mainSource.indexOf('navigateRef.current(homeTarget || fallbackTarget')).toBeLessThan(mainSource.indexOf('setInfo(permissionInfo)'))
  })
})
