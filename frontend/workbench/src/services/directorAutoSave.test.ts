import { afterEach, describe, expect, it, vi } from 'vitest'
import { DirectorAutoSaveCoordinator, type DirectorAutoSaveState } from './directorAutoSave'

afterEach(() => vi.useRealTimers())

describe('DirectorAutoSaveCoordinator', () => {
  it('debounces and serializes the latest draft snapshots', async () => {
    vi.useFakeTimers()
    const calls: string[] = []
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => `key-${calls.length}`)
    coordinator.begin()
    coordinator.schedule(async () => { calls.push('first') })
    await vi.advanceTimersByTimeAsync(1000)
    coordinator.schedule(async () => { calls.push('second') })
    await vi.advanceTimersByTimeAsync(1499)
    expect(calls).toEqual([])
    await vi.advanceTimersByTimeAsync(1)
    expect(calls).toEqual(['second'])
  })

  it('reuses the same idempotency key when retrying a failed job', async () => {
    vi.useFakeTimers()
    const keys: string[] = []
    let attempts = 0
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'stable-key')
    coordinator.begin()
    coordinator.schedule(async key => {
      keys.push(key)
      if (attempts++ === 0) throw new Error('network')
    })
    await vi.advanceTimersByTimeAsync(1500)
    await coordinator.retry()
    expect(keys).toEqual(['stable-key', 'stable-key'])
  })

  it('ignores completion state from an invalidated session', async () => {
    let resolveSave: (() => void) | undefined
    const states: DirectorAutoSaveState[] = []
    const coordinator = new DirectorAutoSaveCoordinator(1500, state => states.push(state), () => 'key')
    coordinator.begin()
    const saving = coordinator.saveNow(() => new Promise<void>(resolve => { resolveSave = resolve }))
    coordinator.begin()
    resolveSave?.()
    await saving
    expect(states.at(-1)).toEqual({ status: 'idle' })
  })

  it('serializes a new session behind an in-flight stale request', async () => {
    let resolveOld: (() => void) | undefined
    const calls: string[] = []
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'key')
    coordinator.begin()
    const oldSave = coordinator.saveNow(() => new Promise<void>(resolve => {
      calls.push('old')
      resolveOld = resolve
    }))
    await vi.waitFor(() => expect(calls).toEqual(['old']))
    coordinator.begin()
    const newSave = coordinator.saveNow(async () => { calls.push('new') })

    expect(calls).toEqual(['old'])
    resolveOld?.()
    await Promise.all([oldSave, newSave])
    expect(calls).toEqual(['old', 'new'])
  })

  it('discards an unsent snapshot before formal submit', async () => {
    vi.useFakeTimers()
    const save = vi.fn(async () => undefined)
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'key')
    coordinator.begin()
    coordinator.schedule(save)
    await coordinator.prepareSubmit()
    await vi.runAllTimersAsync()
    expect(save).not.toHaveBeenCalled()
  })

  it('retries a failed sequence before saving a newer snapshot', async () => {
    vi.useFakeTimers()
    const calls: string[] = []
    let firstAttempt = true
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, (() => {
      let key = 0
      return () => `key-${++key}`
    })())
    coordinator.begin()
    coordinator.schedule(async key => {
      calls.push(`first:${key}`)
      if (firstAttempt) {
        firstAttempt = false
        throw new Error('response lost')
      }
    })
    await vi.advanceTimersByTimeAsync(1500)
    coordinator.schedule(async key => { calls.push(`second:${key}`) })
    await vi.advanceTimersByTimeAsync(1500)
    expect(calls).toEqual(['first:key-1'])

    await coordinator.retry()
    expect(calls).toEqual(['first:key-1', 'first:key-1', 'second:key-2'])
  })

  it('does not submit past an unresolved autosave failure', async () => {
    vi.useFakeTimers()
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'key')
    coordinator.begin()
    coordinator.schedule(async () => { throw new Error('network') })
    await vi.advanceTimersByTimeAsync(1500)
    await expect(coordinator.prepareSubmit()).rejects.toThrow('network')
  })

  it('does not replay a terminal version conflict for later input', async () => {
    vi.useFakeTimers()
    const calls: string[] = []
    const conflict = { code: 1900010024 }
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'key', error => error === conflict)
    coordinator.begin()
    coordinator.schedule(async () => { calls.push('stale'); throw conflict })
    await vi.advanceTimersByTimeAsync(1500)
    coordinator.schedule(async () => { calls.push('latest') })
    await vi.advanceTimersByTimeAsync(1500)
    expect(calls).toEqual(['stale'])
  })

  it('rejects explicit save and retry without replaying a terminal conflict', async () => {
    vi.useFakeTimers()
    const calls: string[] = []
    const conflict = { code: 1900014003 }
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'key', error => error === conflict)
    coordinator.begin()
    coordinator.schedule(async () => { calls.push('stale'); throw conflict })
    await vi.advanceTimersByTimeAsync(1500)

    await expect(coordinator.retry()).rejects.toBe(conflict)
    await expect(coordinator.saveNow(async () => { calls.push('explicit') })).rejects.toBe(conflict)
    expect(calls).toEqual(['stale'])
  })

  it('clears a terminal conflict when a new session begins', async () => {
    vi.useFakeTimers()
    const calls: string[] = []
    const conflict = { code: 1900010024 }
    const coordinator = new DirectorAutoSaveCoordinator(1500, () => undefined, () => 'key', error => error === conflict)
    coordinator.begin()
    coordinator.schedule(async () => { calls.push('stale'); throw conflict })
    await vi.advanceTimersByTimeAsync(1500)

    coordinator.begin()
    await coordinator.saveNow(async () => { calls.push('fresh') })
    expect(calls).toEqual(['stale', 'fresh'])
  })
})
