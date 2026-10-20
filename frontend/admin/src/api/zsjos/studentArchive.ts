import request from '@/config/axios'
import type { CollectionDetail, CollectionImage } from './registration/studentInfo'
export interface Archive {
  personId: number
  serviceRelationId: number
  registrationCaseId?: number
  orderItemId?: number
  productName?: string
  revision: number
  canMaintain: boolean
  information: CollectionDetail
  history: {
    revision: number
    modifiedAt: string
    operatorUserId: number
    operatorName?: string
    reason: string
  }[]
}
export interface Edit {
  revision: number
  values: Record<string, unknown>
  options: Record<string, { value: string; label: string }[]>
  images: CollectionImage[]
}
const base = (id: number) => `/zsjos/student/service/${id}/archive`
export const detail = (id: number, sensitive = false): Promise<Archive> =>
  request.get({ url: base(id) + (sensitive ? '/sensitive' : '') })
export const edit = (id: number): Promise<Edit> => request.get({ url: base(id) + '/edit' })
export const save = (
  id: number,
  revision: number,
  changes: Record<string, unknown>,
  reason: string,
  idempotencyKey: string
): Promise<number> =>
  request.post({ url: base(id), data: { revision, changes, reason, idempotencyKey } })
export const upload = (
  id: number,
  fieldKey: string,
  side: string,
  file: File
): Promise<CollectionImage> => {
  const data = new FormData()
  data.append('fieldKey', fieldKey)
  data.append('side', side)
  data.append('file', file)
  return request.upload({ url: base(id) + '/images', data })
}
export const image = async (id: number, item: CollectionImage) => {
  const blob = await request.download<Blob>({ url: base(id) + '/images/' + item.id })
  if (blob.type.includes('json')) throw Error('图片读取失败或没有权限')
  return URL.createObjectURL(new Blob([blob], { type: item.contentType }))
}
export const download = (id: number): Promise<Blob> =>
  request.download({ url: base(id) + '/export' })
