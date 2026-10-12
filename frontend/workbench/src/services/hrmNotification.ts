import { http, unwrap } from './api'
export async function hrmEmployeeNotificationTarget(id: number): Promise<string> {
  unwrap<{ id: number }>(await http.get('/hrm/employee/get', { params: { id } }))
  return `/hrm/employee/list?employeeId=${id}`
}
