import { describe, it, expect, vi } from 'vitest'
import { PerformanceCache, PERFORMANCE_CACHE_TTL, performanceQueryKey } from './performanceCache'

describe('page-local performance cache', () => {
  it('expires 600 seconds after success, never extends TTL on a hit', async () => {
    let now = 0
    const cache = new PerformanceCache(() => now), loader = vi.fn(async () => 12)
    await cache.load('overview', loader)
    now = PERFORMANCE_CACHE_TTL - 1
    expect((await cache.load('overview', loader)).data).toBe(12)
    now++
    expect(cache.peek('overview')).toBeUndefined()
    await cache.load('overview', loader)
    expect(loader).toHaveBeenCalledTimes(2)
  })
  it('starts TTL on response, deduplicates pending requests, caches empty success', async () => {
    let now = 0, complete!: (value: unknown[]) => void
    const cache = new PerformanceCache(() => now), loader = vi.fn(() => new Promise<unknown[]>(resolve => { complete = resolve }))
    const first = cache.load('a', loader), second = cache.load('a', loader)
    expect(first).toBe(second)
    await Promise.resolve(); now = 5000; complete([])
    await first
    expect(cache.peek('a')).toEqual({ data: [], updatedAt: 5000 })
    expect(loader).toHaveBeenCalledTimes(1)
  })
  it('keeps at most 32 successful results and evicts least recently used', async () => {
    const cache = new PerformanceCache()
    for (let i = 0; i < 32; i++) await cache.load(String(i), async () => i)
    await cache.load('0', async () => -1)
    await cache.load('32', async () => 32)
    expect(cache.peek('1')).toBeUndefined()
    expect(cache.peek('0')?.data).toBe(0)
    expect(cache.peek('32')?.data).toBe(32)
  })
  it('refresh and identity disposal abort requests and reject late writes', async () => {
    const cache = new PerformanceCache()
    let complete!: (value: number) => void, signal!: AbortSignal
    const previous = cache.load('x', s => { signal = s; return new Promise<number>(resolve => { complete = resolve }) })
    await Promise.resolve(); cache.clear(); expect(signal.aborted).toBe(true)
    await cache.load('x', async () => 2)
    complete(1); await expect(previous).rejects.toMatchObject({ name: 'AbortError' })
    expect(cache.peek('x')?.data).toBe(2)
  })
  it('does not cache failures or individual cancellation', async () => {
    const cache = new PerformanceCache(), loader = vi.fn().mockRejectedValueOnce(new Error('failure')).mockResolvedValue(7)
    await expect(cache.load('x', loader)).rejects.toThrow('failure')
    expect(cache.peek('x')).toBeUndefined()
    expect((await cache.load('x', loader)).data).toBe(7)
    cache.invalidate('x'); expect(cache.peek('x')).toBeUndefined()
  })
  it('a late rejection from a cancelled request cannot revoke a new result', async () => {
    const cache = new PerformanceCache()
    let reject!: (reason: unknown) => void
    const previous = cache.load('a', () => new Promise((_resolve, fail) => { reject = fail }))
    await Promise.resolve(); cache.clear(); await cache.load('a', async () => 2)
    reject({ code: 403 }); await expect(previous).rejects.toMatchObject({ name: 'AbortError' })
    expect(cache.peek('a')?.data).toBe(2)
  })
  it('separates resources/scopes/dates and canonicalizes parameter order', () => {
    expect(performanceQueryKey('analysis', { scopeId: 1, start: '2026-10-01', grain: undefined }))
      .toBe(performanceQueryKey('analysis', { start: '2026-10-01', scopeId: 1 }))
    expect(performanceQueryKey('analysis', { scopeId: 1 })).not.toBe(performanceQueryKey('analysis', { scopeId: 2 }))
    expect(performanceQueryKey('analysis', { scopeId: 1 })).not.toBe(performanceQueryKey('overview', { scopeId: 1 }))
  })
  it('a denied request clears all old results until explicit retry', async () => {
    const cache = new PerformanceCache()
    await cache.load('overview', async () => 1); await cache.load('analysis', async () => 2)
    cache.clear('无权查看该业绩范围')
    expect(cache.peek('overview')).toBeUndefined(); expect(cache.peek('analysis')).toBeUndefined()
    expect(cache.blockedError).toBe('无权查看该业绩范围')
    cache.clear(); expect(cache.blockedError).toBe('')
  })
})
