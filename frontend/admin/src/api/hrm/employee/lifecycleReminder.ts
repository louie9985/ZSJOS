import request from '@/config/axios'

export interface LifecycleReminderRule {
  enabled: boolean
  advanceDays: number
  triggerTime: string
  deptIds: number[]
  includeChildDepartments: boolean
  recipientUserIds: number[]
  recipientDeptIds: number[]
}
export interface LifecycleReminderConfig {
  regular: LifecycleReminderRule
  leave: LifecycleReminderRule
  change: LifecycleReminderRule
}
const path = '/hrm/employee-reminder/lifecycle-config'
export const getConfig = () => request.get<LifecycleReminderConfig>({ url: path })
export const saveConfig = (data: LifecycleReminderConfig) => request.put({ url: path, data })
export const previewConfig = (data: LifecycleReminderConfig) =>
  request.post<
    Record<keyof LifecycleReminderConfig, { recipientUserIds: number[]; pendingCount: number }>
  >({ url: `${path}/preview`, data })
