import { beforeEach, describe, expect, it } from 'vitest'
import { STORAGE_KEYS } from '../constants'
import {
  clearLoginFormCache,
  loadLoginFormCache,
  LOGIN_FORM_CACHE_TTL_MS,
  saveLoginFormCache
} from './loginFormCache'

class MemoryStorage implements Storage {
  private data = new Map<string, string>()

  get length() { return this.data.size }
  clear() { this.data.clear() }
  getItem(key: string) { return this.data.get(key) ?? null }
  key(index: number) { return Array.from(this.data.keys())[index] ?? null }
  removeItem(key: string) { this.data.delete(key) }
  setItem(key: string, value: string) { this.data.set(key, value) }
}

class UnavailableStorage extends MemoryStorage {
  override getItem(_key: string): string | null { throw new Error('storage unavailable') }
  override removeItem(_key: string): void { throw new Error('storage unavailable') }
  override setItem(_key: string, _value: string): void { throw new Error('storage unavailable') }
}

describe('remembered login form cache', () => {
  let storage: Storage
  const now = Date.UTC(2026, 7, 9)

  beforeEach(() => {
    storage = new MemoryStorage()
  })

  it('returns no remembered form by default', () => {
    expect(loadLoginFormCache(storage, now)).toBeNull()
  })

  it('encrypts and restores a remembered login for 30 days', () => {
    saveLoginFormCache('employee', 'secret-password', storage, now)

    const raw = storage.getItem(STORAGE_KEYS.LOGIN_FORM)
    expect(raw).not.toContain('secret-password')
    expect(loadLoginFormCache(storage, now + LOGIN_FORM_CACHE_TTL_MS - 1))
      .toEqual({ username: 'employee', password: 'secret-password' })
  })

  it('removes an expired cache entry', () => {
    saveLoginFormCache('employee', 'secret-password', storage, now)

    expect(loadLoginFormCache(storage, now + LOGIN_FORM_CACHE_TTL_MS)).toBeNull()
    expect(storage.getItem(STORAGE_KEYS.LOGIN_FORM)).toBeNull()
  })

  it.each([
    ['malformed JSON', '{'],
    ['missing fields', JSON.stringify({ version: 1, username: 'employee' })],
    ['unsupported version', JSON.stringify({ version: 2, username: 'employee', encryptedPassword: 'value', expiresAt: now + 1 })],
    ['invalid ciphertext', JSON.stringify({ version: 1, username: 'employee', encryptedPassword: 'value', expiresAt: now + 1 })]
  ])('removes %s', (_label, value) => {
    storage.setItem(STORAGE_KEYS.LOGIN_FORM, value)

    expect(loadLoginFormCache(storage, now)).toBeNull()
    expect(storage.getItem(STORAGE_KEYS.LOGIN_FORM)).toBeNull()
  })

  it('clears a remembered login immediately', () => {
    saveLoginFormCache('employee', 'secret-password', storage, now)

    clearLoginFormCache(storage)

    expect(loadLoginFormCache(storage, now)).toBeNull()
  })

  it('degrades safely when browser storage is unavailable', () => {
    const unavailableStorage = new UnavailableStorage()

    expect(loadLoginFormCache(unavailableStorage, now)).toBeNull()
    expect(() => clearLoginFormCache(unavailableStorage)).not.toThrow()
    expect(() => saveLoginFormCache('employee', 'secret-password', unavailableStorage, now))
      .toThrow('storage unavailable')
  })
})
