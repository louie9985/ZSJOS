import { describe, expect, it } from 'vitest'
import { buildAttendanceProject, mergeRebuiltProjectWithReviewState } from './reviewBuilder'
import { employeeProfileKey, normalizeProjectRowKeys } from './employeeIdentity'
import { filterReviewEmployees, nextReviewIndex } from './reviewNavigation'
import { classifyDay } from './dayClassifier'
import { ATTENDANCE_STATUSES, type AttendanceMonth } from './models'

const options = { restDays: [], paidRestDays: [], shouldAttendanceDays: 21, payrollDays: 23 }
const month = (names = ['同名', '同名']): AttendanceMonth => ({
  year: 2026, month: 6, startDate: '2026-06-01', endDate: '2026-06-30', sourceFileName: '合成.xlsx', sourceFileHash: 'test',
  employees: names.map((name, index) => ({ name, account: '', rule: '', department: `部门${index}`, position: '', jobNo: '', summaries: {}, dayDetails: { 1: '正常' } })),
})

describe('attendance row identity and review navigation', () => {
  it('keeps duplicate names independently editable while preserving the original personnel lookup', () => {
    const project = buildAttendanceProject(month(), options, { 同名: { entry_date: '2026-01-01' } })
    const [first, second] = project.employees
    expect(first.key).not.toBe(second.key)
    expect(project.employees.map(employeeProfileKey)).toEqual(['同名', '同名'])
    expect(project.employees.map(employee => employee.entryDate)).toEqual(['2026-01-01', '2026-01-01'])
    const changed = project.employees.map(employee => employee.key === first.key ? { ...employee, displayName: '仅第一行修改' } : employee)
    expect(changed.map(employee => employee.displayName)).toEqual(['仅第一行修改', '同名'])
  })

  it('opens legacy duplicate-key drafts without changing their recorded values or personnel keys', () => {
    const project = buildAttendanceProject(month(), options)
    const legacy = { ...project, employees: project.employees.map((employee, index) => ({
      ...employee, key: '同名', profileKey: undefined, confirmed: index === 0,
      summary: { ...employee.summary, remark: `历史修订${index}` }, manualSummaryOverrides: { remark: true },
    })) }
    const restored = normalizeProjectRowKeys(legacy)
    expect(new Set(restored.employees.map(employee => employee.key)).size).toBe(2)
    expect(restored.employees.map(employeeProfileKey)).toEqual(['同名', '同名'])
    expect(restored.employees.map(employee => employee.summary.remark)).toEqual(['历史修订0', '历史修订1'])
    expect(restored.employees.map(employee => employee.confirmed)).toEqual([true, false])
    const rebuilt = mergeRebuiltProjectWithReviewState(buildAttendanceProject(month(), options), legacy)
    expect(rebuilt.employees.map(employee => employee.summary.remark)).toEqual(['历史修订0', '历史修订1'])
  })

  it('selects B after saving A in the unconfirmed filter instead of skipping to C', () => {
    const project = buildAttendanceProject(month(['A', 'B', 'C']), options)
    const before = filterReviewEmployees(project.employees, '', 'unconfirmed')
    const saved = project.employees.map((employee, index) => ({ ...employee, confirmed: index === 0 }))
    const after = filterReviewEmployees(saved, '', 'unconfirmed')
    expect(after[nextReviewIndex(before, after, before[0].key)].displayName).toBe('B')
    expect(nextReviewIndex([before[0]], [], before[0].key)).toBe(0)
  })

  it('keeps the next unconfirmed identity when the saved row remains in all employees', () => {
    const project = buildAttendanceProject(month(['A', 'B', 'C']), options)
    project.employees[1].confirmed = true
    const saved = project.employees.map((employee, index) => index === 0 ? { ...employee, confirmed: true } : employee)
    expect(saved[nextReviewIndex(project.employees, saved, project.employees[0].key)].displayName).toBe('C')
  })

  it.each(['陪产假', '产检假'])('retains the existing %s classifier output in the shared status contract', leave => {
    const result = classifyDay(`${leave}8小时`).result
    expect(result.morning).toBe(leave)
    expect(result.afternoon).toBe(leave)
    expect(ATTENDANCE_STATUSES).toContain(result.morning)
  })
})
