/**
 * 新到客资的「未读」标记。
 *
 * 接单成功后客资才进入销售的客资列表，此时列表项需要一个视觉提示，
 * 让销售看出哪条是刚接下的单；点进去看过一次即恢复常态。
 *
 * 标记落在 localStorage：接单入口（LeadAssignmentHost）全局挂载，客资列表页
 * 可能当时没挂载，纯内存状态会在切页时丢掉。写入后派发窗口事件，让已挂载的
 * 列表页立即响应，不必等下一次读取。
 */

export const LEAD_INBOX_UNSEEN_STORAGE_KEY = 'zsjos_lead_inbox_unseen'
export const LEAD_INBOX_UNSEEN_EVENT = 'zsjos-lead-inbox-unseen'

/** 上限防止长期累积；超出时丢最旧的。 */
export const LEAD_INBOX_UNSEEN_LIMIT = 200

/**
 * 接单后列表刷新的重试节奏（毫秒）。
 * 接单与列表查询是两次请求，后端写入对读可见有延迟，单次刷新可能拿不到新客资。
 */
export const LEAD_INBOX_REFRESH_RETRY_DELAYS_MS = [0, 400, 1200] as const

export type UnseenLeadDetail = { leadIds: number[] }

const getDefaultStorage = () => (typeof localStorage === 'undefined' ? undefined : localStorage)
const getDefaultTarget = () => (typeof window === 'undefined' ? undefined : window)

const dedupeIds = (ids: number[]) => [...new Set(ids)]

const isLeadId = (value: number) => Number.isSafeInteger(value) && value > 0

export function parseUnseenLeadIds(raw: string | null): number[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return dedupeIds(parsed.map(Number).filter(isLeadId))
  } catch {
    return []
  }
}

export function addUnseenLeadId(current: number[], leadId: number): number[] {
  if (!isLeadId(leadId)) return current
  return dedupeIds([...current, leadId]).slice(-LEAD_INBOX_UNSEEN_LIMIT)
}

export function removeUnseenLeadId(current: number[], leadId: number): number[] {
  return current.filter(id => id !== leadId)
}

export function unseenLeadIds(storage: Storage | undefined = getDefaultStorage()): number[] {
  try {
    return parseUnseenLeadIds(storage?.getItem(LEAD_INBOX_UNSEEN_STORAGE_KEY) ?? null)
  } catch {
    return []
  }
}

function persist(ids: number[], storage: Storage | undefined, target: EventTarget | undefined) {
  try {
    if (ids.length) storage?.setItem(LEAD_INBOX_UNSEEN_STORAGE_KEY, JSON.stringify(ids))
    else storage?.removeItem(LEAD_INBOX_UNSEEN_STORAGE_KEY)
  } catch {
    // 隐私模式下写入会抛。标记只是视觉提示，丢失不影响接单与列表本身
  }
  target?.dispatchEvent(new CustomEvent<UnseenLeadDetail>(LEAD_INBOX_UNSEEN_EVENT, {
    detail: { leadIds: ids }
  }))
}

/** 标记一条新到客资，返回更新后的全量集合。 */
export function markLeadUnseen(
  leadId: number,
  storage: Storage | undefined = getDefaultStorage(),
  target: EventTarget | undefined = getDefaultTarget()
): number[] {
  const current = unseenLeadIds(storage)
  const next = addUnseenLeadId(current, leadId)
  if (next.length === current.length && next.every((id, index) => id === current[index])) return current
  persist(next, storage, target)
  return next
}

/** 销售已查看该客资，清掉标记。 */
export function clearLeadUnseen(
  leadId: number,
  storage: Storage | undefined = getDefaultStorage(),
  target: EventTarget | undefined = getDefaultTarget()
): number[] {
  const current = unseenLeadIds(storage)
  const next = removeUnseenLeadId(current, leadId)
  if (next.length === current.length) return current
  persist(next, storage, target)
  return next
}
