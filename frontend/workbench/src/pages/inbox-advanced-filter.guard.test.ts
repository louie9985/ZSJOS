import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(process.cwd(), 'src')
const read = (file: string) => readFileSync(resolve(root, file), 'utf8')

const targetSurfaces = [
  ['pages/LeadManagementPage.tsx', 'lead'],
  ['pages/LeadClaimPoolPage.tsx', 'lead'],
  ['pages/LeadAgingPoolPage.tsx', 'lead'],
  ['pages/LeadQualificationExceptionPage.tsx', 'lead'],
  ['pages/MySalesOrderPage.tsx', 'order'],
  ['pages/SalesOrderApprovalPage.tsx', 'order'],
  ['components/SalesOrderSupervisorInbox.tsx', 'order'],
  ['pages/LeadAppealPage.tsx', 'lead_appeal'],
  ['pages/LeadDuplicateReviewPage.tsx', 'duplicate_review'],
  ['pages/RegistrationPages.tsx', 'registration'],
  ['pages/RegistrationPages.tsx', 'student'],
  ['pages/SubordinateSalesPage.tsx', 'subordinate_sales']
] as const

describe('business inbox advanced-filter guard', () => {
  it('does not load unscoped system users for personnel filter options', () => {
    const source = read('components/AdvancedFilter.tsx')
    expect(source).not.toContain('api.simpleUsers()')
    expect(source).not.toContain("source === 'visible-users'")
  })

  it.each(targetSurfaces)('%s wires the %s server filter scene', (file, scene) => {
    const source = read(file)
    expect(source).toContain('AdvancedFilterToolbar')
    expect(source).toContain(`scene="${scene}"`)
    expect(source).toContain('advancedFilter')
  })

  it.each([...new Set(targetSurfaces.map(([file]) => file))])('%s has the required refresh behavior', file => {
    const source = read(file)
    const refreshRequired = [
      'pages/LeadManagementPage.tsx',
      'pages/MySalesOrderPage.tsx',
      'pages/SalesOrderApprovalPage.tsx',
      'components/SalesOrderSupervisorInbox.tsx',
      'pages/RegistrationPages.tsx'
    ].includes(file)
    if (refreshRequired) expect(source).toContain('ReloadOutlined')
  })

  it('keeps every advanced-filter request on a server search endpoint', () => {
    const source = read('services/api.ts')
    for (const endpoint of [
      '/lead-duplicate-review/search-page',
      '/lead/appeal/inbox/search-page',
      '/sales-order/supervisor-confirmation/search-page',
      '/subordinate-sales/search-page',
      '/registration/pool/search-page',
      '/student/my/search-page'
    ]) expect(source).toContain(endpoint)
  })
})
