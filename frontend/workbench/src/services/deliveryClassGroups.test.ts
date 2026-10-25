import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, AuthenticationError, http, type DeliveryClass } from './api'
import { classGroups, isClassGroupDenied } from './deliveryClassGroups'
afterEach(() => vi.restoreAllMocks())
describe('class group transport contracts', () => {
  it('clears unauthorized data while retaining retry for conflicts and transient failures', () => {
    expect(isClassGroupDenied(new ApiError(1091140002, '无权维护'))).toBe(true)
    expect(isClassGroupDenied(new ApiError(1900019007, '无权查看班级'))).toBe(true)
    expect(isClassGroupDenied(new AuthenticationError('登录失效'))).toBe(true)
    expect(isClassGroupDenied(new ApiError(1091140004, '版本冲突'))).toBe(false)
    expect(isClassGroupDenied(new Error('网络错误'))).toBe(false)
  })
  it('keeps personal grouping and child filters in the same read scope', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    const query = { pageNo: 2, pageSize: 12, keyword: 'BJ01', homeroomUserId: 8, status: 'COMPLETED', projectCategoryId: 3 }
    await classGroups.page(query, false)
    await classGroups.children('group:7', query, false)
    expect(get.mock.calls).toEqual([
      ['/zsjos/delivery-class/group/my-page', { params: query }],
      ['/zsjos/delivery-class/group/my-children', { params: { ...query, key: 'group:7' } }],
    ])
  })
  it('creates a parent and all children in one command', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: { groupId: 8, classIds: [9, 10] } } })
    const data = { className: '测试', startDate: '2026-12-01', homeroomUserIds: [1, 2], groupMode: 'NEW' as const, newGroup: { name: '父级', referenceDate: '2026-12-02' } }
    expect(await classGroups.createClasses(data)).toEqual({ groupId: 8, classIds: [9, 10] })
    expect(post).toHaveBeenCalledExactlyOnceWith('/zsjos/delivery-class/group/create-classes', data)
  })
  it('moves only identifiers and versions and explicitly clears membership', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: true } })
    await classGroups.move([{ id: 3, version: 4, homeroomUserId: 10, status: 'COMPLETED' } as DeliveryClass])
    expect(post).toHaveBeenCalledExactlyOnceWith('/zsjos/delivery-class/group/move', { classes: [{ id: 3, version: 4 }], targetGroupId: null })
  })
  it('preserves backend distinctions without issuing fallback writes', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 1091140004, msg: '班级已变化，请刷新' } })
    await expect(classGroups.move([{ id: 3, version: 0 } as DeliveryClass], 7)).rejects.toThrow('班级已变化，请刷新')
    expect(post).toHaveBeenCalledTimes(1)
  })
})
