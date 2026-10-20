import request from '@/config/axios'
import type { Field } from '../studentInfoFormConfig'
export interface CollectionLink {
  formId: number
  status: string
  url?: string
  registrationVersion: number
  sentAt?: string
  canRegenerate: boolean
  canRevoke: boolean
}
export interface CollectionImage {
  id: number
  fieldKey: string
  side: string
  contentType: string
  name: string
}
export interface CollectionDetail {
  status: string
  fields: Field[]
  values: Record<string, string>
  images: CollectionImage[]
  canReadSensitive: boolean
  canExport: boolean
}
export interface CollectionCommand {
  version: number
  formId?: number
  sent?: boolean
  idempotencyKey: string
}
const base = (id: number) => '/zsjos/registration/' + id + '/student-info-form'
export const link = (id: number): Promise<CollectionLink | null> =>
  request.get({ url: base(id) + '/link' })
export const command = (
  id: number,
  operation: string,
  data: CollectionCommand
): Promise<CollectionLink> =>
  request.post({ url: base(id) + (operation ? '/' + operation : ''), data })
export const detail = (id: number, sensitive = false): Promise<CollectionDetail> =>
  request.get({ url: base(id) + (sensitive ? '/sensitive' : '/detail') })
export const image = async (id: number, item: CollectionImage) => {
  const blob = await request.download<Blob>({ url: base(id) + '/images/' + item.id })
  if (blob.type.includes('json')) throw new Error('图片读取失败或没有权限')
  return URL.createObjectURL(new Blob([blob], { type: item.contentType }))
}
export const download = (id: number): Promise<Blob> =>
  request.download({ url: base(id) + '/export' })
