import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { managementApi } from './managementApi'

describe('management API paging', () => {
  afterEach(() => vi.restoreAllMocks())

  it('posts finance filters with ordinary conditions, pagination and personal read scope', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    const advancedFilter = { logic: 'AND' as const, conditions: [{ fieldKey: 'cashback.amount', operator: 'gte', value: 100 }], groups: [] }
    const params = { pageNo: 2, pageSize: 10, status: 'available', keyword: 'CB', advancedFilter }
    await managementApi.cashbacks(true, params)
    expect(post).toHaveBeenLastCalledWith('/zsjos/cashback/my-search-page', params)
    await managementApi.cashbacks(false, params)
    expect(post).toHaveBeenLastCalledWith('/zsjos/cashback/search-page', params)
    const withdrawal = { ...params, readScope: 'USER' as const, targetUserId: 7,
      advancedFilter: { ...advancedFilter, conditions: [{ fieldKey: 'withdrawal.applicationAmount', operator: 'gte', value: 100 }] } }
    await managementApi.withdrawals(true, withdrawal)
    expect(post).toHaveBeenLastCalledWith('/zsjos/withdrawal/my-search-page', withdrawal)
    await managementApi.withdrawals(false, withdrawal)
    expect(post).toHaveBeenLastCalledWith('/zsjos/withdrawal/search-page', withdrawal)
    await managementApi.withdrawals(true, { pageNo: 1, pageSize: 10 })
    expect(get).toHaveBeenLastCalledWith('/zsjos/withdrawal/my-page', { params: { pageNo: 1, pageSize: 10 } })
  })

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
