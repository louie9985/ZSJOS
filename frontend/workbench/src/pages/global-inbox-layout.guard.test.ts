import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { LEAD_DISPATCH_MODE_LABELS } from '../constants'

const root = new URL('.', import.meta.url)
const pages = [
  'MessageInboxPage.tsx',
  'LeadManagementPage.tsx',
  'LeadAppealPage.tsx',
  'BpmApprovalCenterPage.tsx',
  'MySalesOrderPage.tsx',
  'SalesOrderApprovalPage.tsx',
  'AnnouncementCenterPage.tsx',
  'LeadDuplicateReviewPage.tsx'
]

describe('global inbox layout mode', () => {
  it('labels self-sourced dispatch mode in Chinese', () => {
    expect(LEAD_DISPATCH_MODE_LABELS.self).toBe('自拓录')
  })

  it('keeps eligible inbox pages wired to the shared setting', () => {
    for (const page of pages) {
      const source = readFileSync(new URL(page, root), 'utf8')
      const detailActionSource = ['MySalesOrderPage.tsx', 'SalesOrderApprovalPage.tsx'].includes(page)
        ? `${source}\n${readFileSync(new URL('../components/SalesOrderTableColumns.tsx', root), 'utf8')}`
        : source
      if (page !== 'MessageInboxPage.tsx') expect(source).toContain('useInboxTableLayout')
      expect(source).toContain('ProTable')
      expect(source).toContain('columnsState')
      expect(detailActionSource).toContain('详细')
    }
  })

  it('keeps the customer table configurable and reuses the full detail surface', () => {
    const lead = readFileSync(new URL('LeadManagementPage.tsx', root), 'utf8')
    const detail = readFileSync(new URL('../components/LeadDetail.tsx', root), 'utf8')
    expect(lead).toContain('ProTable')
    expect(lead).toContain('columnsState')
    expect(lead).toContain('LeadDetail')
    expect(lead).toContain('detailContent')
    expect(lead).toContain('toolBarRender')
    expect(lead).toContain('lead-management-table-filter-toolbar')
    expect(lead).toContain('AdvancedFilterToolbar scene="lead" pageKey="lead_management"')
    expect(detail).toContain("if (tab === 'follow-ups')")
    expect(detail).toContain("if (tab === 'appeals')")
    expect(detail).toContain("if (tab === 'complaints')")
    expect(detail).toContain("if (tab === 'flow-history')")
    expect(detail).toContain('LeadCustomerOrders')
  })
})
