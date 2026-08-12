import request from '@/config/axios'

export interface WorkPlanTypeVO { id: number; code: string; name: string; description?: string; status: number; sort: number }
export interface WorkPlanTemplateFieldVO { id?: number; fieldKey?: string; label: string; section: 'plan' | 'task' | 'report' | 'summary'; fieldType: string; required?: boolean; unit?: string; placeholder?: string; filterable?: boolean; exportable?: boolean; optionsJson?: string; defaultValueJson?: string; sort?: number }
export interface WorkPlanTemplateTaskVO { title: string; description?: string; deliverableRequirement?: string; dueOffsetDays?: number; dueOffsetBasis?: string; confirmationRequired?: boolean; sort?: number }
export interface WorkPlanTemplateVO { id: number; typeId: number; code: string; name: string; description?: string; status: string; currentVersionNo: number; versionId?: number; versionStatus?: string; periodMode?: string; fields?: WorkPlanTemplateFieldVO[]; applicableDeptIds?: number[]; includeChildDepartments?: boolean; presetItems?: WorkPlanTemplateTaskVO[] }
export interface WorkPlanTypeSaveReqVO { name: string; description?: string; sort?: number }
export interface WorkPlanTemplateSaveReqVO { typeId: number; name: string; description?: string; periodMode: string; fields: WorkPlanTemplateFieldVO[]; applicableDeptIds?: number[]; includeChildDepartments?: boolean; presetItems?: WorkPlanTemplateTaskVO[] }
export const getTypes = () => request.get<WorkPlanTypeVO[]>({ url: '/zsjos/work-plan-config/types' })
export const createType = (data: WorkPlanTypeSaveReqVO) => request.post<number>({ url: '/zsjos/work-plan-config/types', data })
export const getTemplates = (typeId?: number) => request.get<WorkPlanTemplateVO[]>({ url: '/zsjos/work-plan-config/templates', params: { typeId } })
export const createTemplate = (data: WorkPlanTemplateSaveReqVO) => request.post<number>({ url: '/zsjos/work-plan-config/templates', data })
export const updateTemplate = (id: number, data: WorkPlanTemplateSaveReqVO) => request.put<boolean>({ url: `/zsjos/work-plan-config/templates/${id}`, data })
export const copyTemplateVersion = (id: number) => request.post<number>({ url: `/zsjos/work-plan-config/templates/${id}/versions/copy` })
export const publishTemplate = (id: number) => request.post<boolean>({ url: `/zsjos/work-plan-config/templates/${id}/publish` })
export const disableTemplate = (id: number) => request.post<boolean>({ url: `/zsjos/work-plan-config/templates/${id}/disable` })
