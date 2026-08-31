import { describe, expect, it } from 'vitest'
import { STORAGE_KEYS } from '../constants'
import {
  getStoredImpersonation,
  handleImpersonationInvalid,
  IMPERSONATION_SESSION_INVALID_CODE,
  resolveImpersonationSessionHeader
} from './impersonation'

class MemoryStorage implements Storage {
  private data = new Map<string, string>()
  get length() { return this.data.size }
  clear() { this.data.clear() }
  getItem(key: string) { return this.data.get(key) ?? null }
  key(index: number) { return Array.from(this.data.keys())[index] ?? null }
  removeItem(key: string) { this.data.delete(key) }
  setItem(key: string, value: string) { this.data.set(key, value) }
}

describe('impersonation request header', () => {
  const stored = JSON.stringify({ id: 42, administratorUserId: 7, administratorNameSnapshot: 'Admin', targetUserId: 8, targetNameSnapshot: 'Target', reason: 'Review', status: 'active', startedAt: '2026-08-16T00:00:00Z', lastActiveAt: '2026-08-16T00:00:00Z' })

  it('injects the session id into ordinary API requests', () => {
    expect(resolveImpersonationSessionHeader('/zsjos/lead/page', stored)).toBe(42)
    expect(resolveImpersonationSessionHeader('https://untrusted.example/zsjos/lead/page', stored, 'https://api.example')).toBeUndefined()
  })

  it('does not inject the session into impersonation lifecycle requests', () => {
    expect(resolveImpersonationSessionHeader('/zsjos/impersonation/42/end', stored)).toBeUndefined()
    expect(resolveImpersonationSessionHeader('/system/user/simple-list', stored)).toBeUndefined()
  })

  it('ignores absent, malformed, inactive, and invalid session storage', () => {
    expect(resolveImpersonationSessionHeader('/zsjos/lead/page', null)).toBeUndefined()
    expect(resolveImpersonationSessionHeader('/zsjos/lead/page', '{')).toBeUndefined()
    expect(resolveImpersonationSessionHeader('/zsjos/lead/page', JSON.stringify({ id: '42', status: 'active' }))).toBeUndefined()
    expect(resolveImpersonationSessionHeader('/zsjos/lead/page', JSON.stringify({ id: 42, status: 'expired' }))).toBeUndefined()
  })

  it('clears an invalid server session and emits one synchronization event', () => {
    const storage = new MemoryStorage()
    storage.setItem(STORAGE_KEYS.IMPERSONATION, stored)
    let events = 0
    const notify = () => events++

    expect(handleImpersonationInvalid(IMPERSONATION_SESSION_INVALID_CODE, 42, storage, notify)).toBe(true)
    expect(handleImpersonationInvalid(IMPERSONATION_SESSION_INVALID_CODE, 42, storage, notify)).toBe(false)
    expect(storage.getItem(STORAGE_KEYS.IMPERSONATION)).toBeNull()
    expect(events).toBe(1)
  })

  it('removes malformed and inactive stored sessions when read', () => {
    const storage = new MemoryStorage()
    storage.setItem(STORAGE_KEYS.IMPERSONATION, JSON.stringify({ id: 42, status: 'ended' }))
    expect(getStoredImpersonation(storage, () => undefined)).toBeUndefined()
    expect(storage.getItem(STORAGE_KEYS.IMPERSONATION)).toBeNull()
  })
})
