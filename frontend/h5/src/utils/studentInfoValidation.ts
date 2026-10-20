export const validCollectionIdentity = (input: string): boolean => {
  const value = input.trim().toUpperCase()
  if (!/^\d{17}[\dX]$/.test(value)) return false
  const date = value.slice(6, 14)
  const year = Number(date.slice(0, 4)), month = Number(date.slice(4, 6)), day = Number(date.slice(6, 8))
  const parsed = new Date(year, month - 1, day)
  if (parsed.getFullYear() !== year || parsed.getMonth() !== month - 1 || parsed.getDate() !== day || parsed > new Date()) return false
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const remainder = weights.reduce((sum, weight, i) => sum + Number(value[i]) * weight, 0) % 11
  return '10X98765432'[remainder] === value[17]
}

export const deriveCollectionIdentity = (input: string, today = new Date()) => {
  const card = input.trim().toUpperCase()
  if (!validCollectionIdentity(card)) return undefined
  const year = Number(card.slice(6, 10)), month = Number(card.slice(10, 12)), day = Number(card.slice(12, 14))
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(today)
  const current = (type: string) => Number(parts.find(part => part.type === type)?.value)
  let age = current('year') - year
  if (current('month') < month || current('month') === month && current('day') < day) age--
  return { age: String(age), gender: Number(card[16]) % 2 ? '1' : '2', areaCode: card.slice(0, 6) }
}

interface IdentityArea { id: number; name: string; selectionCode?: string; children?: IdentityArea[] }
export const findCollectionIdentityArea = (nodes: IdentityArea[], code: string): IdentityArea[] => {
  for (const node of nodes) {
    if ((node.selectionCode || String(node.id)) === code) return [node]
    const children = findCollectionIdentityArea(node.children || [], code)
    if (children.length) return [node, ...children]
  }
  return []
}
