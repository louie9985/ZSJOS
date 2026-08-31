import { describe, expect, it } from 'vitest'
import { getPaymentLinkActionLabel } from './SalesOrderEntryModal'

describe('getPaymentLinkActionLabel', () => {
  it('shows generate for a fresh online draft', () => {
    expect(getPaymentLinkActionLabel('online_link', undefined)).toBe('生成支付链接')
  })

  it('hides the action while the payment link is still active', () => {
    expect(getPaymentLinkActionLabel('online_link', undefined, {
      paymentStatus: 'waiting',
      paymentUrl: 'https://example.com/pay/PAY123',
    })).toBeNull()
  })

  it('shows regenerate after the payment link expires or closes', () => {
    expect(getPaymentLinkActionLabel('online_link', undefined, {
      paymentStatus: 'expired',
      paymentUrl: 'https://example.com/pay/PAY123',
    })).toBe('重新生成支付链接')
    expect(getPaymentLinkActionLabel('online_link', undefined, {
      paymentStatus: 'closed',
      paymentUrl: 'https://example.com/pay/PAY123',
    })).toBe('重新生成支付链接')
  })

  it('never shows a link action for non-online orders or existing orders', () => {
    expect(getPaymentLinkActionLabel('offline_paid', undefined)).toBeNull()
    expect(getPaymentLinkActionLabel('online_link', 101)).toBeNull()
  })
})
