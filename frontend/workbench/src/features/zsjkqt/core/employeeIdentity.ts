import type { AttendanceProject, EmployeeReview } from './models'

export const employeeProfileKey = (employee: EmployeeReview): string => employee.profileKey ?? employee.key

export const sourceEmployeeRowKey = (profileKey: string, sourceIndex: number): string => `wechat:${sourceIndex}:${profileKey}`

/** Older drafts used the personnel lookup key as row identity; retain that lookup without merging rows. */
export function normalizeProjectRowKeys(project: AttendanceProject): AttendanceProject {
  if (project.sourceKind !== 'wechat-monthly') return project
  return {
    ...project,
    employees: project.employees.map(employee => {
      const profileKey = employeeProfileKey(employee)
      return { ...employee, profileKey, key: sourceEmployeeRowKey(profileKey, employee.sourceIndex) }
    }),
  }
}
