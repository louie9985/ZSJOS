import request from '@/config/axios'
import type { CollectionImage } from './registration/studentInfo'
import type { Archive } from './studentArchive'
export interface ExtraValue {
  text: string
  links: string[]
  attachmentIds: number[]
}
export interface ExtraField {
  key: string
  label: string
  type: string
  allowedExtensions: string[]
}
export interface Extra {
  revision: number
  canMaintain: boolean
  canReadAttachments: boolean
  fields: ExtraField[]
  values: Record<string, ExtraValue>
  attachments: CollectionImage[]
  history: Archive['history']
}
const base = (id: number) => `/zsjos/student/service/${id}/archive/extra`
export const detail = (id: number): Promise<Extra> => request.get({ url: base(id) })
export const save = (
  id: number,
  revision: number,
  changes: Record<string, ExtraValue>,
  reason: string,
  idempotencyKey: string
): Promise<number> =>
  request.post({ url: base(id), data: { revision, changes, reason, idempotencyKey } })
export const upload = (id: number, fieldKey: string, file: File): Promise<CollectionImage> => {
  const data = new FormData()
  data.append('fieldKey', fieldKey)
  data.append('file', file)
  return request.upload({ url: base(id) + '/attachments', data })
}
export const download = async (id: number, item: CollectionImage) => {
  const blob = await request.download<Blob>({ url: base(id) + '/attachments/' + item.id })
  if (blob.type.includes('json')) throw Error('附件读取失败或没有权限')
  const url = URL.createObjectURL(blob),
    a = document.createElement('a')
  a.href = url
  a.download = item.name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
