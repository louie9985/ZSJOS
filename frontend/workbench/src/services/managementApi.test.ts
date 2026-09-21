import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { managementApi } from './managementApi'

describe('management API paging', () => {
  afterEach(() => vi.restoreAllMocks())

  it('passes independent page parameters to relations and logs', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: { code: 0, data: { list: [], total: 0 } }
    })

    await managementApi.relations('sales-manager', 3, 20)
    await managementApi.relationLogs('sales-manager', 4, 20)

    expect(get).toHaveBeenNthCalledWith(1, '/zsjos/user-relation/relation/page', {
      params: { sceneCode: 'sales-manager', pageNo: 3, pageSize: 20 }
    })
    expect(get).toHaveBeenNthCalledWith(2, '/zsjos/user-relation/log/page', {
      params: { sceneCode: 'sales-manager', pageNo: 4, pageSize: 20 }
    })
  })
  it('preserves all configured posts through save and detail response', async () => {
    const scene = { id: 7, name: '多岗位场景', code: 'multi_post', sourceLabel: '来源', targetLabel: '目标',
      sourcePostCodes: ['source_a', 'source_b'], targetPostCodes: ['target_a', 'target_b'],
      targetEligibilityType: 'post' as const, status: 0 }
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: { code: 0, data: true } })
    vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: scene } })
    await managementApi.updateRelationScene(scene)
    expect(put).toHaveBeenCalledWith('/zsjos/user-relation/scene/update', scene)
    expect(await managementApi.relationScene(7)).toEqual(scene)
  })

})
