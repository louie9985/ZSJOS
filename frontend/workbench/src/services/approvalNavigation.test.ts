import { expect, it } from 'vitest'
import { BUSINESS_APPROVAL_PROCESS_KEYS } from './approvalNavigation'

it('includes appeal tasks in workflow lists and counts while retaining the order exclusion', () => {
  const excluded = BUSINESS_APPROVAL_PROCESS_KEYS.split(',')
  expect(excluded).not.toContain('zsjos_lead_appeal_review')
  expect(excluded).toContain('zsjos_sales_order_dual_approval')
})
