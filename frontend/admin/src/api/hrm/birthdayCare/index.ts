import request from '@/config/axios'
export interface ReminderRule { enabled: boolean; advanceDays: number; triggerTime: string; deptIds: number[]; includeChildDepartments: boolean; recipientUserIds?: number[]; missingTaskPermissionUserIds?: number[] }
export interface EmployeeReminderConfig { birthday: ReminderRule; contractExpiry: ReminderRule; entryAnniversary: ReminderRule }
export const getEmployeeReminderConfig = () => request.get<EmployeeReminderConfig>({ url: '/hrm/employee-reminder/config' })
export const saveEmployeeReminderConfig = (data: EmployeeReminderConfig) => request.put({ url: '/hrm/employee-reminder/config', data })
export const getBirthdayCareConfig = getEmployeeReminderConfig
export const saveBirthdayCareConfig = saveEmployeeReminderConfig
