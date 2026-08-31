import { describe, expect, it, vi } from 'vitest'
import type { WorkPlan } from './api'
import { loadWorkPlanPageResources } from './workPlanLoading'

const emptyPage = { list: [] as WorkPlan[], total: 0 }

const resourceApi = () => ({
  workPlanPage: vi.fn().mockResolvedValue(emptyPage),
  workPlanTemplates: vi.fn().mockResolvedValue([]),
  simpleUsers: vi.fn().mockResolvedValue([]),
  simpleDepartments: vi.fn().mockResolvedValue([])
})

describe('work plan page resource loading', () => {
  it('loads a read-only plan page without requesting create-only resources', async () => {
    const api = resourceApi()

    const result = await loadWorkPlanPageResources(api, 1, ['zsjos:work-plan:query'])

    expect(result.page).toBe(emptyPage)
    expect(api.workPlanPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 12 })
    expect(api.workPlanTemplates).not.toHaveBeenCalled()
    expect(api.simpleUsers).not.toHaveBeenCalled()
    expect(api.simpleDepartments).not.toHaveBeenCalled()
  })

  it('loads templates and people options for plan creators', async () => {
    const api = resourceApi()

    await loadWorkPlanPageResources(api, 2, ['zsjos:work-plan:query', 'zsjos:work-plan:create'])

    expect(api.workPlanTemplates).toHaveBeenCalledOnce()
    expect(api.simpleUsers).toHaveBeenCalledOnce()
    expect(api.simpleDepartments).toHaveBeenCalledOnce()
  })

  it('keeps the plan list when an optional resource fails', async () => {
    const api = resourceApi()
    api.workPlanTemplates.mockRejectedValue(new Error('forbidden'))

    const result = await loadWorkPlanPageResources(api, 1, ['zsjos:work-plan:query', 'zsjos:work-plan:create'])

    expect(result.page).toBe(emptyPage)
    expect(result.templates).toEqual([])
    expect(result.auxiliaryErrors).toEqual(['计划模板加载失败，相关操作暂不可用'])
  })

  it('still rejects when the plan list itself fails', async () => {
    const api = resourceApi()
    api.workPlanPage.mockRejectedValue(new Error('计划列表无权限'))

    await expect(loadWorkPlanPageResources(api, 1, ['zsjos:work-plan:query']))
      .rejects.toThrow('计划列表无权限')
  })
})
