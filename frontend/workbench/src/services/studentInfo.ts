import { http, unwrap } from './api'
import type { Timestamp } from './time'

export interface StudentInfoField {
  key: string; label: string; type: 'text' | 'textarea' | 'dict' | 'area'
  enabled: boolean; required: boolean; sort: number; note?: string; dictType?: string; sensitive: boolean
}
export interface StudentInfoDetail {
  id?: number; status: string; createdAt?: Timestamp; submittedAt?: Timestamp; expiresAt?: Timestamp
  configVersion?: number; fields: StudentInfoField[]; values: Record<string,string>
  canReadSensitive: boolean; canExport: boolean
}
export interface StudentInfoLink {
  formId: number; status: string; url?: string; createdAt?: Timestamp; expiresAt?: Timestamp; canRegenerate: boolean; canRevoke: boolean
}
const base = (leadId: number) => `/zsjos/lead/${leadId}/student-info-form`
export const studentInfoApi = {
  generate: async (id: number) => unwrap<StudentInfoLink>(await http.post(base(id))),
  link: async (id: number) => unwrap<StudentInfoLink | null>(await http.get(base(id) + '/link')),
  regenerate: async (id: number, formId: number) => unwrap<StudentInfoLink>(await http.post(base(id) + '/regenerate', { formId })),
  revoke: async (id: number, formId: number) => unwrap<boolean>(await http.post(base(id) + '/revoke', { formId })),
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
