import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('unified sales-order approval entry', () => {
  it('renders review and supervisor worklists from the same page by permission', () => {
    const source = readFileSync('src/pages/SalesOrderApprovalPage.tsx', 'utf8')

    expect(source).toContain('resolveSalesOrderApprovalAccess(permissions)')
    expect(source).toContain('<SalesOrderSupervisorInbox')
    expect(source).toContain('requestedConfirmationId={requestedConfirmationId}')
    expect(source).toContain("label: '订单审批'")
    expect(source).toContain("label: '主管确认'")
    expect(source).not.toContain("label: '双中心审批'")
    expect(existsSync('src/pages/SalesOrderSupervisorConfirmationPage.tsx')).toBe(false)
  })

  it('redirects the legacy supervisor URL and does not render it as a menu route', () => {
    const main = readFileSync('src/main.tsx', 'utf8')
    const routes = readFileSync('src/layouts/RouteHost.tsx', 'utf8')
    const constants = readFileSync('src/constants.ts', 'utf8')

    expect(main).toContain('path={APP_ROUTES.SALES_ORDER_SUPERVISOR_CONFIRMATIONS}')
    expect(main).toContain('to={APP_ROUTES.SALES_ORDER_APPROVALS}')
    expect(routes).not.toContain('SalesOrderSupervisorConfirmationPage')
    expect(constants.match(/APP_ROUTES\.SALES_ORDER_SUPERVISOR_CONFIRMATIONS/g)).toBeNull()
  })

  it('keeps ordinary decisions visible during supervisor add-sign and uses generic submission copy', () => {
    const detail = readFileSync('src/components/SalesOrderDetailCards.tsx', 'utf8')
    const entry = readFileSync('src/components/SalesOrderEntryModal.tsx', 'utf8')

    expect(detail).toContain("mode === 'approval-todo' && canReview")
    expect(detail).not.toContain('canReview && !supervisorPending')
    expect(detail).toContain('<SalesOrderApprovalRail nodes={approvalNodes}/>')
    expect(detail).toContain('sales-order-approval-sidebar')
    expect(detail).toContain('<Timeline className="sales-order-approval-track"')
    expect(entry).toContain("'提交审批'")
    expect(entry).toContain("'重新提交审批'")
    expect(entry).not.toMatch(/提交双中心审批|提交会签|重新提交会签/)
  })

  it('uses approval and rejection wording for supervisor decisions', () => {
    const supervisor = readFileSync('src/components/SalesOrderSupervisorInbox.tsx', 'utf8')

    expect(supervisor).toContain('通过')
    expect(supervisor).toContain('驳回')
    expect(supervisor).toMatch(/okText=\{decision === ["']confirm["'] \? ["']通过["'] : ["']驳回["']\}/)
    expect(supervisor).not.toMatch(/>确认<\/Button>|>不确认<\/Button>/)
  })

  it('lets supervisors switch between todo, done, and all confirmation records', () => {
    const supervisor = readFileSync('src/components/SalesOrderSupervisorInbox.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')

    expect(supervisor).toContain('type SupervisorInboxScope = "todo" | "done" | "all"')
    expect(supervisor).toContain('scope === "all" ? undefined : scope === "done"')
    expect(supervisor).toContain('{ label: "全部", value: "all" }')
    expect(api).toContain('handled?: boolean;')
  })

  it('renders the linked Lead business profile without falling back to an internal id', () => {
    const detail = readFileSync('src/components/SalesOrderDetailCards.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')
    const styles = readFileSync('src/styles/pages/sales-order.css', 'utf8')

    expect(api).toContain('leadProfile?: {')
    expect(detail).toContain('leadProfile && <section className="sales-order-info-block sales-order-info-block-wide">')
    expect(detail).toContain('>客资编号</span>')
    expect(detail).toContain('leadProfile.leadNo')
    expect(detail).toContain('<CopyButton value={leadProfile.submittedMobile}/>')
    expect(detail).not.toMatch(/leadProfile\.leadNo\s*\|\|\s*(order\.)?leadId/)
    expect(styles).toContain('.sales-order-information')
    expect(styles).toContain('.sales-order-detail-layout')
    expect(styles).toContain('.sales-order-approval-sidebar')
  })
})
