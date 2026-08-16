import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { getToken, getTenantId, getRefreshToken } from '@/utils/storage'
import router from '@/router'

/** 统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  data: T
  msg: string
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

// 是否正在刷新 token
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, msg, data } = response.data

    if (code === 0) {
      return data as unknown as AxiosResponse
    }

    // 业务错误
    showToast({ message: msg || '操作失败', type: 'fail' })
    return Promise.reject(new Error(msg || '业务错误'))
  },
  async (error) => {
    const { response, config } = error

    // 401: Token 过期
    if (response?.status === 401) {
      const userStore = useUserStore()
      const refreshTokenValue = getRefreshToken()

      if (!refreshTokenValue) {
        userStore.logout()
        router.replace({ name: 'Login' })
        return Promise.reject(error)
      }

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const res = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
            `${import.meta.env.VITE_APP_BASE_API}/zsjos/auth/refresh-token`,
            null,
            { params: { refreshToken: refreshTokenValue } }
          )
          if (res.data.code === 0) {
            const { accessToken, refreshToken } = res.data.data
            userStore.setTokens(accessToken, refreshToken)
            onTokenRefreshed(accessToken)
            isRefreshing = false
            // 重试原请求
            config.headers.Authorization = `Bearer ${accessToken}`
            return request(config)
          }
        } catch {
          // refresh 也失败了
        }
        isRefreshing = false
        userStore.logout()
        router.replace({ name: 'Login' })
        return Promise.reject(error)
      }

      // 排队等待刷新完成
      return new Promise(resolve => {
        subscribeTokenRefresh((token) => {
          config.headers.Authorization = `Bearer ${token}`
          resolve(request(config))
        })
      })
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
