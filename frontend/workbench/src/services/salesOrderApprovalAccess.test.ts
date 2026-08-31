import { describe, expect, it } from 'vitest'
import { resolveSalesOrderApprovalAccess } from './salesOrderApprovalAccess'

describe('sales-order approval access', () => {
  it.each([
    [['zsjos:sales-order:review'], true, false, 'approval', false],
    [['zsjos:sales-order:supervisor-confirm'], false, true, 'supervisor', false],
    [['zsjos:sales-order:review', 'zsjos:sales-order:supervisor-confirm'], true, true, 'approval', true],
    [[], false, false, undefined, false]
  ] as const)('resolves permission combination %#', (permissions, canReview, canConfirmSupervisor, defaultWorkType, showWorkTypeSwitch) => {
    expect(resolveSalesOrderApprovalAccess([...permissions])).toEqual({
      canReview,
      canConfirmSupervisor,
      defaultWorkType,
      showWorkTypeSwitch
    })
  })
})
