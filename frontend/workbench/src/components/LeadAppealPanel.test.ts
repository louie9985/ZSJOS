import { describe, expect, it } from 'vitest'
import { canSubmitLeadAppeal } from './LeadAppealPanel'

describe('current decision appeal entry', () => {
  it('uses server authorization and permits the first round of a new cycle', () => {
    expect(canSubmitLeadAppeal({ canSubmit: true, nextRoundNo: 1 })).toBe(true)
    expect(canSubmitLeadAppeal({ canSubmit: true, nextRoundNo: 3 })).toBe(true)
  })
  it('fails closed for missing, denied or finished context', () => {
    expect(canSubmitLeadAppeal()).toBe(false)
    expect(canSubmitLeadAppeal({ canSubmit: false, nextRoundNo: 1 })).toBe(false)
    expect(canSubmitLeadAppeal({ canSubmit: true, nextRoundNo: null })).toBe(false)
  })
})
