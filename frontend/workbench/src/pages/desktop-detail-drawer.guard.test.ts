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
  'pages/ManagementPages.tsx'
]

describe('employee detail drawer breakpoints', () => {
  for (const page of detailPages) {
    it(`${page} opens detail drawers only from a mobile viewport`, () => {
      const source = readFileSync(`src/${page}`, 'utf8')
      const openLines = source.split('\n').filter(line => line.includes('setDrawerOpen(true)'))

      expect(openLines.length).toBeGreaterThan(0)
      if (page.includes('SalesOrderSupervisorInbox.tsx'))
        expect(source).toContain("window.matchMedia(\"(max-width: 768px)\").matches")
      else for (const line of openLines)
        expect(line).toContain("window.matchMedia('(max-width: 768px)').matches")
      expect(source).toMatch(/<main className="(?:message-inbox|lead-inbox|sales-order|business-inbox)-detail-pane">/)
    })
  }
})
