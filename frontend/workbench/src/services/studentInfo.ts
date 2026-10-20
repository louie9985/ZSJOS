import { http, unwrap } from './api'
import type { Timestamp } from './time'

export interface StudentInfoField {
  key: string; label: string; type: 'text' | 'textarea' | 'dict' | 'area' | 'images'
  allowedExtensions?: string[]; group: string; maxImages?: number; maxSizeMb?: number
  enabled: boolean; required: boolean; sort: number; note?: string; dictType?: string; sensitive: boolean
}
export interface StudentInfoDetail {
  id?: number; status: string; createdAt?: Timestamp; submittedAt?: Timestamp; expiresAt?: Timestamp
  configVersion?: number; fields: StudentInfoField[]; values: Record<string,string>
  canReadSensitive: boolean; canExport: boolean; images: StudentInfoImage[]
}
export interface StudentInfoLink {
  sentAt?: Timestamp; sentByUserId?: number; registrationVersion: number
  formId: number; status: string; url?: string; createdAt?: Timestamp; expiresAt?: Timestamp; canRegenerate: boolean; canRevoke: boolean
}
export interface StudentInfoImage { id: number; fieldKey: string; side: string; contentType: string; size: number; name: string }
export interface StudentInfoCommand { version: number; idempotencyKey: string; formId?: number; sent?: boolean }
const base = (caseId: number) => `/zsjos/registration/${caseId}/student-info-form`
export const studentInfoApi = {
  generate: async (id: number, command: StudentInfoCommand) => unwrap<StudentInfoLink>(await http.post(base(id), command)),
  link: async (id: number) => unwrap<StudentInfoLink | null>(await http.get(base(id) + '/link')),
  regenerate: async (id: number, command: StudentInfoCommand) => unwrap<StudentInfoLink>(await http.post(base(id) + '/regenerate', command)),
  revoke: async (id: number, command: StudentInfoCommand) => unwrap<StudentInfoLink>(await http.post(base(id) + '/revoke', command)),
  sent: async (id: number, command: StudentInfoCommand) => unwrap<StudentInfoLink>(await http.post(base(id) + '/sent', command)),
  image: async (id: number, image: StudentInfoImage) => {
    const response = await http.get(base(id) + '/images/' + image.id, { responseType: 'blob' })
    if ((response.data as Blob).type.includes('json')) throw new Error('图片读取失败或没有权限')
    return URL.createObjectURL(new Blob([response.data], { type: image.contentType }))
  },
  detail: async (id: number, sensitive = false) => unwrap<StudentInfoDetail>(await http.get(base(id) + (sensitive ? '/sensitive' : '/detail'))),
  export: async (id: number) => {
    const response = await http.get(base(id) + '/export', { responseType: 'blob' })
    const blob = response.data as Blob
    if (blob.type.includes('json')) {
      const error = JSON.parse(await blob.text()) as { msg?: string }
      throw new Error(error.msg || '导出失败')
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = '学员信息.xlsx'; a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
}
export const studentInfoStatus: Record<string,string> = {
  NONE: '尚未生成', DRAFT: '待填写', SUBMITTED: '已提交', EXPIRED: '已失效', REVOKED: '已撤销',
}
export const studentInfoError = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'
