import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToString } from 'react-dom/server'
import { MetricPageScopeContext, useMetricSourceApi } from './metricPageScope'

const transport = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('./api', () => ({ http: transport, unwrap: (response: { data: unknown }) => response.data }))

function bind<T extends object>(source: string, original: T, contextSource = source): T {
  let bound = original
  function Harness() { bound = useMetricSourceApi(source, original); return null }
  renderToString(<MetricPageScopeContext.Provider value={{ contextId: 'frozen-token', operand: 'A', source: contextSource }}><Harness/></MetricPageScopeContext.Provider>)
  return bound
}

describe('metric business-page scope', () => {
  beforeEach(() => { vi.clearAllMocks(); transport.post.mockResolvedValue({ data: { list: [], total: 0 } }); transport.get.mockResolvedValue({ data: {} }) })
  it('keeps the opaque context when the business page clears filters or changes pages', async () => {
    const original = { allLeadPage: vi.fn(), updateLead: vi.fn() }
    const api = bind('lead', original)
    await api.allLeadPage({ pageNo: 2, pageSize: 20, advancedFilter: undefined })
    expect(transport.post).toHaveBeenCalledWith('/zsjos/home-metric/source-page', {
      contextId: 'frozen-token', operand: 'A', source: 'lead', cursor: false,
      query: { pageNo: 2, pageSize: 20, advancedFilter: undefined },
    })
    expect(original.allLeadPage).not.toHaveBeenCalled()
    expect(api.updateLead).toBe(original.updateLead)
  })
  it('does not apply an employee-list metric to a nested employee lead panel', () => {
    const original = { allLeadPage: vi.fn() }
    expect(bind('lead', original, 'subordinate_sales')).toBe(original)
  })
  it('retains cursor mode and the owning request shape', async () => {
    const api = bind('lead_appeal', { leadAppealInboxCursor: vi.fn() })
    await api.leadAppealInboxCursor(false, { cursor: 'next', limit: 20, keyword: 'search' })
    expect(transport.post).toHaveBeenCalledWith('/zsjos/home-metric/source-page', expect.objectContaining({ cursor: true, query: { cursor: 'next', limit: 20, keyword: 'search' } }))
  })
  it('stops a built-in task read when context authorization fails', async () => {
    transport.get.mockRejectedValue(new Error('expired'))
    const original = { bpmTaskPage: vi.fn() }
    await expect(bind('bpm_todo', original).bpmTaskPage('todo', { pageNo: 1 })).rejects.toThrow('expired')
    expect(original.bpmTaskPage).not.toHaveBeenCalled()
  })
  it('forces own pending tasks and rechecks context before returning rows', async () => {
    const original = { businessTaskList: vi.fn().mockResolvedValue({ list: [], total: 0 }) }
    await bind('business_todo', original).businessTaskList({ readScope: 'ALL', targetUserId: 99, bucket: 'today' })
    expect(original.businessTaskList).toHaveBeenCalledWith({ readScope: 'SELF', targetUserId: undefined, status: 'pending', bucket: 'today' })
    expect(transport.get).toHaveBeenCalledTimes(2)
  })
})
