import request from '@/config/axios'
import type { DeliveryClassOption } from '@/api/zsjos/deliveryClass'

export interface TeachingLifecycle {
  closed: boolean
  previousClassId?: number
  closedAt?: string
  availableActions: string[]
  history: { action: string; reason: string; operatorName?: string; occurredAt: string; targetClassName?: string; targetOwnerName?: string }[]
}
export interface TeachingCommand { reason: string; version: number; idempotencyKey: string; targetClassId?: number }
const base = (id: number) => `/zsjos/student/service/${id}`
export const teachingLifecycleApi = {
  detail: (id: number) => request.get<TeachingLifecycle>({ preserveBusinessError: true, url: `${base(id)}/lifecycle` }),
  targets: (id: number) => request.get<DeliveryClassOption[]>({ preserveBusinessError: true, url: `${base(id)}/restore-targets` }),
  close: (id: number, data: TeachingCommand) => request.post({ preserveBusinessError: true, url: `${base(id)}/close`, data }),
  restore: (id: number, data: TeachingCommand) => request.post({ preserveBusinessError: true, url: `${base(id)}/restore`, data })
}
