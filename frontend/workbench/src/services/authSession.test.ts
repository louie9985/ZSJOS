import { describe, expect, it } from 'vitest'
import { isCurrentRefreshSession, readSharedTenantId, writeSharedTenantId } from './api'

describe('refresh session generation', () => {
  it('accepts a result only while the refresh token still identifies the same session', () => {
    expect(isCurrentRefreshSession('refresh-old', 'refresh-old')).toBe(true)
    expect(isCurrentRefreshSession('refresh-old', 'refresh-new')).toBe(false)
  })

  it('treats a newly created or cleared session as stale for an older refresh', () => {
    expect(isCurrentRefreshSession(null, 'refresh-new')).toBe(false)
    expect(isCurrentRefreshSession('refresh-old', null)).toBe(false)
  })
})

describe('shared tenant storage', () => {
  it('reads the Vue Admin web-storage-cache envelope', () => {
    const storage = { getItem: () => JSON.stringify({ c: Date.now(), e: 253402300799999, v: '8' }) }
    expect(readSharedTenantId(storage)).toBe('8')
  })

  it('rejects missing, expired, and non-positive tenant identifiers', () => {
    expect(readSharedTenantId({ getItem: () => null })).toBeUndefined()
    expect(readSharedTenantId({ getItem: () => JSON.stringify({ c: 1, e: 2, v: '8' }) })).toBeUndefined()
    expect(readSharedTenantId({ getItem: () => '0' })).toBeUndefined()
  })

  it('writes a value that Vue Admin can decode', () => {
    let stored = ''
    writeSharedTenantId('12', { setItem: (_key, value) => { stored = value } })
    const envelope = JSON.parse(stored) as { v: string }
    expect(JSON.parse(envelope.v)).toBe(12)
  })
})
