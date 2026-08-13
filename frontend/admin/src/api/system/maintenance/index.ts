import request from '@/config/axios'

export interface MaintenanceModeVO {
  enabled: boolean
}

export const getMaintenanceMode = (): Promise<MaintenanceModeVO> =>
  request.get({ url: '/system/maintenance-mode' })

export const updateMaintenanceMode = (enabled: boolean) =>
  request.put({ url: '/system/maintenance-mode', data: { enabled } })
