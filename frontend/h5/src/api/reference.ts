import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { getTenantId } from '@/utils/storage'
import { isMissingImplementation, type ApiResponse } from './request'

const referenceRequest = axios.create({
  // 字典、地区等公共参考数据仍由 MEMBER app-api 提供，不携带兼职 Token。
  baseURL: (import.meta.env.VITE_APP_REFERENCE_API || '/app-api') as string,
  timeout: 15000
})

referenceRequest.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers['tenant-id'] = getTenantId()
  delete config.headers.Authorization
  return config
})

referenceRequest.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    if (response.data.code === 0) {
      return response.data.data as unknown as AxiosResponse
    }
    if (isMissingImplementation(response.status, response.data.msg)) {
      return Promise.reject(new Error('后端接口暂未提供'))
    }
    return Promise.reject(new Error(response.data.msg || '参考数据加载失败'))
  },
  (error) => {
    if (isMissingImplementation(error.response?.status, error.response?.data?.msg)) {
      return Promise.reject(new Error('后端接口暂未提供'))
    }
    return Promise.reject(error)
  }
)

export default referenceRequest
