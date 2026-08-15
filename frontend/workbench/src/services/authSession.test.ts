import { describe, expect, it } from 'vitest'
import { isCurrentRefreshSession } from './api'

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
