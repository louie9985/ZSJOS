import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('unified sales-order approval entry', () => {
  it('renders review and supervisor worklists from the same page by permission', () => {
    const source = readFileSync('src/pages/SalesOrderApprovalPage.tsx', 'utf8')

    expect(source).toContain('resolveSalesOrderApprovalAccess(permissions)')
    expect(source).toContain('<SalesOrderSupervisorInbox/>')
    expect(source).toContain("label: '双中心审批'")
    expect(source).toContain("label: '主管确认'")
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
})
