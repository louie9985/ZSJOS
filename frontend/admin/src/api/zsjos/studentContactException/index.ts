import request from '@/config/axios'

export interface StudentContactExtensionVO {
  id: number
  serviceRelationId: number
  taskId: number
  status: string
  originalDueAt: string
  requestedDueAt: string
  reasonValue: string
  reasonLabel?: string
  description: string
  attachmentFileIds: number[]
  applicantUserId: number
  reviewerUserId: number
  processInstanceId?: string
  decisionReason?: string
  submittedAt: string
  resolvedAt?: string
  version: number
}

export const getStudentContactExtensions = (pageNo: number, pageSize: number, statusScope: string) =>
  request.get<{ list: StudentContactExtensionVO[]; total: number }>({
    url: '/zsjos/student/service/extensions', params: { pageNo, pageSize, statusScope }
  })
