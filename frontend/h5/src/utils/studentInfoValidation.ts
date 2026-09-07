export const validCollectionIdentity = (input: string): boolean => {
  const value = input.trim().toUpperCase()
  if (!/^(\d{15}|\d{17}[\dX])$/.test(value)) return false
  const date = value.length === 15 ? `19${value.slice(6, 12)}` : value.slice(6, 14)
  const year = Number(date.slice(0, 4)), month = Number(date.slice(4, 6)), day = Number(date.slice(6, 8))
  const parsed = new Date(year, month - 1, day)
  if (parsed.getFullYear() !== year || parsed.getMonth() !== month - 1 || parsed.getDate() !== day || parsed > new Date()) return false
  if (value.length === 15) return true
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const remainder = weights.reduce((sum, weight, i) => sum + Number(value[i]) * weight, 0) % 11
  return '10X98765432'[remainder] === value[17]
}
