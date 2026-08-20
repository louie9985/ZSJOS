import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { detailTabsFromProjection } from '../services/leadFollowUp'

describe('study planner student sales history', () => {
  it('shows only the history tabs projected by the server', () => {
    const tabs = detailTabsFromProjection(['overview', 'follow-ups', 'orders'])
    expect(tabs).toEqual(['overview', 'follow-ups', 'orders'])
    expect(tabs).not.toContain('appeals')
    expect(tabs).not.toContain('complaints')
  })

  it('reuses the complete Lead detail for an assigned student', () => {
    const page = readFileSync('src/pages/RegistrationPages.tsx', 'utf8')
    expect(page).toContain('student.leadId ? await api.managedLead(student.leadId) : undefined')
    expect(page).toContain('mode="student-readonly"')
    expect(page).toContain('<LeadDetail')
  })

  it('keeps student mode read-only', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    expect(detail).toContain("mode === 'manager-readonly' || mode === 'student-readonly'")
    expect(detail).toContain('const actions = readOnly ? new Map')
  })
})
