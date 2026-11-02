import { recalculateSummary } from './summary'

import type { AttendanceProject, EmployeeReview } from './models'

export const employeeProfileKey = (employee: EmployeeReview): string => employee.profileKey ?? employee.key

export const sourceEmployeeRowKey = (profileKey: string, sourceIndex: number): string => `wechat:${sourceIndex}:${profileKey}`

/** Older drafts used the personnel lookup key as row identity; retain that lookup without merging rows. */
export function normalizeProjectRowKeys(project: AttendanceProject): AttendanceProject {
  project = { ...project, employees: project.employees.map(withPrenatalSummary) }
  if (project.sourceKind !== 'wechat-monthly') return project
  return {
    ...project,
    employees: project.employees.map(employee => {
      const profileKey = employeeProfileKey(employee)
      return { ...employee, profileKey, key: sourceEmployeeRowKey(profileKey, employee.sourceIndex) }
    }),
  }
}

/** Backfill only the newly introduced field; preserve all existing and manual totals. */
export function withPrenatalSummary(employee: EmployeeReview): EmployeeReview {
  if (employee.summary.prenatalLeaveDays !== undefined) return employee
  const calculated = recalculateSummary({ days: employee.days, sourceSummaries: employee.sourceSummaries || {},
    shouldAttendanceDays: employee.summary.shouldAttendanceDays, payrollDays: employee.summary.payrollDays,
    entryDate: employee.entryDate, workSchedule: employee.departmentSchedule?.schedule })
  return { ...employee, summary: { ...employee.summary, prenatalLeaveDays: calculated.prenatalLeaveDays } }
}
