import request from '@/config/axios'

export interface NotifyChannelConfigVO {
  channelCode: string
  enabled: boolean
  configRef?: string
  maskedConfig?: string
  socialClientConfigured?: boolean
}

export const getNotifyChannelConfig = (channelCode = 'wecom'): Promise<NotifyChannelConfigVO | null> =>
  request.get({ url: '/system/notify-channel/get', params: { channelCode } })

export const updateNotifyChannelEnabled = (channelCode: string, enabled: boolean) =>
  request.put({ url: '/system/notify-channel/update', data: { channelCode, enabled } })
