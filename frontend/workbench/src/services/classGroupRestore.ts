/** Restore a browsed range in larger pages, with at most two requests in flight. */
export function createClassReadQueue() {
  let active = 0
  const waiting: Array<() => void> = []
  return async function read<T>(operation: () => Promise<T>): Promise<T> {
    if (active >= 2) await new Promise<void>(resolve => waiting.push(resolve))
    else active++
    try { return await operation() }
    finally {
      const next = waiting.shift()
      if (next) next()
      else active--
    }
  }
}

export async function restoreClassPages<T, R extends { list: T[]; total: number }>(
  visiblePages: number,
  read: (pageNo: number, pageSize: number) => Promise<R>,
  isCurrent: () => boolean,
): Promise<R | undefined> {
  const pageSize = 60
  const requested = Math.max(1, Math.floor(visiblePages)) * 12
  const first = await read(1, pageSize)
  if (!isCurrent()) return undefined
  const count = Math.min(requested, first.total)
  const rows = [...first.list]
  for (let page = 2; (page - 1) * pageSize < count; page += 2) {
    if (!isCurrent()) return undefined
    const pages = [page, page + 1].filter(value => (value - 1) * pageSize < count)
    const batch = await Promise.all(pages.map(value => read(value, pageSize)))
    if (!isCurrent()) return undefined
    rows.push(...batch.flatMap(value => value.list))
  }
  return { ...first, list: rows.slice(0, count) }
}
