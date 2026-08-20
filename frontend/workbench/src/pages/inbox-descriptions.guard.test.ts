import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const inboxSources = [
  'components/SalesOrderDetailCards.tsx',
  'components/SalesOrderSupervisorInbox.tsx',
  'pages/LeadAgingPoolPage.tsx',
  'pages/LeadAppealPage.tsx',
  'pages/LeadDuplicateReviewPage.tsx',
  'pages/LeadManagementPage.tsx',
  'pages/MessageInboxPage.tsx',
  'pages/MySalesOrderPage.tsx',
  'pages/RegistrationPages.tsx',
  'pages/SalesOrderApprovalPage.tsx',
  'pages/SubordinateSalesPage.tsx',
  'pages/WorkPlanPage.tsx'
]

describe('inbox detail presentation', () => {
  for (const sourcePath of inboxSources) {
    it(`${sourcePath} does not use Ant Design Descriptions`, () => {
      const source = readFileSync(`src/${sourcePath}`, 'utf8')

      expect(source).not.toMatch(/\bDescriptions\b/)
      expect(source).not.toContain('<Descriptions')
    })
  }
})
