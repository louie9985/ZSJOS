import request from './request'

export interface UserProfile {
  nickname: string
  mobile: string
  email?: string
  avatar?: string
  sex?: number
}

export interface PartnerInfo {
  id: number
  partnerNo: string
  name: string
  mobile: string
  status: 'enabled' | 'disabled' | 'converted'
  channelId?: string
  enabledAt: string
  disabledAt?: string | null
}

/** 获取账号资料 */
export function getProfile() {
  return request.get<never, UserProfile>('/zsjos/profile/get')
}

/** 修改账号资料 */
export function updateProfile(data: Partial<Omit<UserProfile, 'mobile'>>) {
  return request.put<never, void>('/zsjos/profile/update', data)
}

/** 修改密码 */
export function updatePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put<never, void>('/zsjos/profile/update-password', data)
}

/** 获取兼职主体信息 */
export function getPartnerMe() {
  return request.get<never, PartnerInfo>('/zsjos/partner/me')
}
