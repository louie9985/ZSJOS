import { describe, expect, it, vi } from 'vitest'
import { createIdempotencyKey } from './idempotency'

describe('createIdempotencyKey', () => {
  it('uses randomUUID when the secure-origin API is available', () => {
    const randomUUID = vi.fn(() => 'secure-origin-key')
    const source = { randomUUID, getRandomValues: vi.fn() } as unknown as Crypto
    expect(createIdempotencyKey(source)).toBe('secure-origin-key')
    expect(randomUUID).toHaveBeenCalledOnce()
  })

  it('creates an RFC 4122 key when randomUUID is unavailable on LAN HTTP', () => {
    const source = {
      getRandomValues: (values: Uint8Array) => {
        values.set(Array.from({ length: 16 }, (_, index) => index))
        return values
      }
    } as Crypto
    expect(createIdempotencyKey(source)).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f')
  })
})
