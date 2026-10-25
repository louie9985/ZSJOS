import { http, unwrap, type DeliveryClassOption } from './api'

export const STUDENT_CREATE = 'zsjos:student:create'
export type EntryType = 'media' | 'teaching'
export type StudentEntry = { entryType: EntryType; name: string; mobile?: string; wechatId?: string; skuRef?: string; directorUserId?: number; teachingDelivery: boolean; classId?: number; reusePersonId?: number; confirmReuse: boolean; idempotencyKey: string }
export type EntryOptions = { directors: { id: number; name: string }[]; classes: DeliveryClassOption[]; courses: { skuRef: string; name: string }[] }
export type EntryCheck = { status: 'new' | 'reuse' | 'occupied' | 'conflict'; personId?: number; personNo?: string; name?: string; services: { serviceRelationId: number; studentName?: string; status: string; statusLabel: string }[] }
export type EntryResult = { personId: number; personNo: string; serviceRelationId: number; canRead: boolean }
const base = '/zsjos/student-entry'
export const studentEntry = {
  options: async () => unwrap<EntryOptions>(await http.get(`${base}/options`)),
  check: async (data: Pick<StudentEntry, 'mobile' | 'wechatId'>) => unwrap<EntryCheck>(await http.post(`${base}/check`, data)),
  create: async (data: StudentEntry) => unwrap<EntryResult>(await http.post(`${base}/create`, data)),
}
