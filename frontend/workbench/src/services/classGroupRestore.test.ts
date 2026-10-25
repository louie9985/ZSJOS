import { describe, expect, it, vi } from 'vitest'
import { createClassReadQueue, restoreClassPages } from './classGroupRestore'

describe('class range restoration', () => {
  it('restores the complete browsed prefix with larger pages and bounded concurrency', async () => {
    let active = 0, maximum = 0
    const queue = createClassReadQueue()
    const read = vi.fn((pageNo: number, pageSize: number) => queue(async () => {
      maximum = Math.max(maximum, ++active)
      await new Promise(resolve => setTimeout(resolve, 1))
      active--
      return { list: Array.from({ length: pageSize }, (_, index) => (pageNo - 1) * pageSize + index), total: 2000 }
    }))
    const [root, child] = await Promise.all([
      restoreClassPages(100, read, () => true), restoreClassPages(20, read, () => true),
    ])
    expect(root?.list).toEqual(Array.from({ length: 1200 }, (_, index) => index))
    expect(child?.list).toHaveLength(240)
    expect(read).toHaveBeenCalledTimes(24)
    expect(maximum).toBeLessThanOrEqual(2)
  })

  it('retries a failed range from its beginning and respects a smaller current total', async () => {
    let fail = true
    const read = vi.fn(async (page: number, size: number) => {
      if (page === 2 && fail) { fail = false; throw new Error('temporary') }
      return { list: Array.from({ length: Math.min(size, Math.max(0, 83 - (page - 1) * size)) }, (_, i) => (page - 1) * size + i), total: 83 }
    })
    await expect(restoreClassPages(10, read, () => true)).rejects.toThrow('temporary')
    expect((await restoreClassPages(10, read, () => true))?.list).toEqual(Array.from({ length: 83 }, (_, i) => i))
    expect(read.mock.calls.map(([page]) => page)).toEqual([1, 2, 1, 2])
  })

  it('does not dispatch remaining pages after a scope change', async () => {
    const read = vi.fn(async () => ({ list: [1], total: 2000 }))
    expect(await restoreClassPages(100, read, () => false)).toBeUndefined()
    expect(read).toHaveBeenCalledTimes(1)
  })
})
