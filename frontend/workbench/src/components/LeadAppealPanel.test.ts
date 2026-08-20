import { describe, expect, it } from 'vitest'
import type { LeadAppeal, ManagedLead } from '../services/api'
import { canSubmitLeadAppeal } from './LeadAppealPanel'

const lead = (relationTypes: ManagedLead['relationTypes'], status = 'invalid') => ({ relationTypes, status })
const appeal = (roundNo: number, status: LeadAppeal['status']) => ({ roundNo, status })

describe('canSubmitLeadAppeal', () => {
  it('allows a submitter to start the first appeal in the unified Lead view', () => {
    expect(canSubmitLeadAppeal(lead(['submitter']))).toBe(true)
  })

  it('allows the next round only after an upheld decision and before round three', () => {
    expect(canSubmitLeadAppeal(lead(['submitter']), appeal(1, 'upheld'))).toBe(true)
    expect(canSubmitLeadAppeal(lead(['submitter']), appeal(1, 'sales_manager_reviewing'))).toBe(false)
    expect(canSubmitLeadAppeal(lead(['submitter']), appeal(3, 'upheld'))).toBe(false)
  })

  it('rejects non-submitters and Leads that are not invalid', () => {
    expect(canSubmitLeadAppeal(lead(['owner']))).toBe(false)
    expect(canSubmitLeadAppeal(lead(['submitter'], 'valid'))).toBe(false)
  })
})
