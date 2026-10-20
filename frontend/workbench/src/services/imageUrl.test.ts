import { describe, expect, it } from 'vitest'
import { retryImageUrl } from './imageUrl'

describe('retryImageUrl', () => {
  it('removes expired S3 signature parameters while preserving the object path', () => {
    const value = retryImageUrl(
      'https://files.example.test/a.png?tenant=1&X-Amz-Date=20260917T000000Z&X-Amz-Signature=expired',
      2,
    )
    expect(value).toBe('https://files.example.test/a.png?tenant=1&__image_retry=2')
  })

  it('keeps relative URLs relative and adds a cache-busting retry marker', () => {
    expect(retryImageUrl('/assets/a.png?mode=cover', 1)).toBe('/assets/a.png?mode=cover&__image_retry=1')
  })
})
