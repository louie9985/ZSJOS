import request from '@/config/axios'

export type NotifyChannelCode = 'in_app' | 'websocket' | 'wecom' | 'sms'

export interface NotifySceneVariableVO {
  key: string
  label: string
  sensitive: boolean
}

export interface NotifySceneRoleVO {
  code: string
  name: string
}

export interface NotifySceneVO {
  code: string
  name: string
  variables: NotifySceneVariableVO[]
  recipientRoles: NotifySceneRoleVO[]
  allowedActions: Array<'none' | 'message_detail' | 'business_detail'>
  timed?: boolean
}

export interface NotifyRuleVO {
  id?: number
  name: string
  sceneCode: string
  channelCode: NotifyChannelCode
  templateId?: number
  recipientRoles: string[]
  specifiedUserIds: number[]
  actionType: 'none' | 'message_detail' | 'business_detail'
  timingStage?: 'advance' | 'due' | 'overdue'
  timingOffsetMinutes?: number
  status: number
  createTime?: Date
}

export const getNotifySceneList = (): Promise<NotifySceneVO[]> =>
  request.get({ url: '/system/notify-scene/list' })
export const getNotifyRulePage = (params: PageParam) =>
  request.get({ url: '/system/notify-rule/page', params })
export const getNotifyRule = (id: number): Promise<NotifyRuleVO> =>
  request.get({ url: '/system/notify-rule/get', params: { id } })
export const createNotifyRule = (data: NotifyRuleVO) =>
  request.post({ url: '/system/notify-rule/create', data })
export const updateNotifyRule = (data: NotifyRuleVO) =>
  request.put({ url: '/system/notify-rule/update', data })
export const deleteNotifyRule = (id: number) =>
  request.delete({ url: '/system/notify-rule/delete', params: { id } })
export const updateNotifyRuleStatus = (id: number, status: number) =>
  request.put({ url: '/system/notify-rule/update-status', data: { id, status } })
