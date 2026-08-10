import { describe, expect, it } from 'vitest'
import { addFollowUpDays, appendQuickNote, defaultLeadDetailTab, shouldBlockLeadSwitch } from './leadFollowUp'

describe('lead follow-up form logic', () => {
  it('adds quick notes without overwriting existing remarks', () => {
    expect(appendQuickNote('', '稍后联系')).toBe('稍后联系')
    expect(appendQuickNote('客户在开会', '稍后联系')).toBe('客户在开会 稍后联系')
  })

  it('calculates quick follow-up days from the current local time', () => {
    expect(addFollowUpDays(new Date(2026, 7, 9, 10, 30), 3))
      .toEqual(new Date(2026, 7, 12, 10, 30))
  })

  it('blocks lead switching only while the form is dirty', () => {
    expect(shouldBlockLeadSwitch(true)).toBe(true)
    expect(shouldBlockLeadSwitch(false)).toBe(false)
  })

  it('opens customer details on overview unless a follow-up task requested the form', () => {
    expect(defaultLeadDetailTab(false)).toBe('overview')
    expect(defaultLeadDetailTab(true)).toBe('follow-ups')
  })
})
