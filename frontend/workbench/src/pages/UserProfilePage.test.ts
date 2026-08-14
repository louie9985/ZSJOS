import { describe, expect, it } from 'vitest'
import { parseWecomCallback } from './UserProfilePage'

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
