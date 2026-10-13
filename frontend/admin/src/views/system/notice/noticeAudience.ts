import type { NoticeRecipientOptionsVO } from '@/api/system/notice'

// Expand department-based drafts for editing without changing published recipient snapshots.
export function noticeSelectedUserIds(options: NoticeRecipientOptionsVO, deptIds: number[], userIds: number[]): number[] {
  const selected = new Set(userIds)
  const departments = new Map(options.departments.map(dept => [dept.id, dept.parentId]))
  const scopes = new Set(deptIds)
  for (const user of options.users) {
    if (!user.selectable) continue
    let deptId = user.deptId
    const visited = new Set<number>()
    while (deptId != null && !visited.has(deptId)) {
      if (scopes.has(deptId)) { selected.add(user.id); break }
      visited.add(deptId)
      deptId = departments.get(deptId)
    }
  }
  return [...selected]
}
