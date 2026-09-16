import { describe, expect, it } from 'vitest'
import { getPaymentLinkActionLabel, paymentAlertMessage, paymentAlertType } from './SalesOrderEntryModal'

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

describe('payment alert', () => {
  it('marks a pending cancellation as warning and surfaces the gateway reason', () => {
    const intent = {
      paymentStatus: 'waiting' as const,
      paymentCancelPending: true,
      paymentCancelMessage: '原交易不存在',
    }
    expect(paymentAlertType(intent)).toBe('warning')
    expect(paymentAlertMessage(intent)).toBe('取消结果待确认：原交易不存在')
  })

  it('falls back to a generic retry hint when no reason is recorded', () => {
    expect(paymentAlertMessage({ paymentStatus: 'waiting', paymentCancelPending: true }))
      .toBe('取消结果待确认，请稍后重试或刷新状态')
  })

  it('reports paid, expired and active states', () => {
    expect(paymentAlertType({ paymentStatus: 'paid' })).toBe('success')
    expect(paymentAlertMessage({ paymentStatus: 'paid' })).toBe('通联已确认到账')
    expect(paymentAlertType({ paymentStatus: 'closed' })).toBe('warning')
    expect(paymentAlertType({ paymentStatus: 'waiting' })).toBe('info')
  })
})
