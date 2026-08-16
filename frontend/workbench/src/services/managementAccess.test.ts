import { describe, expect, it } from 'vitest'
import {
  availableAuditTabs,
  canUpdateMaintenance,
  hasPermission,
  withdrawalDetailScope
} from './managementAccess'

describe('management access', () => {
  it('uses exact permissions or the wildcard only', () => {
    expect(hasPermission(['zsjos:audit:query'], 'zsjos:audit:query')).toBe(true)
    expect(hasPermission(['*:*:*'], 'zsjos:partner:convert')).toBe(true)
    expect(hasPermission(['zsjos:audit:query'], 'zsjos:audit:query-impersonation')).toBe(false)
  })

  it('builds only the authorized audit tabs in stable order', () => {
    expect(availableAuditTabs([])).toEqual([])
    expect(availableAuditTabs(['zsjos:audit:query'])).toEqual(['business'])
    expect(availableAuditTabs(['zsjos:audit:query-impersonation'])).toEqual(['impersonation'])
    expect(availableAuditTabs(['*:*:*'])).toEqual(['business', 'impersonation'])
  })

  it('requires the server role for maintenance updates', () => {
    expect(canUpdateMaintenance(['super_admin'])).toBe(true)
    expect(canUpdateMaintenance(['admin'])).toBe(false)
  })

  it('selects withdrawal detail scope from query permissions', () => {
    expect(withdrawalDetailScope(['zsjos:withdrawal:finance-query'])).toBe('finance')
    expect(withdrawalDetailScope(['zsjos:withdrawal:admin-query'])).toBe('admin')
    expect(withdrawalDetailScope(['zsjos:withdrawal:my-query'])).toBe('own')
    expect(withdrawalDetailScope(['*:*:*'])).toBe('finance')
  })
})
