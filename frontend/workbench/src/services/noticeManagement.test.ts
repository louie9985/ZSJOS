import { describe, expect, it, vi, beforeEach } from 'vitest'
import { noticeActions, noticeManagement, noticeReadStatistics, noticeRecipientTree, noticeSelectedUserIds, noticeView, type NoticeInput } from './noticeManagement'
import { http } from './api'

vi.mock('./api', () => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  unwrap: (response: { data: unknown }) => response.data,
  api: { dictDataByType: vi.fn() }
}))
const perms = (...actions: string[]) => actions.map(action => `system:notice:${action}`)
describe('announcement authorization and lifecycle', () => {
  it.each([
    [perms('read'), null, false, 'mine'], [perms('query'), null, false, 'manage'],
    [perms('read', 'query'), null, false, 'mine'], [perms('read', 'query'), 'manage', false, 'manage'],
    [perms('read', 'query'), 'manage', true, 'mine'], [perms('query'), null, true, 'denied'],
    [perms('read'), 'manage', false, 'mine'], [[], null, false, 'denied']
  ] as const)('resolves authorized view %#', (permissions, requested, deepLink, expected) => {
    expect(noticeView([...permissions], requested, deepLink)).toBe(expected)
  })
  it('does not expose mutation for query-only or read-only accounts', () => {
    for (const permission of ['query', 'read']) expect(Object.values(noticeActions(perms(permission), 'DRAFT')).every(value => !value)).toBe(true)
  })
  it('limits operations by lifecycle and individual permissions', () => {
    expect(noticeActions(['*:*:*'], 'PUBLISHED')).toEqual({ edit: false, publish: false, delete: false, offline: true, copy: true })
    expect(noticeActions(['*:*:*'], 'OFFLINE')).toEqual({ edit: false, publish: false, delete: false, offline: false, copy: true })
    expect(noticeActions(perms('publish'), 'DRAFT')).toEqual({ edit: false, publish: true, delete: false, offline: false, copy: false })
  })
  it('preserves department hierarchy, ineligible users, and unassigned users', () => {
    const tree = noticeRecipientTree({ departments: [{ id: 1, parentId: 0, name: '部门' }, { id: 2, parentId: 1, name: '子部门' }], users: [
      { id: 3, nickname: '不可接收用户', deptId: 2, selectable: false }, { id: 4, nickname: '未分配用户', selectable: true }
    ] })
    expect(tree[0].children?.[0].children?.[0]).toMatchObject({ key: 'u:3', disabled: true })
    expect(tree[1]).toMatchObject({ checkable: false, children: [{ key: 'u:4', disabled: false }] })
  })
})
describe('notice recipient selection', () => {
  const options = {
    departments: [{ id: 10, parentId: 0, name: '中心' }, { id: 20, parentId: 10, name: '部门' }, { id: 30, parentId: 20, name: '小组' }],
    users: [{ id: 1, deptId: 10, nickname: '中心员工', selectable: true },
      { id: 2, deptId: 30, nickname: '下级员工', selectable: true },
      { id: 3, deptId: 20, nickname: '不可接收', selectable: false },
      { id: 4, nickname: '未分配员工', selectable: true }]
  }
  it('expands an old department draft through all levels and deduplicates explicit employees', () => {
    expect(noticeSelectedUserIds(options, [10, 20], [2, 4])).toEqual([2, 4, 1])
  })
  it('does not restore excluded employees from parent groups after saving explicit selection', () => {
    expect(noticeSelectedUserIds(options, [], [1, 4])).toEqual([1, 4])
    expect(noticeSelectedUserIds(options, [], [])).toEqual([])
  })
  it('keeps unavailable explicit selections for server validation and tolerates department cycles', () => {
    expect(noticeSelectedUserIds({ ...options, departments: [{ id: 10, parentId: 30, name: '中心' }, ...options.departments.slice(1)] }, [99], [404])).toEqual([404])
  })
})

describe('management API contract', () => {
  beforeEach(() => { vi.clearAllMocks(); for (const method of [http.get, http.post, http.put, http.delete]) vi.mocked(method).mockResolvedValue({ data: true }) })
  it('queries reading summary and paged people without changing read status', async () => {
    await noticeReadStatistics.summary(7)
    const query = { id: 7, scope: 'EXTRA' as const, pageNo: 2, pageSize: 20, name: '测试', deptId: 10 }
    await noticeReadStatistics.page(query)
    expect(http.get).toHaveBeenCalledWith('/system/notice/read-summary', { params: { id: 7 } })
    expect(http.get).toHaveBeenCalledWith('/system/notice/read-page', { params: query })
    expect(http.put).not.toHaveBeenCalled(); expect(http.post).not.toHaveBeenCalled()
  })
  it('fetches management details without writing read records', async () => {
    await noticeManagement.get(7)
    expect(http.get).toHaveBeenCalledWith('/system/notice/get', { params: { id: 7 } })
    expect(http.put).not.toHaveBeenCalled(); expect(http.post).not.toHaveBeenCalled()
  })
  it('preserves paging and state filters', async () => {
    await noticeManagement.page({ pageNo: 2, pageSize: 50, title: '公告', publishStatus: 'OFFLINE' })
    expect(http.get).toHaveBeenCalledWith('/system/notice/page', { params: { pageNo: 2, pageSize: 50, title: '公告', publishStatus: 'OFFLINE' } })
  })
  it('uploads attachment metadata through the notice endpoint and content through Infra', async () => {
    const file = new File(['synthetic'], 'notice.pdf', { type: 'application/pdf' })
    await noticeManagement.upload(file, () => {})
    const [path, body, options] = vi.mocked(http.post).mock.calls[0]
    expect(path).toBe('/system/notice/attachment/upload')
    expect((body as FormData).get('file')).toBeInstanceOf(File)
    expect(options?.onUploadProgress).toBeTypeOf('function')
    await noticeManagement.uploadContent(file, 'image')
    const [contentPath, contentBody] = vi.mocked(http.post).mock.calls[1]
    expect(contentPath).toBe('/infra/file/upload')
    expect((contentBody as FormData).get('directory')).toBe('system-notice-content-image')
  })
  it('uses existing command methods with internal ids and unchanged content', async () => {
    const data: NoticeInput = { title: '测试', type: 2, content: '<p><strong>正文</strong></p>', audienceType: 'TARGET', targetDeptIds: [1], targetUserIds: [2], attachments: [] }
    await noticeManagement.create(data); await noticeManagement.update({ ...data, id: 7 })
    await noticeManagement.publish(7); await noticeManagement.offline(7); await noticeManagement.copy(7); await noticeManagement.delete(7)
    expect(http.post).toHaveBeenCalledWith('/system/notice/create', data)
    expect(http.put).toHaveBeenCalledWith('/system/notice/update', { ...data, id: 7 })
    for (const action of ['publish', 'offline', 'copy']) expect(http.post).toHaveBeenCalledWith(`/system/notice/${action}`, undefined, { params: { id: 7 } })
    expect(http.delete).toHaveBeenCalledWith('/system/notice/delete', { params: { id: 7 } })
  })
})
