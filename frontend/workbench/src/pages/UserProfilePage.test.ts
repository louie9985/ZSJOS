import { describe, expect, it } from 'vitest'
import { isLatestAvatarUpload, parseWecomCallback } from './UserProfilePage'

describe('WeCom OAuth callback parsing', () => {
  it('accepts only WeCom callbacks with code and state', () => {
    expect(parseWecomCallback('?type=30&code=abc&state=xyz')).toEqual({
      type: 30, code: 'abc', state: 'xyz', hasValidSocialCallback: true
    })
  })

  it('rejects missing state without allowing a bind request', () => {
    expect(parseWecomCallback('?type=30&code=abc')).toMatchObject({ type: 30, code: 'abc', state: '', hasValidSocialCallback: false })
  })

  it('rejects callbacks for other social types', () => {
    expect(parseWecomCallback('?type=20&code=abc&state=xyz').hasValidSocialCallback).toBe(false)
  })
})

describe('avatar upload sequencing', () => {
  it('accepts only the latest upload result', () => {
    expect(isLatestAvatarUpload(2, 2)).toBe(true)
    expect(isLatestAvatarUpload(1, 2)).toBe(false)
  })
})
