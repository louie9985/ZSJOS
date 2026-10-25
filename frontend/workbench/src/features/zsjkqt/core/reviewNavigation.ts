import type { EmployeeReview } from './models'
import { employeeReviewIssueCount } from './reviewIssues'

export type EmployeeFilter = 'all' | 'issues' | 'unconfirmed' | 'disabled'

export function filterReviewEmployees(employees: EmployeeReview[], query: string, filter: EmployeeFilter): EmployeeReview[] {
  return employees.filter(employee => (!query || employee.displayName.includes(query) || employee.displayDepartment.includes(query))
    && (filter === 'all' || filter === 'issues' && employeeReviewIssueCount(employee) > 0
      || filter === 'unconfirmed' && !employee.confirmed || filter === 'disabled' && !employee.enabled))
}

/** Resolve the next row by identity after the saved row may have left the active filter. */
export function nextReviewIndex(before: EmployeeReview[], after: EmployeeReview[], savedKey: string): number {
  const current = before.findIndex(employee => employee.key === savedKey)
  const remaining = new Map(after.map((employee, index) => [employee.key, index]))
  const following = before.slice(current + 1).filter(employee => remaining.has(employee.key))
  const next = following.find(employee => !employee.confirmed) ?? following[0]
  return next ? remaining.get(next.key)! : Math.max(0, Math.min(current, after.length - 1))
}
