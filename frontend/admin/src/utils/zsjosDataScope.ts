export type ZsjosDataScope = 'all' | 'own' | 'unauthorized'

export const withdrawalDataScope = (permissions: ReadonlySet<string>): ZsjosDataScope => {
  if (permissions.has('*:*:*')) return 'all'
  if (permissions.has('zsjos:withdrawal:finance-query')) return 'all'
  if (permissions.has('zsjos:withdrawal:admin-query')) return 'all'
  if (permissions.has('zsjos:withdrawal:my-query')) return 'own'
  return 'unauthorized'
}

export const cashbackDataScope = (permissions: ReadonlySet<string>): ZsjosDataScope => {
  if (permissions.has('*:*:*')) return 'all'
  if (permissions.has('zsjos:cashback:finance-query')) return 'all'
  if (permissions.has('zsjos:cashback:my-query')) return 'own'
  return 'unauthorized'
}
