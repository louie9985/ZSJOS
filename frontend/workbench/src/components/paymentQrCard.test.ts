import { describe, expect, it } from 'vitest'
import type { PurchaseIntent } from '../services/api'
import { canSharePaymentQr } from './paymentQrImage'

const intent = { collectionMode: 'online_link', paymentUrl: 'https://example.com/pay/demo', paymentStatus: 'waiting', paymentExpiresAt: 2000 } as PurchaseIntent
describe('payment QR sharing lifecycle', () => {
  it('allows active created/waiting links before expiration', () => {
    expect(canSharePaymentQr(intent, 1000)).toBe(true)
    expect(canSharePaymentQr({ ...intent, paymentStatus: 'created' }, 1000)).toBe(true)
  })
  it('blocks settled, closed, expired and cancellation-pending links', () => {
    for (const paymentStatus of ['paid', 'closed', 'expired', undefined] as const) {
      expect(canSharePaymentQr({ ...intent, paymentStatus }, 1000)).toBe(false)
    }
    expect(canSharePaymentQr({ ...intent, paymentCancelPending: true }, 1000)).toBe(false)
    expect(canSharePaymentQr(intent, 2000)).toBe(false)
  })
  it('fails closed without URL/expiry or for offline collection', () => {
    expect(canSharePaymentQr({ ...intent, paymentUrl: undefined }, 1000)).toBe(false)
    expect(canSharePaymentQr({ ...intent, paymentExpiresAt: undefined }, 1000)).toBe(false)
    expect(canSharePaymentQr({ ...intent, paymentExpiresAt: NaN }, 1000)).toBe(false)
    expect(canSharePaymentQr({ ...intent, collectionMode: 'offline_paid' }, 1000)).toBe(false)
  })
})
