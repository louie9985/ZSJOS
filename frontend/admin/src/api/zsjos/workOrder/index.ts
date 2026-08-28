import request from '@/config/axios'

export interface WorkOrderScene {
  id?: number
  code: string
  name: string
  remark?: string
  categoryValue?: string
  icon?: string
  sort?: number
  processorType?: string
  fields: Array<{
    key: string
    label: string
    type: string
    required?: boolean
    dictionaryType?: string
  }>
  allowedAssignmentTypes?: string[]
  sourceQualificationMode?: string
  sourceRoleIds?: number[]
  sourceDeptIds?: number[]
  targetQualificationMode?: string
  targetRoleIds?: number[]
  targetDeptIds?: number[]
  rejectionStrategy?: string
  numberPrefix?: string
  numberResetPeriod?: string
  numberSequenceWidth?: number
  status: number
  version?: number
  lifecycleStatus?: string
  publishedVersionNo?: number
  publishedAt?: string
}

export interface WorkOrderAudit {
  id: number
  orderNo: string
  sceneName?: string
  processorType?: string
  sourceName?: string
  targetName?: string
  status: string
  currentRound: number
  remark?: string
  completionRemark?: string
  fields?: WorkOrderScene['fields']
  values?: Record<string, unknown>
  requestAttachments?: Array<{ id: number; name: string; type?: string; size?: number }>
  createTime?: string
  timeline?: Array<{
    operation: string
    fromStatus?: string
    toStatus: string
    operatorName?: string
    reason?: string
    operatedAt?: string
    roundNo?: number
  }>
}

export const getWorkOrderScenePage = (params: {
  pageNo: number
  pageSize: number
  code?: string
  name?: string
}) => request.get({ url: '/zsjos/work-order/scene/page', params })
export const getWorkOrderScene = (code: string) =>
  request.get<WorkOrderScene>({ url: '/zsjos/work-order/scene/get', params: { code } })
export const createWorkOrderScene = (data: WorkOrderScene) =>
  request.post({ url: '/zsjos/work-order/scene/create', data })
export const updateWorkOrderScene = (data: WorkOrderScene) =>
  request.put({ url: '/zsjos/work-order/scene/update', data })
export const publishWorkOrderScene = (id: number, version: number) =>
  request.post({ url: '/zsjos/work-order/scene/publish', data: { id, version } })
export const validateWorkOrderScenePublish = (id: number) =>
  request.get<{ valid: boolean; numberPreview: string }>({
    url: '/zsjos/work-order/scene/publish-validation',
    params: { id }
  })
export const disableWorkOrderScene = (id: number, version: number) =>
  request.put({ url: '/zsjos/work-order/scene/disable', params: { id, version } })
export const getWorkOrderSceneVersions = (id: number) =>
  request.get<WorkOrderScene[]>({ url: '/zsjos/work-order/scene/versions', params: { id } })
export const getWorkOrderAuditPage = (params: {
  pageNo: number
  pageSize: number
  status?: string
}) => request.get({ url: '/zsjos/work-order/audit/page', params })
export const getWorkOrderAudit = (id: number) =>
  request.get<WorkOrderAudit>({ url: `/zsjos/work-order/audit/${id}` })
