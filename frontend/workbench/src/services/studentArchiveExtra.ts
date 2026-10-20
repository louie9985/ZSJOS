import { http, unwrap } from './api'
import type { StudentInfoImage } from './studentInfo'
import type { StudentArchive } from './studentArchive'
export interface ArchiveExtraValue { text: string; links: string[]; attachmentIds: number[] }
export interface ArchiveExtraField { key: string; label: string; type: string; allowedExtensions: string[] }
export interface ArchiveExtra {
  revision: number; visibilityRevision: number; canMaintain: boolean; canReadAttachments: boolean; canConfigureVisibility: boolean
  fields: ArchiveExtraField[]; values: Record<string, ArchiveExtraValue>
  educationVisibility: Record<string, boolean>
  attachments: StudentInfoImage[]; history: StudentArchive['history']
}
const base = (id: number) => `/zsjos/student/service/${id}/archive/extra`
export const studentArchiveExtraApi = {
  detail: async (id: number) => unwrap<ArchiveExtra>(await http.get(base(id))),
  save: async (id: number, revision: number, changes: Record<string, ArchiveExtraValue>, reason: string, idempotencyKey: string) =>
    unwrap<number>(await http.post(base(id), { revision, changes, reason, idempotencyKey })),
  saveVisibility: async (id: number, revision: number, visibility: Record<string, boolean>) =>
    unwrap<number>(await http.post(base(id) + '/visibility', { revision, visibility })),
  upload: async (id: number, fieldKey: string, file: File) => {
    const body = new FormData(); body.append('fieldKey', fieldKey); body.append('file', file)
    return unwrap<StudentInfoImage>(await http.post(base(id) + '/attachments', body))
  },
  download: async (id: number, item: StudentInfoImage) => {
    const response = await http.get(base(id) + '/attachments/' + item.id, { responseType: 'blob' })
    const blob = response.data as Blob
    if (blob.type.includes('json')) throw Error('附件读取失败或没有权限')
    const url = URL.createObjectURL(blob), a = document.createElement('a')
    a.href = url; a.download = item.name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
  resource: async (id: number, item: StudentInfoImage) => {
    const response = await http.get(base(id) + '/attachments/' + item.id, { responseType: 'blob' })
    const blob = response.data as Blob
    if (blob.type.includes('json')) throw Error('附件读取失败或没有权限')
    return { name: item.name, type: item.contentType, size: item.size, url: URL.createObjectURL(blob) }
  },
}
