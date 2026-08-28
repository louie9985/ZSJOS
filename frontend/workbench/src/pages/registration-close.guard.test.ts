import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('registration close-service', () => {
  it('renders the close action only through server permissions and typed api', () => {
    const page = readFileSync('src/pages/RegistrationPages.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')
    const routeHost = readFileSync('src/layouts/RouteHost.tsx', 'utf8')

    expect(page).toContain('hasPermission(permissions, "zsjos:registration:close")')
    expect(page).toContain('关闭服务')
    expect(page).toContain('closeOpen')
    expect(page).toContain('closeRegistration')
    expect(api).toContain('closeRegistration')
    expect(api).toContain('/zsjos/registration/${id}/close')
    expect(routeHost).toContain('return <RegistrationPoolPage permissions={permissions}/>')
  })
})
