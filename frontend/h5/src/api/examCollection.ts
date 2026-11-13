import axios from 'axios'
import { CollectionError, type CollectionField, type CollectionImage } from './studentInfo'

export interface ExamCollectionRuntime { title: string; description: string; content?: string; status: 'OPEN' | 'COMPLETED'; fields: CollectionField[] }
export interface ExamCollectionSession { sessionToken: string; expiresAt: number }
export interface ExamSubmissionCommand { idempotencyKey: string; values: Record<string, string | string[] | number[]> }
const client = axios.create({ baseURL: '/public-api', timeout: 15000 })
client.interceptors.response.use(response => response, async error => {
  if (axios.isAxiosError(error)) {
    let payload: unknown = error.response?.data
    if (payload instanceof Blob && payload.type.includes('json')) {
      try { payload = JSON.parse(await payload.text()) } catch { payload = undefined }
    }
    if (payload && typeof payload === 'object' && 'code' in payload && typeof payload.code === 'number') {
      const message = 'msg' in payload && typeof payload.msg === 'string' ? payload.msg : '请求未完成，请重试'
      throw new CollectionError(payload.code, message)
    }
  }
  throw new Error('网络请求未完成，请检查网络后重试')
})
const base = '/zsjos/exam-collection'
const headers = (token: string, session?: string) => ({ 'X-Exam-Token': token, 'X-Exam-Session': session, Authorization: undefined })
const unwrap = <T>(response: { data: { code: number; data: T; msg?: string } }): T => {
  if (response.data.code !== 0) throw new CollectionError(response.data.code, response.data.msg || '请求失败，请重试')
  return response.data.data
}
const uploadTimeout = 10 * 60 * 1000
export const examCollectionApi = {
  detail: async (token: string) => unwrap<ExamCollectionRuntime>(await client.get(base + '/detail', { headers: headers(token) })),
  start: async (token: string) => unwrap<ExamCollectionSession>(await client.post(base + '/session', {}, { headers: headers(token) })),
  submit: async (token: string, session: string, command: ExamSubmissionCommand) => unwrap<boolean>(await client.post(base + '/submit', command, { headers: headers(token, session) })),
  upload: async (token: string, session: string, fieldKey: string, side: string, file: File, progress: (value: number) => void, requestKey: string) => {
    const body = new FormData(); body.append('fieldKey', fieldKey); body.append('side', side); body.append('file', file)
    return unwrap<CollectionImage>(await client.post(base + '/attachments', body, { headers: { ...headers(token, session), 'X-Exam-Upload-Key': requestKey }, timeout: uploadTimeout,
      onUploadProgress: event => progress(event.total ? Math.round(event.loaded * 100 / event.total) : 0) }))
  },
  remove: async (token: string, session: string, id: number) => unwrap<boolean>(await client.delete(`${base}/attachments/${id}`, { headers: headers(token, session) })),
  image: async (token: string, session: string, image: CollectionImage) => {
    const response = await client.get(`${base}/attachments/${image.id}`, { headers: headers(token, session), responseType: 'blob', timeout: uploadTimeout })
    if (response.data.type.includes('json')) {
      let failure: { code?: number; msg?: string }
      try { failure = JSON.parse(await response.data.text()) as { code?: number; msg?: string } }
      catch { throw new Error('材料暂不可用，请重试') }
      throw new CollectionError(typeof failure.code === 'number' ? failure.code : 500, failure.msg || '材料暂不可用，请重试')
    }
    return URL.createObjectURL(new Blob([response.data], { type: image.contentType }))
  },
}
