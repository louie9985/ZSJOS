import axios from 'axios'

// EAM anonymous endpoints are mounted under /public-api, not the Partner /part-api prefix.
const client = axios.create({ baseURL: '', timeout: 15000 })
client.interceptors.response.use((response) => {
  if (response.data?.code !== 0) return Promise.reject(new Error(response.data?.msg || '请求失败'))
  return response.data.data
})

export type OptionValue = string | number
export interface PublicField { key: string; label: string; value: unknown; type: string; editable: boolean; options: { value: OptionValue; label: string }[] }
export interface TreeOption { value: OptionValue; label: string; children?: TreeOption[] }
export interface EmployeeOption { value: number; label: string; deptId?: number }
export interface PublicAsset {
  version: number
  fields: PublicField[]
  editFields: Record<string, any>
  fileUrls?: string[]
  categoryTree: TreeOption[]
  departmentTree: TreeOption[]
  employeeOptions: EmployeeOption[]
}
export function getPublicAsset(assetCode: string) { return client.get<never, PublicAsset>('/public-api/eam/asset', { params: { assetCode } }) }
export function updatePublicAsset(assetCode: string, data: Record<string, unknown>, code: string) {
  return client.put<never, boolean>('/public-api/eam/asset', data, { params: { assetCode }, headers: { 'X-EAM-Edit-Code': code } })
}
export function clearPublicAssetUsage(assetCode: string, version: number, code: string) {
  return client.put<never, boolean>('/public-api/eam/asset/clear-usage', { version }, {
    params: { assetCode },
    headers: { 'X-EAM-Edit-Code': code }
  })
}
export function verifyPublicEditCode(assetCode: string, code: string) {
  return client.post<never, boolean>('/public-api/eam/asset/verify', null, { params: { assetCode }, headers: { 'X-EAM-Edit-Code': code } })
}
