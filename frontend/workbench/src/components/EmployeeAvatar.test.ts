import { describe, expect, it } from 'vitest'
import { getEmployeeAvatarCandidates, getNextEmployeeAvatarCandidate } from './EmployeeAvatar'

describe('getEmployeeAvatarCandidates', () => {
  it('prefers the personal avatar before the default avatar', () => {
    expect(getEmployeeAvatarCandidates('personal.png', 'default.png')).toEqual(['personal.png', 'default.png'])
  })

  it('uses only the default avatar when personal avatar is empty', () => {
    expect(getEmployeeAvatarCandidates(' ', 'default.png')).toEqual(['default.png'])
  })

  it('returns no image candidate when both avatars are empty', () => {
    expect(getEmployeeAvatarCandidates(undefined, '')).toEqual([])
  })

  it('does not retry the same URL twice', () => {
    expect(getEmployeeAvatarCandidates('same.png', 'same.png')).toEqual(['same.png'])
  })

  it('falls back from a broken personal avatar to the default avatar, then to the initial', () => {
    const candidates = getEmployeeAvatarCandidates('broken-personal.png', 'broken-default.png')

    expect(getNextEmployeeAvatarCandidate(candidates, 0)).toBe('broken-default.png')
    expect(getNextEmployeeAvatarCandidate(candidates, 1)).toBeUndefined()
  })
})
