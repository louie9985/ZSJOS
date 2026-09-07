import axios from 'axios'

export interface CollectionField {
  key: string; label: string; type: 'text' | 'textarea' | 'dict' | 'area'
  required: boolean; enabled: boolean; note?: string; sensitive: boolean
}
export interface CollectionRuntime {
  status: string; tenantId?: number; configVersion?: number; fields: CollectionField[]
  options: Record<string, { value: string; label: string }[]>
}
export interface CollectionArea { id: number; name: string; children?: CollectionArea[]; leafSelectable?: boolean }
const client = axios.create({ baseURL: '/public-api', timeout: 15000 })
export class CollectionError extends Error {
  constructor(public code: number, message: string) { super(message) }
}
const unwrap = <T>(response: { data: { code: number; data: T; msg: string } }): T => {
  if (response.data.code !== 0) throw new CollectionError(response.data.code, response.data.msg || '请求失败')
  return response.data.data
}
const headers = (token: string) => ({ 'X-Student-Info-Token': token, Authorization: undefined })
export const collectionApi = {
  detail: async (token: string) => unwrap<CollectionRuntime>(await client.get('/zsjos/student-info-form/detail', { headers: headers(token) })),
  submit: async (token: string, values: Record<string, string | number[]>) => unwrap<boolean>(await client.post('/zsjos/student-info-form/submit', { values }, { headers: headers(token) })),
  areas: async (tenantId: number) => unwrap<CollectionArea[]>(await axios.get('/app-api/system/area/tree', { headers: { 'tenant-id': tenantId, Authorization: undefined }, timeout: 15000 })),
}
