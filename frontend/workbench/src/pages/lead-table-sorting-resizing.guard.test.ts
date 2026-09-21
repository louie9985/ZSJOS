import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceNotToContainTokens, expectSourceToContainTokens } from '../test/sourceGuard'

const page = readFileSync(new URL('LeadManagementPage.tsx', import.meta.url), 'utf8')
const api = readFileSync(new URL('../services/api.ts', import.meta.url), 'utf8')
const styles = readFileSync(new URL('../styles/pages/lead-management.css', import.meta.url), 'utf8')

describe('lead ProTable sorting and column resizing', () => {
  it('uses allowlisted server sorting without changing native ProTable options', () => {
    expect(page).toContain("lastActivityAt: 'lastActivityAt'")
    expect(page).toContain("source: 'sourceType'")
    expect(page).not.toMatch(/sourceUser:\s*'sourceUserId'/)
    expect(page).not.toMatch(/owner:\s*'ownerUserId'/)
    expect(page).not.toMatch(/product:\s*'/)
    expectSourceToContainTokens(page, 'sorter: backendSortField ? true : undefined')
    expectSourceToContainTokens(page, "if (extra.action !== 'sort') return")
    expect(page).toContain('sortField,')
    expect(page).toContain('sortOrder,')
    expect(api).toContain('sortField?: LeadSortField;')
    expectSourceToContainTokens(api, 'sortOrder?: "ascend" | "descend";')
    expect(page).toContain('onReload={')
  })

  it('delegates resizing while retaining both legacy preference keys', () => {
    expect(page).toContain('widthPersistenceKey="crm-lead-management-table-column-widths"')
    expect(page).toContain("persistenceKey: 'crm-lead-management-table-columns'")
    expect(page).toContain('BusinessTable<ManagedLead>')
    expect(page).not.toContain('startLeadTableColumnResize')
    expect(styles).not.toContain('.lead-table-resizable-header')
  })
})
