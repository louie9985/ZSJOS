import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens } from '../test/sourceGuard'

const page = readFileSync(new URL('LeadManagementPage.tsx', import.meta.url), 'utf8')
const shared = readFileSync(new URL('../components/BusinessTable/index.tsx', import.meta.url), 'utf8')
const api = readFileSync(new URL('../services/api.ts', import.meta.url), 'utf8')

describe('lead table batch actions', () => {
  it('keeps selection and ProTable batch controls in table mode', () => {
    expect(page).toContain('rowSelection={leadRowSelection}')
    expectSourceToContainTokens(page, 'preserveSelectedRowKeys: true')
    expect(page).toContain('showSizeChanger: true')
    expectSourceToContainTokens(page, 'pageSizeOptions: [20, 50, 100]')
    expect(page).toContain('sizeChanged ? 1 : nextPage')
    expect(page).toContain('BusinessTable<ManagedLead>')
    expect(shared.indexOf('className="business-table-batch"')).toBeLessThan(shared.indexOf('className="business-table-filters"'))
    expect(page).toContain('keys.slice(0, 100)')
    expect(shared).toContain('tableAlertRender={false}')
    expect(shared).toContain('tableAlertOptionRender={false}')
    expect(page).not.toContain('lead-management-batch-float')
    expect(page).toContain('<Dropdown menu={{ items: batchMenuItems }}')
    expect(page).toContain('>批量操作</Button>')
    expect(page).toContain('batchActions={')

    expect(page).toContain('setSelectedRowKeys([])')
    expectSourceToContainTokens(page, 'setSelectedLeadMap(new Map())')
    const styles = readFileSync(new URL('../styles/components/business-table.css', import.meta.url), 'utf8')
    expect(styles).toMatch(/\.business-table-toolbar \{[^}]*flex-wrap: wrap;/)
    expect(styles).not.toContain('1040px')
  })

  it('exposes all five disposition actions and their result contract', () => {
    for (const action of ['transfer', 'restore', 'recycle', 'release-claim-pool', 'release-public-sea']) {
      expect(page).toContain(`type: '${action}'`)
      expect(api).toContain(`| "${action}"`)
    }
    expect(page).toContain('批量操作完成')
    expect(page).toContain('成功 ${result.successCount} 条，失败 ${result.failureCount} 条')
    expect(page).toContain('item.message')
    expect(api).toContain('`/zsjos/lead/batch/${action}`')
  })

  it('requires the transfer target and operation reason in the shared form', () => {
    expect(page).toContain('name="targetUserId" label="目标销售"')
    expect(page).toContain('name="reason" label="操作原因"')
    expect(page).toContain('公海跟进销售（可不填）')
  })
})
