import { beforeEach, describe, expect, it } from 'vitest'
import {
  LEAD_INBOX_REFRESH_RETRY_DELAYS_MS,
  LEAD_INBOX_UNSEEN_EVENT,
  LEAD_INBOX_UNSEEN_LIMIT,
  LEAD_INBOX_UNSEEN_STORAGE_KEY,
  addUnseenLeadId,
  clearLeadUnseen,
  markLeadUnseen,
  parseUnseenLeadIds,
  removeUnseenLeadId,
  unseenLeadIds,
  type UnseenLeadDetail
} from './leadInboxUnseen'

class MemoryStorage implements Storage {
  private data = new Map<string, string>()

  get length() { return this.data.size }
  clear() { this.data.clear() }
  getItem(key: string) { return this.data.get(key) ?? null }
  key(index: number) { return Array.from(this.data.keys())[index] ?? null }
  removeItem(key: string) { this.data.delete(key) }
  setItem(key: string, value: string) { this.data.set(key, value) }
}

class UnavailableStorage extends MemoryStorage {
  override getItem(_key: string): string | null { throw new Error('storage unavailable') }
  override setItem(_key: string, _value: string): void { throw new Error('storage unavailable') }
}

describe('unseen lead id set', () => {
  it('reads nothing from absent or malformed storage', () => {
    expect(parseUnseenLeadIds(null)).toEqual([])
    expect(parseUnseenLeadIds('not json')).toEqual([])
    expect(parseUnseenLeadIds('{"id":1}')).toEqual([])
  })

  it('drops entries that cannot be a lead id', () => {
    expect(parseUnseenLeadIds('[1,"2",0,-3,1.5,null,1]')).toEqual([1, 2])
  })

  it('keeps one entry per lead without reordering the rest', () => {
    expect(addUnseenLeadId([1, 2], 2)).toEqual([1, 2])
    expect(addUnseenLeadId([1, 2], 3)).toEqual([1, 2, 3])
    expect(addUnseenLeadId([1, 2], 0)).toEqual([1, 2])
  })

  it('drops the oldest entries past the cap', () => {
    const full = Array.from({ length: LEAD_INBOX_UNSEEN_LIMIT }, (_, index) => index + 1)
    const next = addUnseenLeadId(full, 9_999)
    expect(next).toHaveLength(LEAD_INBOX_UNSEEN_LIMIT)
    expect(next[0]).toBe(2)
    expect(next.at(-1)).toBe(9_999)
  })

  it('removes a viewed lead', () => {
    expect(removeUnseenLeadId([1, 2, 3], 2)).toEqual([1, 3])
    expect(removeUnseenLeadId([1], 2)).toEqual([1])
  })
})

describe('unseen lead storage round trip', () => {
  let storage: Storage
  let target: EventTarget
  let announced: number[][]

  beforeEach(() => {
    storage = new MemoryStorage()
    target = new EventTarget()
    announced = []
    target.addEventListener(LEAD_INBOX_UNSEEN_EVENT, event =>
      announced.push((event as CustomEvent<UnseenLeadDetail>).detail.leadIds))
  })

  it('survives a page reload so the highlight is not lost', () => {
    markLeadUnseen(42, storage, target)
    expect(storage.getItem(LEAD_INBOX_UNSEEN_STORAGE_KEY)).toBe('[42]')
    expect(unseenLeadIds(storage)).toEqual([42])
  })

  it('clears the key entirely once the last lead is viewed', () => {
    markLeadUnseen(42, storage, target)
    clearLeadUnseen(42, storage, target)
    expect(storage.getItem(LEAD_INBOX_UNSEEN_STORAGE_KEY)).toBeNull()
    expect(unseenLeadIds(storage)).toEqual([])
  })

  it('announces changes so a mounted inbox updates without re-reading', () => {
    markLeadUnseen(7, storage, target)
    clearLeadUnseen(7, storage, target)
    expect(announced).toEqual([[7], []])
  })

  it('stays quiet when nothing actually changed', () => {
    markLeadUnseen(7, storage, target)
    // re-marking and clearing an absent lead must not fire, or selecting any
    // lead would re-render the whole inbox
    markLeadUnseen(7, storage, target)
    clearLeadUnseen(8, storage, target)
    expect(announced).toEqual([[7]])
  })

  it('still notifies the inbox when storage is unavailable', () => {
    markLeadUnseen(5, new UnavailableStorage(), target)
    expect(announced).toEqual([[5]])
  })

  it('retries the inbox refresh, since accept and list are separate requests', () => {
    expect(LEAD_INBOX_REFRESH_RETRY_DELAYS_MS).toEqual([0, 400, 1200])
  })
})
