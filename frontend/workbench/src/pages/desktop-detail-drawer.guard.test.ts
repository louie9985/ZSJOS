import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const detailPages = [
  'pages/MessageInboxPage.tsx',
  'pages/LeadAppealPage.tsx',
  'pages/MySalesOrderPage.tsx',
  'pages/SalesOrderApprovalPage.tsx',
  'components/SalesOrderSupervisorInbox.tsx',
  'pages/LeadDuplicateReviewPage.tsx',
  'pages/LeadComplaintPage.tsx',
  'pages/LeadManagementPage.tsx',
  'pages/ManagementPages.tsx'
]

const resizableTableDetailPages = [
  'pages/MessageInboxPage.tsx',
  'pages/LeadAppealPage.tsx',
  'pages/MySalesOrderPage.tsx',
  'pages/SalesOrderApprovalPage.tsx',
  'pages/LeadDuplicateReviewPage.tsx',
  'pages/LeadManagementPage.tsx',
  'pages/BpmApprovalCenterPage.tsx',
  'pages/AnnouncementCenterPage.tsx'
]

describe('employee detail drawer breakpoints', () => {
  it('keeps table-mode detail drawers resizable through the shared component', () => {
    for (const page of resizableTableDetailPages) {
      const source = readFileSync(`src/${page}`, 'utf8')
      expect(source).toContain('ResizableDetailDrawer')
      expect(source).toContain('desktopResizable')
    }
  })

  for (const page of detailPages) {
    it(`${page} opens detail drawers only from a mobile viewport`, () => {
      const source = readFileSync(`src/${page}`, 'utf8')
      const openLines = source.split('\n').filter(line => line.includes('setDrawerOpen(true)'))

      expect(openLines.length).toBeGreaterThan(0)
      if (page.endsWith('MessageInboxPage.tsx')) {
        expect(source).toContain('inboxLayoutMode === \'table\'')
        expect(source).toContain('message-inbox-detail-pane')
        expect(source).toContain('message-inbox-table-drawer')
        expect(source).toContain('placement="right"')
        expect(source).not.toContain('if (selected) setDrawerOpen(true)')
      } else if (page.includes('SalesOrderSupervisorInbox.tsx'))
        expect(source).toContain("window.matchMedia(\"(max-width: 768px)\").matches")
      else for (const line of openLines)
        expect(line).toContain("window.matchMedia('(max-width: 768px)').matches")
      if (!page.endsWith('MessageInboxPage.tsx'))
        expect(source).toMatch(/<main className="(?:message-inbox|lead-inbox|sales-order|business-inbox)-detail-pane">/)
    })
  }
})
