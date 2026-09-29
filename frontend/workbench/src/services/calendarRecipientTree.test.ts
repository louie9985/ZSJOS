import { describe, expect, it, vi } from 'vitest'
import { buildCalendarRecipientTree, loadCalendarRecipients } from './calendarRecipientTree'

const departments = [{ id: 1, name: '教务部', parentId: 0 }, { id: 2, name: '一组', parentId: 1 },
  { id: 3, name: '教研部', parentId: 0 }, { id: 4, name: '空部门', parentId: 0 }]
const users = [{ id: 1, nickname: '员工甲', deptId: 1 }, { id: 2, nickname: '员工乙', deptId: 2 },
  { id: 3, nickname: '员工甲', deptId: 3 }, { id: 4, nickname: '员工丁', deptId: null }]

describe('calendar recipient department tree', () => {
  it('keeps System hierarchy, server order, distinct employee IDs and descendant search text', () => {
    const tree = buildCalendarRecipientTree(departments, users)
    expect(tree.map(node => node.value)).toEqual(['dept:1', 'dept:3', 'user:4'])
    expect(tree[0].children!.map(node => node.value)).toEqual(['dept:2', 'user:1'])
    expect(tree[0].children![0].children![0]).toMatchObject({ value: 'user:2', searchText: '教务部 / 一组 员工乙' })
    expect(tree[1].children![0].value).toBe('user:3')
  })
  it('retains orphan departments and employees without inventing organization relationships', () => {
    const tree = buildCalendarRecipientTree([{ id: 1, name: '部门', parentId: 99 }], [
      { id: 1, nickname: '甲', deptId: 1 }, { id: 2, nickname: '乙', deptId: 99 },
    ])
    expect(tree.map(node => node.value)).toEqual(['dept:1', 'user:2'])
  })
  it('handles cyclic metadata without losing employees or infinitely recursing', () => {
    const tree = buildCalendarRecipientTree([{ id: 1, name: '甲', parentId: 2 }, { id: 2, name: '乙', parentId: 1 }], users.slice(0, 2))
    expect(tree.map(node => node.value)).toEqual(['dept:1', 'dept:2'])
    expect(tree.flatMap(node => node.children!).map(node => node.value)).toEqual(['user:1', 'user:2'])
  })
  it('deduplicates employees and omits empty departments', () => {
    expect(buildCalendarRecipientTree(departments, [])).toEqual([])
    expect(buildCalendarRecipientTree([], [users[0], users[0]])).toHaveLength(1)
  })
})

describe('complete calendar candidate loading', () => {
  it('loads every page before returning the department-selectable roster', async () => {
    const fetch = vi.fn().mockResolvedValueOnce({ list: users.slice(0, 2), total: 4 })
      .mockResolvedValueOnce({ list: users.slice(2), total: 4 })
    expect(await loadCalendarRecipients(fetch)).toEqual(users)
    expect(fetch.mock.calls).toEqual([[undefined, 1, 100], [undefined, 2, 100]])
  })
  it('accepts an empty roster', async () => {
    expect(await loadCalendarRecipients(vi.fn().mockResolvedValue({ list: [], total: 0 }))).toEqual([])
  })
  it.each([
    { list: [], total: 4 },
    { list: [users[0]], total: 4 },
    { list: users.slice(2), total: 5 },
  ])('rejects incomplete, duplicate or changing pages', async second => {
    const fetch = vi.fn().mockResolvedValueOnce({ list: users.slice(0, 2), total: 4 }).mockResolvedValueOnce(second)
    await expect(loadCalendarRecipients(fetch)).rejects.toThrow(/重试/)
  })
  it('propagates permission and transport errors without returning a partial roster', async () => {
    const denied = new Error('没有发送通知权限')
    const fetch = vi.fn().mockResolvedValueOnce({ list: users.slice(0, 2), total: 4 }).mockRejectedValueOnce(denied)
    await expect(loadCalendarRecipients(fetch)).rejects.toBe(denied)
  })
  it('stops paging when the panel no longer needs candidates', async () => {
    let cancelled = false
    const fetch = vi.fn().mockImplementation(async () => { cancelled = true; return { list: users, total: 20 } })
    expect(await loadCalendarRecipients(fetch, () => cancelled)).toEqual([])
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})
