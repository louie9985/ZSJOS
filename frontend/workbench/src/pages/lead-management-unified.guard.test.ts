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

  it('uses one unified relation scope', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expect(page).toContain("const audience: LeadAudience = 'all'")
    expect(page).toContain('relationScope: routeState?.relationScope')
  })

  it('keeps all simple status filters without restoring relation tabs', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    for (const label of ['全部', '待首跟', '待跟进', '待判定',
      '成交待审核', '已成交', '已判无效', '已关闭', '已挂起']) {
      expect(page).toContain(`label: '${label}'`)
    }
    expect(page).toContain("simpleStatus: simpleStatus === 'all' ? undefined : simpleStatus")
    expect(page).not.toContain('我提交的')
    expect(page).not.toContain('我负责的')
    expect(page).not.toContain("label: '待分配'")
    expect(page).not.toContain("label: '待接单'")
    expect(page).not.toContain("label: '抢单池'")
  })

  it('silently refreshes the changed lead without losing its selection', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expect(page).toContain('preferredSelectedId: id, silent: true')
    expect(page).toContain('loadDetail(id, true)')
  })

  it('preserves deep-linked lazy targets and hydrates missing action leads', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')

    expect(page).toContain('preserveRequestedId: routeSelectionRef.current !== undefined')
    expect(page).toContain('setItems(current => current.some(item => item.id === id) ? current : pinLeadFirst(current, loaded))')
    expect(page).toContain('const loaded = await api.managedLead(leadId)')
    expect(page).toContain('routeSelectionRef.current = undefined')
  })

  it('refreshes the mounted follow-up timeline after the standalone modal succeeds', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    const panel = readFileSync('src/components/LeadFollowUpPanel.tsx', 'utf8')

    expect(detail).toContain('setFollowUpRefreshVersion(current => current + 1)')
    expect(detail).toContain('refreshVersion={followUpRefreshVersion}')
    expect(detail).toContain('onSuccess={handleStandaloneFollowUpSuccess}')
    expect(panel).toContain('useEffect(() => { void loadRecords() }, [loadRecords, refreshVersion])')
  })

  it('renders the permission-scoped next follow-up time in the detail hero', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')

    expect(api).toContain('nextFollowUpAt?: Timestamp')
    expect(detail).toContain("visibleTabs.includes('follow-ups') && lead.nextFollowUpAt")
    expect(detail).toContain('className="lead-hero-next-followup"')
    expect(detail).toContain('formatTimestamp(lead.nextFollowUpAt)')
  })
})
