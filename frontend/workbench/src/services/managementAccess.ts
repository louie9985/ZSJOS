export const hasPermission = (permissions: readonly string[], permission: string) =>
  permissions.includes('*:*:*') || permissions.includes(permission)

export const availableAuditTabs = (permissions: readonly string[]) => [
  ...(hasPermission(permissions, 'zsjos:audit:query') ? ['business' as const] : []),
  ...(hasPermission(permissions, 'zsjos:audit:query-impersonation') ? ['impersonation' as const] : [])
]

export const canUpdateMaintenance = (roles: readonly string[]) => roles.includes('super_admin')

export type WithdrawalDetailScope = 'own' | 'admin' | 'finance'

export const withdrawalDetailScope = (permissions: readonly string[]): WithdrawalDetailScope => {
  if (hasPermission(permissions, 'zsjos:withdrawal:finance-query')) return 'finance'
  if (hasPermission(permissions, 'zsjos:withdrawal:admin-query')) return 'admin'
  return 'own'
}
