import request from '@/config/axios'

export interface PersonnelStateVO {
  userId: number
  state: 'enabled' | 'disabled' | 'departed'
  reason?: string
  changedByUserId?: number
  changedAt?: string
}

export const getPersonnelState = (userId: number) =>
  request.get<PersonnelStateVO>({ url: `/zsjos/personnel/${userId}/state` })

export const updatePersonnelState = (
  userId: number,
  state: PersonnelStateVO['state'],
  reason: string
) => request.put({ url: `/zsjos/personnel/${userId}/state`, data: { state, reason } })
