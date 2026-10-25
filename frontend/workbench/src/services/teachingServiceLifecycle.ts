import { http, unwrap, type DeliveryClassOption } from './api'

export interface TeachingServiceEvent {
  action: 'close' | 'restore'; reason: string; operatorName?: string; occurredAt: string
  targetClassName?: string; targetOwnerName?: string
}
export interface TeachingServiceLifecycle {
  closed: boolean; previousClassId?: number; closedAt?: string
  availableActions: string[]; history: TeachingServiceEvent[]
}
export interface TeachingServiceCommand {
  reason: string; version: number; idempotencyKey: string; targetClassId?: number
}
const base = (id: number) => `/zsjos/student/service/${id}`
export const teachingServiceLifecycleApi = {
  detail: async (id: number) => unwrap<TeachingServiceLifecycle>(await http.get(`${base(id)}/lifecycle`)),
  targets: async (id: number) => unwrap<DeliveryClassOption[]>(await http.get(`${base(id)}/restore-targets`)),
  close: async (id: number, data: TeachingServiceCommand) => unwrap<boolean>(await http.post(`${base(id)}/close`, data)),
  restore: async (id: number, data: TeachingServiceCommand) => unwrap<boolean>(await http.post(`${base(id)}/restore`, data)),
}
