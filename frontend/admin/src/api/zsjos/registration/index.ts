import request from '@/config/axios'
import type { AdvancedFilterGroup } from '../advancedFilter'

export interface RegistrationChecklistItem {
  id: number
  itemKey: string
  itemType: string
  title: string
  sort: number
  checked: boolean
  checkedByUserId?: number
  checkedByUserName?: string
  checkedAt?: string
  attachmentRequired?: boolean
  attachments?: RegistrationAttachment[]
}

export interface RegistrationAttachment {
  id: number
  fileUrl: string
  originalName: string
  fileSize: number
  uploadedByUserName?: string
  uploadedAt?: string
}
export interface RegistrationRoute {
  id: number
  optionKey: string
  departmentId: number
  departmentName: string
  assigneeType: 'study_planner' | 'content_director'
  assigneeTypeLabel: string
  selected: boolean
  assigneeUserId?: number
  assigneeUserName?: string
  sort: number
}

export interface RegistrationCase {
  id: number
  orderId: number
  orderNo: string
  orderStatus: string
  orderStatusLabel?: string
  studentName: string
  studentMobile?: string
  leadNo?: string
  status: 'pending' | 'processing' | 'completed' | 'cancelled'
  statusLabel?: string
  studyPlannerUserId?: number
  studyPlannerUserName?: string
  registrationApprovedAt: string
  completedAt?: string
  version: number
  completable: boolean
  completionBlockCode?: string
  completionBlockReason?: string
  items: RegistrationChecklistItem[]
  routes: RegistrationRoute[]
}

export interface StudyPlanner {
  id: number
  nickname: string
}

export interface StudentService {
  serviceRelationId: number
  orderId: number
  orderNo: string
  orderItemId: number
  courseName?: string
  skuName?: string
  categoryPath?: string[]
  attributeValues?: string[]
  productSnapshot?: string
  status: string
  activatedAt: string
}

export interface MyStudent {
  personId: number
  leadNo?: string
  name: string
  mobile?: string
  wechatId?: string
  activatedAt: string
  services: StudentService[]
}

type VersionCommand = { version: number; idempotencyKey: string }

export const getRegistrationPoolPage = (
  params: PageParam & { status?: string; keyword?: string; advancedFilter?: AdvancedFilterGroup }
) => params.advancedFilter
  ? request.post<PageResult<RegistrationCase[]>>({ url: '/zsjos/registration/pool/search-page', data: params })
  : request.get<PageResult<RegistrationCase[]>>({ url: '/zsjos/registration/pool-page', params })

export const getRegistrationCase = (id: number) =>
  request.get<RegistrationCase>({ url: `/zsjos/registration/${id}` })

export const getStudyPlannerCandidates = () =>
  request.get<StudyPlanner[]>({ url: '/zsjos/registration/study-planner-candidates' })

export const getRouteCandidates = (id: number, routeId: number) =>
  request.get<StudyPlanner[]>({ url: `/zsjos/registration/${id}/routes/${routeId}/candidates` })

export const updateRegistrationRoutes = (
  id: number,
  routes: Array<{ routeId: number; selected: boolean; assigneeUserId?: number }>,
  version: number
) =>
  request.put<RegistrationCase>({
    url: `/zsjos/registration/${id}/routes`,
    data: { routes, version, idempotencyKey: crypto.randomUUID() }
  })

export const uploadRegistrationAttachment = (
  id: number,
  itemId: number,
  file: File,
  version: number
) => {
  const data = new FormData()
  data.append('file', file)
  return request.upload<{ version: number }>({
    url: `/zsjos/registration/${id}/items/${itemId}/attachments?version=${version}&idempotencyKey=${crypto.randomUUID()}`,
    data
  })
}

export const deleteRegistrationAttachment = (
  id: number,
  itemId: number,
  attachmentId: number,
  version: number
) =>
  request.delete<RegistrationCase>({
    url: `/zsjos/registration/${id}/items/${itemId}/attachments/${attachmentId}`,
    data: { version, idempotencyKey: crypto.randomUUID() }
  })

export const updateRegistrationItem = (
  id: number,
  itemId: number,
  checked: boolean,
  version: number
) =>
  request.put<RegistrationCase>({
    url: `/zsjos/registration/${id}/items/${itemId}`,
    data: { checked, version, idempotencyKey: crypto.randomUUID() }
  })

export const updateStudyPlanner = (id: number, studyPlannerUserId: number, version: number) =>
  request.put<RegistrationCase>({
    url: `/zsjos/registration/${id}/study-planner`,
    data: { studyPlannerUserId, version, idempotencyKey: crypto.randomUUID() }
  })

export const completeRegistration = (id: number, version: number) =>
  request.post<boolean>({
    url: `/zsjos/registration/${id}/complete`,
    data: { version, idempotencyKey: crypto.randomUUID() } satisfies VersionCommand
  })

export const getMyStudentPage = (params: PageParam & { keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
  params.advancedFilter
    ? request.post<PageResult<MyStudent[]>>({ url: '/zsjos/student/my/search-page', data: params })
    : request.get<PageResult<MyStudent[]>>({ url: '/zsjos/student/my-page', params })

export const getMyStudent = (personId: number) =>
  request.get<MyStudent>({ url: `/zsjos/student/my/${personId}` })
