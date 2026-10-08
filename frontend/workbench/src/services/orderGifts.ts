export type GiftSnapshot = { code: string; name?: string | null; path?: string[] | null; snapshotAt?: string | null }
export type GiftHistory = { giftItems?: string | null; giftItemCodes?: string[]; giftItemSnapshots?: GiftSnapshot[]; giftItemsInvalid?: boolean }

export function readOrderGifts(order?: GiftHistory): GiftSnapshot[] {
  if (!order) return []
  if (order.giftItemsInvalid) throw new Error('历史礼品信息无法读取，请联系管理员核实后再提交')
  const raw: unknown = order.giftItemSnapshots ?? (order.giftItems?.trim() ? JSON.parse(order.giftItems) : [])
  if (!Array.isArray(raw)) throw new Error('历史礼品信息格式无效')
  const codes = new Set<string>()
  const snapshots = raw.map(value => {
    const item: unknown = typeof value === 'string' ? JSON.parse(value) : value
    if (!item || typeof item !== 'object' || !('code' in item) || typeof item.code !== 'string' || !item.code.trim()
        || codes.has(item.code)) throw new Error('历史礼品信息格式无效')
    const snapshot = item as GiftSnapshot
    if (snapshot.name != null && typeof snapshot.name !== 'string'
        || snapshot.path != null && (!Array.isArray(snapshot.path) || snapshot.path.some(value => typeof value !== 'string'))
        || snapshot.snapshotAt != null && typeof snapshot.snapshotAt !== 'string') throw new Error('历史礼品信息格式无效')
    codes.add(item.code)
    return snapshot
  })
  if (order.giftItemCodes != null && (!Array.isArray(order.giftItemCodes)
      || order.giftItemCodes.length !== snapshots.length
      || order.giftItemCodes.some((code, index) => code !== snapshots[index].code))) {
    throw new Error('历史礼品编码与快照不一致，请联系管理员核实后再提交')
  }
  return snapshots
}

export function giftSummary(order?: GiftHistory): string {
  try { return readOrderGifts(order).map(item => item.name || '历史名称未记录').join('、') || '无礼品' }
  catch { return '历史礼品信息无法读取' }
}

export function giftOptions(current: Array<{ value: string; title: string }>, historical: GiftSnapshot[]) {
  const prior = new Set(historical.map(item => item.code))
  return [...historical.map(item => ({ value: item.code, title: `${item.name || '历史名称未记录'}（历史选择）` })),
    ...current.filter(item => !prior.has(item.value))]
}
