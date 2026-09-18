import request from '@/config/axios'

export interface StudentPartnerBinding {
  bound: boolean
  partnerNo?: string
  partnerName?: string
  startedAt?: number
}

export const getStudentPartnerBinding = (studentPersonId: number) =>
  request.get<StudentPartnerBinding>({
    preserveBusinessError: true,
    url: '/zsjos/partner-student-link/student',
    params: { studentPersonId }
  })

export const bindStudentPartner = (params: {
  partnerId: number
  studentPersonId: number
  reason?: string
}) =>
  request.post<boolean>({
    url: '/zsjos/partner-student-link/bind',
    params,
    preserveBusinessError: true
  })
