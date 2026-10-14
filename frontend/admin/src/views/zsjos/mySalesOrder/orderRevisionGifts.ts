import type { GiftConfigVO, GiftSnapshot, OrderGiftProjection } from '@/api/zsjos/gift'

export interface RevisionGiftOption { value: string; label: string }

export function readRevisionGifts(order: OrderGiftProjection): GiftSnapshot[] {
  try {
    if (order.giftItemsInvalid) throw new Error()
    const raw: unknown = order.giftItemSnapshots ?? (order.giftItems?.trim() ? JSON.parse(order.giftItems) : [])
    if (!Array.isArray(raw)) throw new Error()
    const seen = new Set<string>()
    const result = raw.map(value => {
      const item = typeof value === 'string' ? JSON.parse(value) : value
      if (!item || typeof item !== 'object' || typeof item.code !== 'string' || !item.code.trim()
        || seen.has(item.code) || (item.name != null && typeof item.name !== 'string')
        || (item.path != null && (!Array.isArray(item.path) || item.path.some((part: unknown) => typeof part !== 'string')))) throw new Error()
      seen.add(item.code)
      return item as GiftSnapshot
    })
    if (order.giftItemCodes && (order.giftItemCodes.length !== result.length
      || order.giftItemCodes.some((code, index) => code !== result[index].code))) throw new Error()
    return result
  } catch { throw new Error('历史礼品信息无法读取，请先核实订单后再补正') }
}

export function revisionGiftOptions(catalog: GiftConfigVO[], historical: GiftSnapshot[]): RevisionGiftOption[] {
  const all: GiftConfigVO[] = []
  const collect = (rows: GiftConfigVO[]) => rows.forEach(row => { all.push(row); if (row.children) collect(row.children) })
  collect(catalog)
  const byId = new Map(all.map(item => [item.id, item]))
  const parents = new Set(all.map(item => item.parentId).filter(id => id != null))
  const options = new Map<string, RevisionGiftOption>()
  for (const item of all) {
    if (item.status !== 0 || parents.has(item.id) || item.children?.length) continue
    const path = [item.name]
    const visited = new Set([item.id])
    let parent = item.parentId == null ? undefined : byId.get(item.parentId)
    while (parent && !visited.has(parent.id)) {
      visited.add(parent.id); path.unshift(parent.name)
      parent = parent.parentId == null ? undefined : byId.get(parent.parentId)
    }
    options.set(item.code, { value: item.code, label: path.join(' / ') })
  }
  // Persisted names win over current catalog labels, including deleted or disabled selections.
  for (const item of historical) options.set(item.code, {
    value: item.code, label: item.path?.length ? item.path.join(' / ') : item.name || '历史名称未记录'
  })
  return [...options.values()]
}

export function revisionGiftPayload(codes: string[], address: string | undefined, options: RevisionGiftOption[]) {
  if (!Array.isArray(codes) || codes.some(code => !options.some(option => option.value === code))) {
    throw new Error('请重新选择有效礼品')
  }
  const giftItems = [...new Set(codes)]
  const giftShippingAddress = giftItems.length ? address?.trim() : undefined
  if (giftItems.length && (!giftShippingAddress || giftShippingAddress.length > 1000)) {
    throw new Error('选择礼品后请填写邮寄地址，最多 1000 个字符')
  }
  return { giftItems, giftShippingAddress }
}
