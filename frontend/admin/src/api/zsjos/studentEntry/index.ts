import request from '@/config/axios'
import type { DeliveryClassOption } from '@/api/zsjos/deliveryClass'

export type EntryType = 'media' | 'teaching'
export interface EntryRequest {
  entryType: EntryType
  name: string
  mobile?: string
  wechatId?: string
  skuRef?: string
  directorUserId?: number
  teachingDelivery: boolean
  classId?: number
  reusePersonId?: number
  confirmReuse: boolean
  idempotencyKey: string
}
export interface EntryOptions {
  directors: { id: number; name: string }[]
  classes: DeliveryClassOption[]
  courses: { skuRef: string; name: string }[]
}
export interface EntryCheck {
  status: 'new' | 'reuse' | 'occupied' | 'conflict'
  personId?: number
  personNo?: string
  name?: string
  services: {
    serviceRelationId: number
    studentName?: string
    status: string
    statusLabel: string
  }[]
}
export interface EntryResult {
  personId: number
  personNo: string
  serviceRelationId: number
  canRead: boolean
}
const base = '/zsjos/student-entry'
export const options = (): Promise<EntryOptions> => request.get({ url: `${base}/options` })
export const check = (data: Pick<EntryRequest, 'mobile' | 'wechatId'>): Promise<EntryCheck> =>
  request.post({ url: `${base}/check`, data })
export const create = (data: EntryRequest): Promise<EntryResult> =>
  request.post({ url: `${base}/create`, data, preserveBusinessError: true })
