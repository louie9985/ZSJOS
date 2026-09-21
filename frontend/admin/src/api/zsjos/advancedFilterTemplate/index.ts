import request from '@/config/axios'
import type {
  AdvancedFilterScene,
  AdvancedFilterTemplate,
  AdvancedFilterTemplateSaveReq
} from '../advancedFilter'

// 供 `import * as TemplateApi` 的命名空间消费方引用模板类型
export type { AdvancedFilterScene, AdvancedFilterTemplate, AdvancedFilterTemplateSaveReq }

export const getSystemTemplateList = (scene: AdvancedFilterScene, pageKey: string) =>
  request.get<AdvancedFilterTemplate[]>({
    url: '/zsjos/advanced-filter-template/system-list',
    params: { scene, pageKey }
  })

// 业务页可用预置：服务端合并系统预置与当前用户的个人预置，按默认模板、排序、编号排列。
export const getVisibleTemplateList = (scene: AdvancedFilterScene, pageKey: string) =>
  request.get<AdvancedFilterTemplate[]>({
    url: '/zsjos/advanced-filter-template/visible-list',
    params: { scene, pageKey }
  })

export const createPersonalTemplate = (data: AdvancedFilterTemplateSaveReq) =>
  request.post<number>({ url: '/zsjos/advanced-filter-template/personal', data })

export const updatePersonalTemplate = (data: AdvancedFilterTemplateSaveReq) =>
  request.put<boolean>({ url: '/zsjos/advanced-filter-template/personal', data })

export const deletePersonalTemplate = (id: number) =>
  request.delete<boolean>({ url: '/zsjos/advanced-filter-template/personal', params: { id } })

export const createSystemTemplate = (data: AdvancedFilterTemplateSaveReq) =>
  request.post<number>({ url: '/zsjos/advanced-filter-template/system', data })

export const updateSystemTemplate = (data: AdvancedFilterTemplateSaveReq) =>
  request.put<boolean>({ url: '/zsjos/advanced-filter-template/system', data })

export const deleteSystemTemplate = (id: number) =>
  request.delete<boolean>({ url: '/zsjos/advanced-filter-template/system', params: { id } })
