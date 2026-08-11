import { describe, expect, it } from 'vitest'
import { validateSalesOrderSubmission } from './salesOrder'

describe('validateSalesOrderSubmission', () => {
  it('requires mobile or WeChat', () => {
    expect(validateSalesOrderSubmission(' ', undefined, 0, 0)).toBe('手机号和微信号至少填写一个')
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
