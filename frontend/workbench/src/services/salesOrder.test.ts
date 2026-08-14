import { describe, expect, it } from 'vitest'
import { canReviewSalesOrderTask, mergeSalesOrderListItems, salesOrderTaskKey, validateSalesOrderSubmission } from './salesOrder'
import type { SalesOrderApprovalStatus, SalesOrderListItem } from './api'

describe('validateSalesOrderSubmission', () => {
  it('requires mobile or WeChat', () => {
    expect(validateSalesOrderSubmission(' ', undefined, 0, 0)).toBe('请填写手机号或微信号')
  })

  it('requires a voucher for non-zero orders', () => {
    expect(validateSalesOrderSubmission('13800138000', undefined, 0.01, 0))
      .toBe('已付款的非零订单必须上传缴费凭证')
  })

  it('allows zero amount without a voucher and non-zero amount with one', () => {
    expect(validateSalesOrderSubmission(undefined, 'student-wechat', 0, 0)).toBeUndefined()
    expect(validateSalesOrderSubmission('13800138000', undefined, 100, 1)).toBeUndefined()
  })
})

describe('sales-order inbox helpers', () => {
  const item = (id: number, taskId?: string): SalesOrderListItem => ({
    id, taskId, orderNo: `SO-${id}`, leadId: id, status: 'pending_approval', studentName: `学员${id}`,
    totalAmount: 100, approvalRoundNo: 1, submittedAt: 1
  })

  it('merges personal orders by order id', () => {
    expect(mergeSalesOrderListItems([item(1)], [item(1), item(2)]).map(value => value.id)).toEqual([1, 2])
  })

  it('keeps two approval-center tasks for the same order', () => {
    const merged = mergeSalesOrderListItems([item(1, 'registration-task')], [item(1, 'finance-task')], salesOrderTaskKey)
    expect(merged.map(value => value.taskId)).toEqual(['registration-task', 'finance-task'])
  })

  it('only allows actions while the selected center task is still pending', () => {
    const task = { ...item(1, 'registration-task'), taskDefinitionKey: 'registrationReview' as const }
    const order: { status: 'pending_approval'; registrationApproval: SalesOrderApprovalStatus } = {
      status: 'pending_approval', registrationApproval: { status: 'pending' }
    }
    expect(canReviewSalesOrderTask(order, task)).toBe(true)
    order.registrationApproval = { status: 'approved', reviewerUserId: 233, reviewerUserName: '审核员甲', endTime: 1 }
    expect(canReviewSalesOrderTask(order, task)).toBe(false)
  })
})
