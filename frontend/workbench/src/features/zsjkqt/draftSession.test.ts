import { describe, expect, it, vi } from 'vitest'
import { copyDraftName, ZsjkqtDraftSession } from './draftSession'
import type { ZsjkqtDraft, ZsjkqtSave, ZsjkqtSnapshot } from '../../services/zsjkqt'
const snapshot = (value: number) => ({ schemaVersion: 1, month: { year: 2026, month: 6 }, payrollDays: value }) as ZsjkqtSnapshot
const row = (save: ZsjkqtSave, version = 0) => ({ id: 7, ...save, version }) as ZsjkqtDraft
describe('shared attendance draft persistence', () => {
  it('reserves room for the copy suffix without changing the source name or splitting Unicode characters', () => {
    for (const length of [116, 117, 120]) {
      const original = '名'.repeat(length)
      const copy = copyDraftName(original)
      expect(copy.length).toBeLessThanOrEqual(120)
      expect(copy.endsWith('（副本）')).toBe(true)
      expect(original.length).toBe(length)
    }
    expect(copyDraftName('考勤')).toBe('考勤（副本）')
    expect(copyDraftName('名'.repeat(115) + '😀')).toBe('名'.repeat(115) + '（副本）')
  })
  it('retains edits made while saving and advances the version without concurrent writes', async () => {
    let finish!: (value: ZsjkqtDraft) => void
    const writer = vi.fn<(id: number | undefined, data: ZsjkqtSave) => Promise<ZsjkqtDraft>>()
      .mockImplementationOnce((_, s) => new Promise(resolve => { finish = resolve }))
      .mockImplementation(async (_, s) => row(s, s.version + 1))
    const session = new ZsjkqtDraftSession(writer, undefined, 'test')
    session.update(snapshot(20)); const first = session.save();session.update(snapshot(21))
    finish(row(writer.mock.calls[0][1]));await first
    expect(writer).toHaveBeenCalledTimes(2);expect(writer.mock.calls[1][0]).toBe(7);expect(writer.mock.calls[1][1].snapshot.payrollDays).toBe(21);expect(session.dirty).toBe(false)
  })
  it('keeps local changes after conflict and does not change the version on failure', async () => {
    const old = row({ clientId:'test', name:'共享', version:2, snapshot:snapshot(20) }, 2)
    const session = new ZsjkqtDraftSession(vi.fn().mockRejectedValue(new Error('冲突')), old)
    session.update(snapshot(22));await expect(session.save()).rejects.toThrow('冲突');expect(session.dirty).toBe(true);expect(session.latest?.payrollDays).toBe(22);expect(session.row?.version).toBe(2)
  })
  it('deduplicates simultaneous saves and retries creation with the same client identifier', async () => {
    const writer=vi.fn().mockRejectedValueOnce(new Error('网络')).mockImplementation(async (_id: unknown,s:ZsjkqtSave)=>row(s))
    const session=new ZsjkqtDraftSession(writer,undefined,'stable');session.update(snapshot(20));await expect(session.save()).rejects.toThrow();await Promise.all([session.save(),session.save()]);expect(writer).toHaveBeenCalledTimes(2);expect(writer.mock.calls.map(c=>c[1].clientId)).toEqual(['stable','stable'])
  })
})
