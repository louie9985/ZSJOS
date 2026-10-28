import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { myOperationsApi } from './myOperations'

describe('my operations scoped API', () => {
  afterEach(() => vi.restoreAllMocks())
  it('keeps the selected operator in list and detail requests while forwarding filters and sorting', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { id: 9 } } })
    const params = { pageNo: 2, pageSize: 50, keyword: 'KZ', audience: 'management' as const,
      sortField: 'submittedAt' as const, sortOrder: 'descend' as const,
      advancedFilter: { logic: 'AND' as const, conditions: [{ fieldKey: 'lead.submittedName', operator: 'contains', value: '测试' }], groups: [] } }
    await myOperationsApi.leads(31, params)
    expect(post).toHaveBeenCalledWith('/zsjos/my-operations/31/leads/search-page', params)
    expect(await myOperationsApi.lead(31, 9)).toEqual({ id: 9 })
    expect(get).toHaveBeenCalledWith('/zsjos/my-operations/31/leads/9')
  })
  it('preserves server denied and relationship errors instead of treating them as empty teams', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 403, msg: '没有权限' } })
    await expect(myOperationsApi.members()).rejects.toThrow('没有权限')
  })
})
