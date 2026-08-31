import request from './request'

export interface LoginParams {
  mobile: string
  password: string
  platform?: 'PC' | 'MOBILE'
}

export interface WecomLoginParams {
  code: string
  state: string
  platform?: 'PC' | 'MOBILE'
}

export interface ActivateParams {
  mobile: string
  password: string
  confirmPassword: string
  inviteCode: string
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

/** 首次登录邀请码激活 */
export function activate(data: ActivateParams) {
  return request.post<never, LoginResult>('/zsjos/auth/activate', {
    ...data,
    platform: data.platform || 'MOBILE'
  })
}

/** 企业微信授权地址 */
export function wecomAuthorizeUrl(redirectUri: string) {
  return request.get<never, string>('/zsjos/auth/wecom-authorize-url', {
    params: { redirectUri }
  })
}

/** 企业微信登录 */
export function wecomLogin(data: WecomLoginParams) {
  return request.post<never, LoginResult>('/zsjos/auth/wecom-login', {
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
