import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(process.cwd(), 'src')
const read = (file: string) => readFileSync(resolve(root, file), 'utf8')

const targetSurfaces = [
  ['pages/LeadManagementPage.tsx', 'lead', 'lead_management'],
  ['pages/LeadClaimPoolPage.tsx', 'lead', 'lead_claim_pool'],
  ['pages/LeadAgingPoolPage.tsx', 'lead', 'lead_aging_pool'],
  ['pages/MySalesOrderPage.tsx', 'order', 'sales_order_'],
  ['pages/SalesOrderApprovalPage.tsx', 'order', 'sales_order_approval:'],
  ['components/SalesOrderSupervisorInbox.tsx', 'order', 'sales_order_supervisor_confirm'],
  ['pages/LeadAppealPage.tsx', 'lead_appeal', 'lead_appeal'],
  ['pages/LeadDuplicateReviewPage.tsx', 'duplicate_review', 'lead_duplicate_review'],
  ['pages/RegistrationPages.tsx', 'registration', 'registration_pool'],
  ['pages/RegistrationPages.tsx', 'student', 'student_my'],
  ['pages/SubordinateSalesPage.tsx', 'lead', 'subordinate_sales_leads'],
  ['pages/SubordinateSalesPage.tsx', 'subordinate_sales', 'subordinate_sales']
] as const

describe('business inbox advanced-filter guard', () => {
  it('does not load unscoped system users for personnel filter options', () => {
    const source = read('components/AdvancedFilter.tsx')
    expect(source).not.toContain('api.simpleUsers()')
    expect(source).not.toContain("source === 'visible-users'")
  })

  it.each(targetSurfaces)('%s wires the %s server filter scene and page key', (file, scene, pageKey) => {
    const source = read(file)
    expect(source).toContain('AdvancedFilterToolbar')
    expect(source).toContain(`scene="${scene}"`)
    expect(source).toContain(pageKey)
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

  it('keeps advanced-filter templates as structured conditions instead of SQL', () => {
    const source = read('services/api.ts')
    expect(source).toContain('AdvancedFilterTemplate')
    expect(source).toContain('filter: AdvancedFilterGroup')
    expect(source).toContain('startFieldKey?: string')
    expect(source).toContain('unit?: "minute" | "hour" | "day"')
    expect(source).toContain('/zsjos/advanced-filter-template/visible-list')
    expect(source).not.toContain('filterSql')
    expect(source).not.toContain('TIMESTAMPDIFF')
  })

  it('keeps template selection and saving inside the filter drawer draft flow', () => {
    const source = read('components/AdvancedFilter.tsx')
    const toolbarStart = source.indexOf('<div className="advanced-filter-toolbar">')
    const appliedTagsStart = source.indexOf('{active.length > 0')
    const toolbarSource = source.slice(toolbarStart, appliedTagsStart)

    expect(source).toContain('advanced-filter-template-panel')
    expect(toolbarSource).not.toContain('advanced-filter-template-select')
    expect(toolbarSource).not.toContain('SaveOutlined')
    expect(source).toContain('setDraft(cloneFilterGroup(template.filter))')
    expect(source).toContain('filter: effective')
    expect(source).not.toContain('onChange(cloneFilterGroup(template.filter))')
  })

  it('renders duration diff as drawer-only structured controls', () => {
    const source = read('components/AdvancedFilter.tsx')
    expect(source).toContain("condition.fieldKey === 'duration.diff'")
    expect(source).toContain('advanced-filter-duration-control')
    expect(source).toContain('startFieldKey')
    expect(source).toContain('endFieldKey')
    expect(source).toContain('durationUnitOptions')
    expect(source).not.toContain('filterSql')
  })

  it('adapts every shared filter drawer to its actual container width', () => {
    const component = read('components/AdvancedFilter.tsx')
    const styles = read('styles/components/advanced-filter.css')
    expect(component).toContain('popupMatchSelectWidth={FILTER_SELECT_POPUP_WIDTH}')
    expect(component).toContain('popupMatchSelectWidth={FILTER_COMPACT_POPUP_WIDTH}')
    expect(component).toContain('popupMatchSelectWidth={FILTER_TEMPLATE_POPUP_WIDTH}')
    expect(styles).toContain('container-type: inline-size')
    expect(styles).toContain('@container(max-width:500px)')
    expect(styles).toContain('grid-column: 1/-1')
    expect(component).toContain('advanced-filter-value-control')
    expect(styles).toContain('.advanced-filter-value-control')
    expect(styles).not.toContain('.advanced-filter-condition>div')
  })

  it('keeps the claim pool on a fixed twelve-card server page', () => {
    const source = read('pages/LeadClaimPoolPage.tsx')
    expect(source).toContain('const PAGE_SIZE = 12')
    expect(source).toContain('setItems(result.list)')
    expect(source).toContain('setPageNo(targetPage)')
    expect(source).toContain('<Pagination current={pageNo} pageSize={PAGE_SIZE}')
    expect(source).not.toContain('IntersectionObserver')
    expect(source).not.toContain('mergeUniqueLeads')
  })
})
