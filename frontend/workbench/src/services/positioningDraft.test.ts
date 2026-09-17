import { describe, expect, it, vi } from 'vitest'
import { loadPositioningDraft } from './positioningDraft'
import { serializePositioningFormValues } from './positioningJsonImport'
import type { PositioningCard, StudentContactFormField } from './api'

const draft = (id: number, serviceRelationId: number, accountId?: number): PositioningCard => ({
  id, serviceRelationId, accountId, cardNo: 'test', status: 'co_creating', version: 3,
  valuesSnapshot: { pc_homepage_douyin_refs: [1, 8] }, availableActions: [],
})
describe('reopening positioning drafts', () => {
  it('restores a null-account draft and preserves selected material versions through serialization', async () => {
    const load = vi.fn().mockResolvedValue({ ...draft(19, 30), accountId: null })
    const result = await loadPositioningDraft([{ id: 19, accountId: null }], undefined, 30, load)
    expect(result?.id).toBe(19)
    const field: StudentContactFormField = { key: 'pc_homepage_douyin_refs', title: '参考账号', type: 'material_picker', sort: 1, enabled: true, systemField: false, required: false }
    expect(serializePositioningFormValues(result!.valuesSnapshot!, [field])).toEqual({ pc_homepage_douyin_refs: [1, 8] })
  })
  it('selects only the current service relation when multiple unbound drafts exist', async () => {
    const load = vi.fn(async (id: number) => draft(id, id === 1 ? 20 : 30))
    expect((await loadPositioningDraft([{ id: 1, accountId: null }, { id: 2 }], undefined, 30, load))?.id).toBe(2)
  })
  it('does not substitute another account or service draft', async () => {
    const load = vi.fn(async (id: number) => draft(id, 20, 7))
    expect(await loadPositioningDraft([{ id: 1, accountId: 7 }], undefined, 30, load)).toBeUndefined()
    expect(load).not.toHaveBeenCalled()
    await expect(loadPositioningDraft([], 7, 30, load, 1)).rejects.toThrow('草稿与当前账号或课程服务不一致')
  })
})
