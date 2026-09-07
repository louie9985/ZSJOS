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
    expect(page).toContain('options={{ density: true, fullScreen: true, setting: true, reload:')
  })

  it('resizes headers with an 80px minimum and separate local persistence', () => {
    expect(page).toContain('LEAD_TABLE_COLUMN_MIN_WIDTH = 80')
    expect(page).toContain("LEAD_TABLE_COLUMN_WIDTHS_KEY = 'crm-lead-management-table-column-widths'")
    expect(page).toContain("className: 'lead-table-resizable-header'")
    expect(page).toContain('onPointerDownCapture: event => startLeadTableColumnResize')
    expectSourceToContainTokens(page, "window.addEventListener('pointermove', handlePointerMove)")
    expect(page).toContain('event.stopPropagation()')
    expectSourceToContainTokens(page, "columnKey === 'action' ? column.onHeaderCell")
    expectSourceNotToContainTokens(page, 'title: columnKey ===')
    expect(page).toContain("persistenceKey: 'crm-lead-management-table-columns'")
    expect(styles).toContain('.lead-table-resizable-header')
    expect(styles).toContain('touch-action: pan-y')
  })
})
