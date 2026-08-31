import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { getToken, getTenantId, getRefreshToken, getClientId } from '@/utils/storage'
import router from '@/router'

declare module 'axios' {
  interface AxiosRequestConfig {
    _skipAuthRefresh?: boolean
  }
}

/** 统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  data: T
  msg: string
}

export function isMissingImplementation(status?: number, message?: unknown): boolean {
  if ([404, 405, 501].includes(status || 0)) return true
  const normalizedMessage = typeof message === 'string' ? message.toLowerCase() : ''
  return /接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在|not found|not implemented/.test(normalizedMessage)
}

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API as string,
  timeout: 15000
})

// 请求拦截器
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['tenant-id'] = getTenantId()
  return config
})

type RetryableConfig = InternalAxiosRequestConfig & {
  _authRetried?: boolean
  _skipAuthRefresh?: boolean
}
let refreshPromise: Promise<string> | null = null
let redirectingToLogin = false

function clearAuthentication(preserveRedirect = true) {
  useUserStore().logout()
  if (!redirectingToLogin) {
    redirectingToLogin = true
    const currentRoute = router.currentRoute.value
    const query = preserveRedirect && currentRoute.name !== 'Login'
      ? { redirect: currentRoute.fullPath }
      : undefined
    void router.replace({ name: 'Login', query }).finally(() => { redirectingToLogin = false })
  }
}

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    clearAuthentication()
    throw new Error('登录已失效')
  }
  refreshPromise = axios.post<ApiResponse<{
    accessToken: string
    refreshToken: string
    clientId?: string
  }>>(`${import.meta.env.VITE_APP_BASE_API}/zsjos/auth/refresh-token`, null, {
    params: { refreshToken, clientId: getClientId() },
    headers: { 'tenant-id': getTenantId() }
  }).then((response) => {
    if (response.data.code !== 0 || !response.data.data?.accessToken) {
      throw new Error(response.data.msg || '登录已失效')
    }
    const result = response.data.data
    useUserStore().setTokens(result.accessToken, result.refreshToken, result.clientId || getClientId())
    return result.accessToken
  }).catch((error) => {
    clearAuthentication()
    throw error
  }).finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function recoverAndReplay(config: RetryableConfig) {
  if (config._authRetried) {
    clearAuthentication()
    throw new Error('登录已失效')
  }
  config._authRetried = true
  const token = await refreshAccessToken()
  config.headers.Authorization = `Bearer ${token}`
  return request(config)
}

// 响应拦截器
request.interceptors.response.use(
  async (response: AxiosResponse<ApiResponse>) => {
    const { code, msg, data } = response.data

    if (code === 0) {
      return data as unknown as AxiosResponse
    }

    if (code === 401) {
      const config = response.config as RetryableConfig
      if (config._skipAuthRefresh) {
        clearAuthentication(false)
        return Promise.reject(new Error('登录已失效'))
      }
      return recoverAndReplay(config)
    }

    if (isMissingImplementation(response.status, msg)) {
      showToast({ message: '后端接口暂未提供', type: 'fail' })
      return Promise.reject(new Error('后端接口暂未提供'))
    }

    // 业务错误
    showToast({ message: msg || '操作失败', type: 'fail' })
    return Promise.reject(new Error(msg || '业务错误'))
  },
  async (error) => {
    const { response, config } = error

    if (isMissingImplementation(response?.status, response?.data?.msg)) {
      showToast({ message: '后端接口暂未提供', type: 'fail' })
      return Promise.reject(new Error('后端接口暂未提供'))
    }

    // 401: Token 过期
    if (response?.status === 401 && config) {
      const retryableConfig = config as RetryableConfig
      if (retryableConfig._skipAuthRefresh) {
        clearAuthentication(false)
        return Promise.reject(error)
      }
      return recoverAndReplay(retryableConfig)
    }

    // 403
    if (response?.status === 403) {
      showToast({ message: '没有操作权限', type: 'fail' })
      return Promise.reject(error)
    }

    // 网络错误
    const msg = response?.data?.msg || error.message || '网络异常'
    showToast({ message: msg, type: 'fail' })
    return Promise.reject(error)
  }
)

export default request
