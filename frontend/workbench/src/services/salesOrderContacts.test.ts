import { describe, expect, it } from 'vitest'
import { resolveSalesOrderContacts, validateSalesOrderSubmission } from './salesOrder'

describe('order contact requirements and restoration', () => {
  it.each([undefined, '', '  '])('rejects missing mobile or WeChat (%s)', value => {
    expect(validateSalesOrderSubmission(value, 'wx', 100, 1)).toBe('请填写手机号')
    expect(validateSalesOrderSubmission('13800138000', value, 100, 1)).toBe('请填写微信号')
  })
  it('retains nonempty draft values and fills empty fields from order then Lead', () => {
    expect(resolveSalesOrderContacts({ mobile: '  ', wechatId: 'draft-wx' }, { studentMobile: '13800138000', studentWechatId: 'order-wx' },
      { submittedMobile: '13900139000', submittedWechatId: 'lead-wx' })).toEqual({ mobile: '13800138000', wechatId: 'draft-wx' })
    expect(resolveSalesOrderContacts({ wechatId: '' }, undefined, { submittedWechatId: 'lead-wx' })).toEqual({ mobile: undefined, wechatId: 'lead-wx' })
  })
  it('restores canonical aliases in older drafts', () => {
    expect(resolveSalesOrderContacts({ studentMobile: '13800138000', studentWechatId: 'wx' }, undefined, {}))
      .toEqual({ mobile: '13800138000', wechatId: 'wx' })
  })
  it('uses the authorized order Lead projection only for missing revision contacts', () => {
    expect(resolveSalesOrderContacts(undefined, { studentMobile: '13800138000',
      leadProfile: { submittedMobile: '13900139000', submittedWechatId: 'lead-wx' } }, {}))
      .toEqual({ mobile: '13800138000', wechatId: 'lead-wx' })
  })
})
