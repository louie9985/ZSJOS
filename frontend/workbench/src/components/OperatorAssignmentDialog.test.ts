import { describe, expect, it, vi } from 'vitest'
vi.mock('../services/api', () => ({ api: {}, ApiError: class extends Error {} }))
import { operatorReasonRequired } from './OperatorAssignmentDialog'

describe('operator assignment reason', () => {
  it('allows first assignment and unchanged assignment without a reason', () => {
    expect(operatorReasonRequired({}, 9)).toBe(false)
    expect(operatorReasonRequired({ operatorUserId: 9 }, 9)).toBe(false)
    expect(operatorReasonRequired({ operatorUserId: 9 }, undefined)).toBe(false)
  })
  it('requires a reason for ordinary replacement without an assignment conflict', () => {
    expect(operatorReasonRequired({ operatorUserId: 8, operatorAssignmentConflict: false }, 9)).toBe(true)
  })
  it('requires a reason when the server reports a conflict or another service needs correction', () => {
    expect(operatorReasonRequired({ operatorUserId: 9, operatorAssignmentConflict: true }, 9)).toBe(true)
    expect(operatorReasonRequired({}, 9, true)).toBe(true)
  })
})
