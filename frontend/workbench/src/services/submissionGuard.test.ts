import { describe, expect, it, vi } from 'vitest'
import { createSubmissionGate } from './submissionGuard'

describe('createSubmissionGate', () => {
  it('blocks a concurrent second submission and keeps one intent key', async () => {
    const gate = createSubmissionGate()
    let release!: () => void
    const pending = new Promise<void>(resolve => { release = resolve })
    const task = vi.fn(async ({ idempotencyKey }: { idempotencyKey: string }) => {
      expect(idempotencyKey).toBe(gate.key)
      await pending
    })

    const first = gate.run(task)
    const second = await gate.run(task)
    release()
    await first

    expect(second).toBe(false)
    expect(task).toHaveBeenCalledTimes(1)
  })

  it('keeps the key after failure and rotates it only after completion', async () => {
    const gate = createSubmissionGate()
    const firstKey = gate.key

    await expect(gate.run(async ({ idempotencyKey }) => {
      expect(idempotencyKey).toBe(firstKey)
      throw new Error('network')
    })).rejects.toThrow('network')
    expect(gate.key).toBe(firstKey)

    await gate.run(async ({ idempotencyKey, complete }) => {
      expect(idempotencyKey).toBe(firstKey)
      complete()
    })
    expect(gate.key).not.toBe(firstKey)
  })
})
