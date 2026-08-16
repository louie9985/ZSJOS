import request from '@/config/axios'

export interface ExportTaskVO {
  id: number
  taskNo: string
  exportType: 'lead' | 'order' | 'finance_order' | 'cashback' | 'withdrawal'
  status: 'queued' | 'prechecking' | 'generating' | 'ready' | 'failed' | 'cancelled' | 'expired'
  attemptCount: number
  resultFileName?: string
  resultFileSize?: number
  readyAt?: string
  expiresAt?: string
  failureCode?: string
  failureMessage?: string
  createTime: string
}

export const getExportTaskPage = (params: PageParam & { exportType?: string }) =>
  request.get<PageResult<ExportTaskVO>>({ url: '/zsjos/export-task/page', params })

export const createExportTask = (exportType: string, filterJson = '{}') =>
  request.post<number>({ url: '/zsjos/export-task', data: { exportType, filterJson } })

export const cancelExportTask = (id: number) =>
  request.post({ url: `/zsjos/export-task/${id}/cancel` })

export const getExportDownloadUrl = (id: number) =>
  request.get<string>({ url: `/zsjos/export-task/${id}/download-url` })
