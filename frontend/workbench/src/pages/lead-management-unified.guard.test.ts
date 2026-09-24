import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens } from '../test/sourceGuard'

describe('unified Lead management route', () => {
  it('renders one canonical page and redirects legacy relation routes', () => {
    const routeHost = readFileSync('src/layouts/RouteHost.tsx', 'utf8')

    expectSourceToContainTokens(routeHost, 'APP_ROUTES.LEAD_MANAGEMENT) return <LeadManagementPage permissions={permissions}/>')
    expect(routeHost).toContain("relationScope: 'submitted'")
    expect(routeHost).toContain("relationScope: 'owned'")
    expect(routeHost).not.toContain('<LeadManagementPage audience=')
  })

  it('uses one unified relation scope', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expectSourceToContainTokens(page, "const audience: LeadAudience = 'all'")
    expect(page).toContain('relationScope: routeState?.relationScope')
  })

  it('drives graded filters from the server profile without restoring relation tabs', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    // 分级筛选由服务端筛选方案下发，页面不得再维护静态筛选项数组。
    expectSourceToContainTokens(page, "await api.leadInboxFilterProfile('management')")
    expect(page).toContain('filterProfile.groups.map')
    expect(page).toContain('activeGroup.sections.map')
    expect(page).not.toContain('SIMPLE_STATUS_OPTIONS')
    expect(page).not.toContain('simpleStatus')
    // 二级行选项一律来自服务端下发的 section.options。
    expect(page).toContain('section.options.map')
    expect(page).not.toContain('我提交的')
    expect(page).not.toContain('我负责的')
    // 分配环节属于服务端配置内容，不得重新硬编码进页面。
    expect(page).not.toContain("label: '待分配'")
    expect(page).not.toContain("label: '待接单'")
    expect(page).not.toContain("label: '抢单池'")
    // 服务端筛选项不可用时只提示，不得回退到硬编码选项。
    expect(page).toContain("setFilterProfileError")
    expect(page).toContain('setFilterProfile({ groups: [] })')
  })

  it('silently refreshes the changed lead without losing its selection', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expectSourceToContainTokens(page, 'preferredSelectedId: id, silent: true')
    expect(page).toContain('loadDetail(id, true)')
  })

  it('preserves deep-linked lazy targets and hydrates missing action leads', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expect(page).toContain('preserveRequestedId: routeSelectionRef.current !== undefined')
    expectSourceToContainTokens(page, 'setItems(current => current.some(item => item.id === id) ? current.map(item => item.id === id ? loaded : item) : pinLeadFirst(current, loaded))')
    expect(page).toContain('const loaded = await api.managedLead(leadId)')
    expect(page).toContain('routeSelectionRef.current = undefined')
  })

  it('refreshes the mounted follow-up timeline after the standalone modal succeeds', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    const panel = readFileSync('src/components/LeadFollowUpPanel.tsx', 'utf8')

    expect(detail).toContain('setFollowUpRefreshVersion(current => current + 1)')
    expect(detail).toContain('refreshVersion={followUpRefreshVersion}')
    expect(detail).toContain('onSuccess={handleStandaloneFollowUpSuccess}')
    expectSourceToContainTokens(panel, 'useEffect(() => { void loadRecords() }, [loadRecords, refreshVersion])')
  })

  it('renders the permission-scoped next follow-up time in the detail hero', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')

    expect(api).toContain('nextFollowUpAt?: Timestamp')
    expectSourceToContainTokens(detail, "visibleTabs.includes('follow-ups') && lead.nextFollowUpAt")
    expect(detail).toContain('className="lead-hero-next-followup"')
    expect(detail).toContain('formatTimestamp(lead.nextFollowUpAt)')
  })
})
