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
})
