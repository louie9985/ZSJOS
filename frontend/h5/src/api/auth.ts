import request from './request'

export interface LoginParams {
  mobile: string
  password: string
  platform?: 'PC' | 'MOBILE'
}

export interface LoginResult {
  userId: number
  accessToken: string
  refreshToken: string
  expiresTime: string
  clientId: string
}

export interface PermissionInfo {
  user: {
    id: number
    nickname: string
    avatar?: string
  }
  roles: string[]
  permissions: string[]
}

/** 手机号密码登录 */
export function login(data: LoginParams) {
  return request.post<never, LoginResult>('/zsjos/auth/login', {
    ...data,
    platform: data.platform || 'MOBILE'
  })
}

/** 退出登录 */
export function logout() {
  return request.post<never, void>('/zsjos/auth/logout', undefined, { _skipAuthRefresh: true })
}

/** 获取权限信息 */
export function getPermissionInfo() {
  return request.get<never, PermissionInfo>('/zsjos/auth/permission-info')
}
