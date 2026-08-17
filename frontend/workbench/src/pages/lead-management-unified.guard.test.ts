import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('unified Lead management route', () => {
  it('renders one canonical page and redirects legacy relation routes', () => {
    const routeHost = readFileSync('src/layouts/RouteHost.tsx', 'utf8')

    expect(routeHost).toContain('APP_ROUTES.LEAD_MANAGEMENT) return <LeadManagementPage permissions={permissions}/>')
    expect(routeHost).toContain("relationScope: 'submitted'")
    expect(routeHost).toContain("relationScope: 'owned'")
    expect(routeHost).not.toContain('<LeadManagementPage audience=')
  })

  it('derives submitted and owned controls from permission codes', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expect(page).toContain("permissions.includes('zsjos:lead:query-submitted')")
    expect(page).toContain("permissions.includes('zsjos:lead:query-owned')")
    expect(page).toContain("{ value: 'submitted', label: '我提交的', disabled: !canViewSubmitted }")
    expect(page).toContain("{ value: 'owned', label: '我负责的', disabled: !canViewOwned }")
    expect(page).toContain('relationScope')
  })
})
