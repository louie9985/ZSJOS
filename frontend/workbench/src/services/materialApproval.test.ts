import { describe, it, expect, vi, beforeEach } from 'vitest'
import { buildMenuTree, type RawMenu, http } from './api'
import { materialApprovalApi } from './materialApprovalApi'
import { filterRenderableMenus, findMenuByPath } from './menu'
import { APP_ROUTES, RENDERABLE_APP_ROUTES, MOBILE_RENDERABLE_APP_ROUTES } from '../constants'
describe('material approval contract', () => {
  beforeEach(() => vi.restoreAllMocks())
  it('retains the authorized nested route on desktop and mobile without inventing ungranted pages', () => {
    const menus = [{id:1,name:'工作台',path:'/zsjos',visible:true,children:[{id:2,name:'素材库',path:'material-library',visible:true,children:[{id:3,name:'素材审批',path:'approvals',visible:true}]}]}] as RawMenu[]
    for (const routes of [RENDERABLE_APP_ROUTES,MOBILE_RENDERABLE_APP_ROUTES]) {
      expect(findMenuByPath(filterRenderableMenus(buildMenuTree(menus),routes), APP_ROUTES.MATERIAL_APPROVALS)?.id).toBe(3)
      expect(filterRenderableMenus([],routes)).toEqual([])
    }
  })
  it('binds approval to the selected BPM task and submitted version', async () => {
    const post=vi.spyOn(http,'post').mockResolvedValue({data:{code:0,data:true}})
    await materialApprovalApi.decide('approve',3,'task-a','approved')
    expect(post).toHaveBeenCalledWith('/zsjos/material-approval/approve',{versionId:3,taskId:'task-a',reason:'approved'})
  })
  it('propagates authorization failures instead of reporting success', async () => {
    vi.spyOn(http,'post').mockRejectedValue(new Error('无权审批'))
    await expect(materialApprovalApi.decide('reject',3,'task-b','rejected')).rejects.toThrow('无权审批')
  })
})
