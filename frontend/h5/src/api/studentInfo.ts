import axios from 'axios'

export interface CollectionField {
  key: string; label: string; type: 'text' | 'textarea' | 'dict' | 'area' | 'images'
  allowedExtensions?: string[]; group: string; maxImages?: number; maxSizeMb?: number
  required: boolean; enabled: boolean; note?: string; sensitive: boolean
}
export interface CollectionRuntime {
  draft?: CollectionDraft
  productName?: string; images: CollectionImage[]
  status: string; tenantId?: number; configVersion?: number; fields: CollectionField[]
  options: Record<string, { value: string; label: string }[]>
}
export interface CollectionDraft { revision: number; values: Record<string,string>; savedAt?: string }
export interface CollectionImage { id: number; fieldKey: string; side: string; contentType: string; name: string; size: number }
export interface CollectionArea { id: number; name: string; selectionCode?: string; children?: CollectionArea[]; leafSelectable?: boolean }
const client = axios.create({ baseURL: '/public-api', timeout: 15000 })
const attachmentTimeout = 10 * 60 * 1000
export class CollectionError extends Error {
  constructor(public code: number, message: string) { super(message) }
}
const unwrap = <T>(response: { data: { code: number; data: T; msg: string } }): T => {
  if (response.data.code !== 0) throw new CollectionError(response.data.code, response.data.msg || '请求失败')
  return response.data.data
}
const headers = (token: string) => ({ 'X-Student-Info-Token': token, Authorization: undefined })
export const collectionApi = {
  saveDraft: async (token: string, revision: number, values: Record<string,string>) => unwrap<CollectionDraft>(await client.put('/zsjos/student-info-form/draft', { revision, values }, { headers: headers(token) })),
  upload: async (token: string, fieldKey: string, side: string, file: File, progress: (value: number) => void) => {
    const body = new FormData(); body.append('fieldKey', fieldKey); body.append('side', side); body.append('file', file)
    return unwrap<CollectionImage>(await client.post('/zsjos/student-info-form/images', body, { headers: headers(token), timeout: attachmentTimeout, onUploadProgress: e => progress(e.total ? Math.round(e.loaded * 100 / e.total) : 0) }))
  },
  removeImage: async (token: string, id: number) => unwrap<boolean>(await client.delete('/zsjos/student-info-form/images/' + id, { headers: headers(token) })),
  image: async (token: string, image: CollectionImage) => {
    const response = await client.get('/zsjos/student-info-form/images/' + image.id, { headers: headers(token), responseType: 'blob', timeout: attachmentTimeout })
    if ((response.data as Blob).type.includes('json')) throw new Error('图片不可用，请刷新后重试')
    return URL.createObjectURL(new Blob([response.data], { type: image.contentType }))
  },
  detail: async (token: string) => unwrap<CollectionRuntime>(await client.get('/zsjos/student-info-form/detail', { headers: headers(token) })),
  submit: async (token: string, values: Record<string, string | number[]>, revision: number) => unwrap<boolean>(await client.post('/zsjos/student-info-form/submit', { values, revision }, { headers: headers(token) })),
  areas: async (tenantId: number) => unwrap<CollectionArea[]>(await axios.get('/app-api/system/area/tree', { headers: { 'tenant-id': tenantId, Authorization: undefined }, timeout: 15000 })),
}
