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
    expect(page).toContain('const leadId = service?.leadId;')
    expect(page).not.toContain('service?.leadId || student.leadId')
    expect(page).toContain('mode="student-readonly"')
    expect(page).toContain('<LeadDetail')
    expect(page).toContain("key: 'student-contact'")
    expect(page).toContain('<StudentDetail')
    expect(page).toContain('contactContext={studentContactContext}')
  })

  it('keeps student mode read-only', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    expect(detail).toContain("const readOnly = mode === 'student-readonly'")
    expect(detail).toContain("item.code.startsWith('SUPERVISOR_')")
    expect(detail).toContain('const actions = readOnly ? new Map')
    expect(detail).toContain("mode !== 'student-readonly' || tab !== 'follow-ups'")
  })

  it('uses service contact and order snapshots in the planner overview', () => {
    const overview = readFileSync('src/components/LeadDetailOverview.tsx', 'utf8')
    expect(overview).toContain('const service = studentContext?.service || studentService')
    expect(overview).toContain("service ? '成交产品' : '意向产品'")
    expect(overview).toContain('<LatestStudentContact records={studentContext.contactRecords} />')
    expect(overview).toContain("{ key: 'student_first_contact', label: '首联' }")
    expect(overview).toContain("{ key: 'student_study_plan', label: '制定学习计划' }")
    expect(overview).toContain("{ key: 'student_contact', label: '督学' }")
    expect(overview).toContain("{ key: 'student_exam', label: '考试' }")
  })

  it('renders planner commands only from the backend action projection', () => {
    const detail = readFileSync('src/components/LeadDetail.tsx', 'utf8')
    const page = readFileSync('src/pages/RegistrationPages.tsx', 'utf8')
    expect(detail).toContain('studentToolbarActions?: ToolbarAction[]')
    expect(page).toContain('available.includes("ACCEPT")')
    expect(page).toContain('["FIRST_CONTACT", "STUDY_PLAN", "FOLLOW_UP"]')
    expect(page).toContain('available.includes("EDIT_BASIC_INFO")')
    expect(page).toContain('available.includes("ASSIGN_CONTENT_DIRECTOR")')
    expect(page).toContain('available.includes("ASSIGN_CAREER_PLANNER")')
    expect(page.indexOf('key: "student-accept"')).toBeLessThan(page.indexOf('key: `student-${stageAction.toLowerCase()}`'))
    expect(page.indexOf('key: `student-${stageAction.toLowerCase()}`')).toBeLessThan(page.indexOf('key: "student-edit-basic-info"'))
    expect(page).not.toContain("availableActions?.includes('CONTACT')")
  })

  it('reloads the selected student after every successful planner command', () => {
    const page = readFileSync('src/pages/RegistrationPages.tsx', 'utf8')
    expect(page).toContain('const refreshCurrentStudent = useCallback(async () => {')
    expect(page).toContain('load(pageNo, { force: true, reloadDetail: false })')
    expect(page).toContain('loadStudent(selected.personId, selectedService?.serviceRelationId)')
    expect(page).toContain('onRefresh={refreshCurrentStudent}')
    expect(page.match(/await onRefresh\(\)/g)?.length).toBeGreaterThanOrEqual(4)
    expect(page).toContain('await refreshCurrentStudent();')
  })

  it('uses Person identity and keeps the contact history tab free of commands', () => {
    const page = readFileSync('src/pages/RegistrationPages.tsx', 'utf8')
    expect(page).toContain('submittedName: selected.name ?? leadDetail.submittedName')
    expect(page).toContain('submittedMobile: selected.mobile ?? leadDetail.submittedMobile')
    const historyDetail = page.slice(page.indexOf('function StudentContactDetail'), page.indexOf('export function StudentContactConfigPage'))
    expect(historyDetail).not.toContain('<StudentContactForm')
    expect(historyDetail).not.toContain('studentAssignCollaborator')
    expect(historyDetail).not.toContain('studentAccept')
  })
})
