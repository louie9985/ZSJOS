import { useCache, CACHE_KEY } from '@/hooks/web/useCache'
import { TokenType } from '@/api/login/types'
import { decrypt, encrypt } from '@/utils/jsencrypt'

const { wsCache } = useCache()

const AccessTokenKey = 'ACCESS_TOKEN'
const RefreshTokenKey = 'REFRESH_TOKEN'
const ClientIdKey = 'CLIENT_ID'

// Workbench 与 Admin 同源共享的明文 localStorage 协议。旧版 wsCache 数据继续作为迁移回退。
const sharedStorage = localStorage
const legacyAccessTokenKey = 'zsjos_access_token'
const legacyRefreshTokenKey = 'zsjos_refresh_token'
const legacyClientIdKey = 'zsjos_client_id'

const readShared = (key: string, legacyKey: string) => {
  const value = sharedStorage.getItem(key)
  if (value) {
    // 旧版 Admin 用 web-storage-cache 把同名 key 写成 JSON 包装对象；读取后立即迁移成明文协议。
    const cachedValue = wsCache.get(key)
    if (typeof cachedValue === 'string' && cachedValue !== value) {
      sharedStorage.setItem(key, cachedValue)
      return cachedValue
    }
    return value
  }
  const legacyValue = sharedStorage.getItem(legacyKey)
  if (legacyValue) {
    sharedStorage.setItem(key, legacyValue)
    sharedStorage.removeItem(legacyKey)
    return legacyValue
  }
  return undefined
}

// 获取token
export const getAccessToken = () => {
  return (
    readShared(AccessTokenKey, legacyAccessTokenKey) ||
    wsCache.get(AccessTokenKey) ||
    wsCache.get('ACCESS_TOKEN')
  )
}

// 刷新token
export const getRefreshToken = () => {
  return readShared(RefreshTokenKey, legacyRefreshTokenKey) || wsCache.get(RefreshTokenKey)
}

export const getClientId = () =>
  readShared(ClientIdKey, legacyClientIdKey) || wsCache.get(ClientIdKey)

// 设置token
export const setToken = (token: TokenType) => {
  sharedStorage.setItem(RefreshTokenKey, token.refreshToken)
  sharedStorage.setItem(AccessTokenKey, token.accessToken)
  sharedStorage.setItem(ClientIdKey, token.clientId || 'zsjos-pc')
}

// 删除token
export const removeToken = () => {
  sharedStorage.removeItem(AccessTokenKey)
  sharedStorage.removeItem(RefreshTokenKey)
  sharedStorage.removeItem(ClientIdKey)
  sharedStorage.removeItem(legacyAccessTokenKey)
  sharedStorage.removeItem(legacyRefreshTokenKey)
  sharedStorage.removeItem(legacyClientIdKey)
  wsCache.delete(AccessTokenKey)
  wsCache.delete(RefreshTokenKey)
  wsCache.delete(ClientIdKey)
}

/** 格式化token（jwt格式） */
export const formatToken = (token: string): string => {
  return 'Bearer ' + token
}
// ========== 账号相关 ==========

/** 获取当前登录用户编号 */
export const getCurrentUserId = (): number => {
  const user = wsCache.get(CACHE_KEY.USER)?.user
  return Number(user?.id) || 0
}

export type LoginFormType = {
  tenantName: string
  username: string
  password: string
  rememberMe: boolean
}

export const getLoginForm = () => {
  const loginForm: LoginFormType = wsCache.get(CACHE_KEY.LoginForm)
  if (loginForm) {
    loginForm.password = decrypt(loginForm.password) as string
  }
  return loginForm
}

export const setLoginForm = (loginForm: LoginFormType) => {
  loginForm.password = encrypt(loginForm.password) as string
  wsCache.set(CACHE_KEY.LoginForm, loginForm, { exp: 30 * 24 * 60 * 60 })
}

export const removeLoginForm = () => {
  wsCache.delete(CACHE_KEY.LoginForm)
}

// ========== 租户相关 ==========

export const getTenantId = () => {
  return wsCache.get(CACHE_KEY.TenantId)
}

export const setTenantId = (tenantId: number) => {
  wsCache.set(CACHE_KEY.TenantId, tenantId)
}

export const getVisitTenantId = () => {
  return wsCache.get(CACHE_KEY.VisitTenantId)
}

export const setVisitTenantId = (visitTenantId: number) => {
  wsCache.set(CACHE_KEY.VisitTenantId, visitTenantId)
}
