import request from '@/config/axios'

export interface BirthdayCareConfig {
  enabled: boolean
  advanceDays: number
  triggerTime: string
  deptIds: number[]
  includeChildDepartments: boolean
  recipientUserIds?: number[]
  missingTaskPermissionUserIds?: number[]
}

export const getBirthdayCareConfig = () => request.get<BirthdayCareConfig>({ url: '/hrm/birthday-care/config' })
export const saveBirthdayCareConfig = (data: BirthdayCareConfig) =>
  request.put({ url: '/hrm/birthday-care/config', data })
