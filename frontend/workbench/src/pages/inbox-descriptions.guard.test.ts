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

describe('my sales-order page chrome', () => {
  it('keeps the page header free of title and descriptive copy', () => {
    const source = readFileSync('src/pages/MySalesOrderPage.tsx', 'utf8')

    expect(source).toContain('className="sales-order-inbox-actions"')
    expect(source).not.toContain('>我的订单<')
    expect(source).not.toContain('查看本人提交的全部成交订单及当前状态')
  })
})
