import { describe, expect, it } from 'vitest'
import { resolveWorkbenchComponent, WORKBENCH_COMPONENT } from './menuComponentRegistry'

describe('workbench menu component registry', () => {
  it('maps the server-owned appeal component independently of its menu path', () => {
    expect(resolveWorkbenchComponent('zsjos/leadAppeal/index')).toBe(WORKBENCH_COMPONENT.LEAD_APPEAL)
    expect(resolveWorkbenchComponent('  zsjos/leadAppeal/index  ')).toBe(WORKBENCH_COMPONENT.LEAD_APPEAL)
  })

  it('maps the server-owned subordinate-sales component', () => {
    expect(resolveWorkbenchComponent('zsjos/subordinateSales/index')).toBe(WORKBENCH_COMPONENT.SUBORDINATE_SALES)
  })

  it('leaves unknown server components on the migration placeholder', () => {
    expect(resolveWorkbenchComponent('zsjos/notMigrated/index')).toBeUndefined()
    expect(resolveWorkbenchComponent()).toBeUndefined()
  })
})
