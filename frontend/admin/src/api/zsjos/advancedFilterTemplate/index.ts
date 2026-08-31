import request from '@/config/axios'
import type {
  AdvancedFilterScene,
  AdvancedFilterTemplate,
  AdvancedFilterTemplateSaveReq
} from '../advancedFilter'

export const getSystemTemplateList = (scene: AdvancedFilterScene, pageKey: string) =>
  request.get<AdvancedFilterTemplate[]>({
    url: '/zsjos/advanced-filter-template/system-list',
    params: { scene, pageKey }
  })

export const createSystemTemplate = (data: AdvancedFilterTemplateSaveReq) =>
  request.post<number>({ url: '/zsjos/advanced-filter-template/system', data })

export const updateSystemTemplate = (data: AdvancedFilterTemplateSaveReq) =>
  request.put<boolean>({ url: '/zsjos/advanced-filter-template/system', data })

export const deleteSystemTemplate = (id: number) =>
  request.delete<boolean>({ url: '/zsjos/advanced-filter-template/system', params: { id } })
