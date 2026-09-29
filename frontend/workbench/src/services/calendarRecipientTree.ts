import type { CalendarNotifyUser, PageResult, SimpleDept } from './api'

export type CalendarRecipientNode = {
  key: string; value: string; title: string; searchText: string;
  selectable: false; children?: CalendarRecipientNode[];
}

/** Department checkboxes must never operate on a partial employee page. */
export async function loadCalendarRecipients(
  fetchPage: (keyword?: string, pageNo?: number, pageSize?: number) => Promise<PageResult<CalendarNotifyUser>>,
  cancelled: () => boolean = () => false,
): Promise<CalendarNotifyUser[]> {
  const users: CalendarNotifyUser[] = []
  const ids = new Set<number>()
  let total: number | undefined
  for (let page = 1; ; page++) {
    if (cancelled()) return []
    const result = await fetchPage(undefined, page, 100)
    if (cancelled()) return []
    if (!Number.isSafeInteger(result.total) || result.total < 0 || (total !== undefined && result.total !== total)) {
      throw new Error('员工名单发生变化，请重试加载')
    }
    total = result.total
    for (const user of result.list) {
      if (ids.has(user.id)) throw new Error('员工名单发生变化，请重试加载')
      ids.add(user.id)
      users.push(user)
    }
    if (users.length === total) return users
    if (!result.list.length || users.length > total) throw new Error('员工名单加载不完整，请重试')
  }
}

/** Use System parent IDs and server order; keep employees with unavailable departments at root. */
export function buildCalendarRecipientTree(departments: SimpleDept[], users: CalendarNotifyUser[]): CalendarRecipientNode[] {
  const depts = new Map(departments.map(dept => [dept.id, dept]))
  const nodes = new Map<number, CalendarRecipientNode>()
  const roots: CalendarRecipientNode[] = []
  const ancestors = (id: number) => {
    const path: SimpleDept[] = []
    const seen = new Set<number>()
    let dept = depts.get(id)
    while (dept && !seen.has(dept.id)) {
      seen.add(dept.id); path.unshift(dept)
      dept = dept.parentId == null ? undefined : depts.get(dept.parentId)
    }
    return path
  }
  for (const dept of depts.values()) {
    nodes.set(dept.id, { key: 'dept:' + dept.id, value: 'dept:' + dept.id, title: dept.name,
      searchText: ancestors(dept.id).map(item => item.name).join(' / '), selectable: false, children: [] })
  }
  for (const dept of depts.values()) {
    const node = nodes.get(dept.id)!
    const parent = dept.parentId == null ? undefined : nodes.get(dept.parentId)
    const cycle = dept.parentId != null && ancestors(dept.parentId).some(item => item.id === dept.id)
    if (parent && !cycle) parent.children!.push(node)
    else roots.push(node)
  }
  const seen = new Set<number>()
  for (const user of users) {
    if (seen.has(user.id)) continue
    seen.add(user.id)
    const parent = user.deptId == null ? undefined : nodes.get(user.deptId)
    const node: CalendarRecipientNode = { key: 'user:' + user.id, value: 'user:' + user.id, title: user.nickname,
      searchText: (parent?.searchText ?? '') + ' ' + user.nickname, selectable: false }
    if (parent) parent.children!.push(node)
    else roots.push(node)
  }
  const prune = (items: CalendarRecipientNode[]): CalendarRecipientNode[] => items.flatMap(node => {
    if (!node.children) return [node]
    const children = prune(node.children)
    return children.length ? [{ ...node, children }] : []
  })
  return prune(roots)
}
