import { describe, expect, it } from 'vitest'
import { validateSalesOrderAmounts, validateSalesOrderDecisionReason, buildDictionaryLabelMap, canReviewSalesOrderTask, mergeSalesOrderListItems, resolveDictionaryLabel, salesOrderDetailToListItem, salesOrderTaskKey, validateSalesOrderSubmission } from './salesOrder'
import type { SalesOrder, SalesOrderApprovalStatus, SalesOrderListItem } from './api'

describe('validateSalesOrderSubmission', () => {
  it('requires mobile or WeChat', () => {
    expect(validateSalesOrderSubmission(' ', undefined, 0, 0)).toBe('请填写手机号或微信号')
  })

  it('rejects zero vouchers for every order amount', () => {
    expect(validateSalesOrderSubmission('13800138000', undefined, 0.01, 0))
      .toBe('所有订单必须上传至少一份缴费凭证')
    expect(validateSalesOrderSubmission('13800138000', undefined, 0, 0))
      .toBe('所有订单必须上传至少一份缴费凭证')
  })

  it('accepts one through six vouchers and rejects seven', () => {
    expect(validateSalesOrderSubmission(undefined, 'student-wechat', 0, 1)).toBeUndefined()
    expect(validateSalesOrderSubmission('13800138000', undefined, 100, 1)).toBeUndefined()
    expect(validateSalesOrderSubmission('13800138000', undefined, 100, 6)).toBeUndefined()
    expect(validateSalesOrderSubmission('13800138000', undefined, 100, 7))
      .toBe('缴费凭证最多上传 6 份')
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

  it('builds a pinned navigation item from an exact order detail', () => {
    const detail = {
      id: 25, orderNo: 'SO-25', personId: 2, orderType: 'repurchase', status: 'revision_required',
      studentName: '学员25', totalAmount: 800, approvalRoundNo: 2, submittedAt: 25
    } as SalesOrder
    expect(salesOrderDetailToListItem(detail)).toMatchObject({
      id: 25, orderNo: 'SO-25', orderType: 'repurchase', status: 'revision_required', approvalRoundNo: 2
    })
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

describe('sales-order dictionary labels', () => {
  const labels = buildDictionaryLabelMap([
    { dictType: 'zsjos_order_fee_mode', value: 'retail', label: '零售缴费' },
    { dictType: 'zsjos_order_payment_method', value: 'learning_qr', label: '学习二维码' }
  ])

  it('shows backend-owned labels without exposing stable values', () => {
    expect(resolveDictionaryLabel('retail', labels, 'ready')).toBe('零售缴费')
    expect(resolveDictionaryLabel('learning_qr', labels, 'ready')).toBe('学习二维码')
  })

  it('distinguishes loading, failed, missing, and empty values', () => {
    expect(resolveDictionaryLabel('retail', labels, 'loading')).toBe('标签加载中')
    expect(resolveDictionaryLabel('retail', labels, 'error')).toBe('标签加载失败')
    expect(resolveDictionaryLabel('unknown', labels, 'ready')).toBe('标签未配置')
    expect(resolveDictionaryLabel(undefined, labels, 'ready')).toBe('-')
  })
})

describe('decision reason contract', () => {
  it.each(['', '   ', '正常意见'])('allows optional approvals: %j', reason => {
    expect(validateSalesOrderDecisionReason('approve', false, reason)).toBeUndefined()
  })
  it.each(['', '   '])('requires old-definition approval and all rejection reasons: %j', reason => {
    expect(validateSalesOrderDecisionReason('approve', true, reason)).toBe('请填写审批意见')
    for (const required of [true, false])
      expect(validateSalesOrderDecisionReason('reject', required, reason)).toBe('请填写驳回原因')
  })
  it('blocks missing configuration and accepts actual rejection reasons', () => {
    expect(validateSalesOrderDecisionReason('approve', undefined, '')).toBe('审批意见配置未加载，请重试')
    expect(validateSalesOrderDecisionReason('reject', false, ' 补正资料 ')).toBeUndefined()
  })
})


describe('purchase draft and submission amounts', () => {
  const items = (...amounts: number[]) => amounts.map((actualAmount, i) => ({ courseKey: `spu::sku-${i}`, actualAmount }))
  it.each([[0], [0, 0], [0, 12.34]])('accepts offline amounts %j', (...amounts) => {
    expect(validateSalesOrderAmounts(items(...amounts), 'offline_paid')).toBeUndefined()
  })
  it('requires a positive online total but permits free line items', () => {
    expect(validateSalesOrderAmounts(items(0, 0), 'online_link')).toContain('零金额订单请选择线下已支付')
    expect(validateSalesOrderAmounts(items(0.01), 'online_link')).toBeUndefined()
    expect(validateSalesOrderAmounts(items(0, 0.01), 'online_link')).toBeUndefined()
  })
  it.each([-1, NaN, Infinity, 0.001, 1.999])('rejects invalid line amount %s', amount => {
    expect(validateSalesOrderAmounts(items(amount, 100), 'offline_paid')).toBeTruthy()
  })
  it('does not omit incomplete rows or coerce empty values to zero', () => {
    expect(validateSalesOrderAmounts(undefined, 'offline_paid')).toBeTruthy()
    expect(validateSalesOrderAmounts([], 'offline_paid')).toBeTruthy()
    expect(validateSalesOrderAmounts([...items(0), { courseKey: 'spu::sku' }], 'offline_paid')).toBeTruthy()
    expect(validateSalesOrderAmounts([{ actualAmount: 0 }], 'offline_paid')).toBeTruthy()
    expect(validateSalesOrderAmounts([{ courseKey: 'spu::', actualAmount: 0 }], 'offline_paid')).toBeTruthy()
  })
})
