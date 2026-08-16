import request from '@/config/axios'

export interface WorkReportVO { id: number; revisionNo: number; completionSummary: string; submitterUserId: number; submittedAt: string; confirmationDecision?: string; confirmationComment?: string; confirmedByUserId?: number; confirmedAt?: string; infraFileIds?: number[]; reportFields?: Record<string, unknown> }
export interface WorkTaskVO { id: number; planId?: number; parentTaskId?: number; title: string; description?: string; deliverableRequirement?: string; assigneeUserId: number; assigneeDeptId?: number; assignerUserId: number; confirmerUserId?: number; confirmationRequired: boolean; dueAt?: string; status: string; version: number; taskFields?: Record<string, unknown>; reports?: WorkReportVO[]; availableActions?: string[] }
export interface WorkPlanSummaryVO { id: number; summary: string; submitterUserId: number; submittedAt: string; infraFileIds?: number[]; summaryFields?: Record<string, unknown> }
export interface WorkPlanChangeVO { id: number; subjectType: string; subjectId: number; changeType: string; reason: string; operatorUserId: number; changedAt: string; beforeSnapshot?: string; afterSnapshot?: string }
export interface WorkPlanVO { id: number; title: string; periodType: string; planTypeId: number; templateId: number; templateVersionId: number; ownerUserId: number; ownerDeptId?: number; objective?: string; keyRequirements?: string; startDate: string; endDate: string; status: string; summaryReady: boolean; version: number; planFields?: Record<string, unknown>; tasks?: WorkTaskVO[]; summary?: WorkPlanSummaryVO; changes?: WorkPlanChangeVO[] }
export interface WorkPlanPageReqVO extends PageParam { periodType?: string; status?: string; templateId?: number; ownerUserId?: number; ownerDeptId?: number; startDate?: string; endDate?: string }
export interface WorkPlanSearchReqVO extends WorkPlanPageReqVO { dynamicFilters?: Array<{ fieldKey: string; operator: string; value?: unknown; minValue?: unknown; maxValue?: unknown }> }

export const getWorkPlanPage = (params: WorkPlanPageReqVO) => request.get<PageResult<WorkPlanVO[]>>({ url: '/zsjos/work-plan/page', params })
export const searchWorkPlanPage = (data: WorkPlanSearchReqVO) => request.post<PageResult<WorkPlanVO[]>>({ url: '/zsjos/work-plan/search-page', data })
export const getWorkPlan = (id: number) => request.get<WorkPlanVO>({ url: '/zsjos/work-plan/get', params: { id } })
export const cancelWorkPlan = (id: number, version: number, reason: string) => request.post({ url: `/zsjos/work-plan/${id}/cancel`, data: { version, reason } })
export const cancelWorkTask = (id: number, version: number, reason: string, cascadeChildren = false) => request.post({ url: `/zsjos/work-plan/task/${id}/cancel`, data: { version, reason, cascadeChildren } })
export const adjustWorkTask = (task: WorkTaskVO, assigneeUserId: number, reason: string) => request.put({ url: `/zsjos/work-plan/task/${task.id}`, data: { title: task.title, description: task.description, deliverableRequirement: task.deliverableRequirement, assigneeUserId, dueAt: task.dueAt, confirmationRequired: task.confirmationRequired, confirmerUserId: task.confirmerUserId, taskFields: task.taskFields, version: task.version, reason } })
export const exportWorkPlans = (data: WorkPlanSearchReqVO) => request.postDownload<Blob>({ url: '/zsjos/work-plan/export-excel', data })
