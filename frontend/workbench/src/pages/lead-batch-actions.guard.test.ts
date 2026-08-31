import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const page = readFileSync(new URL('LeadManagementPage.tsx', import.meta.url), 'utf8')
const api = readFileSync(new URL('../services/api.ts', import.meta.url), 'utf8')

describe('lead table batch actions', () => {
  it('keeps selection and ProTable batch controls in table mode', () => {
    expect(page).toContain('rowSelection={leadRowSelection}')
    expect(page).toContain('preserveSelectedRowKeys: true')
    expect(page).toContain('showSizeChanger: true')
    expect(page).toContain('pageSizeOptions: [20, 50, 100]')
    expect(page).toContain('sizeChanged ? 1 : nextPage')
    expect(page).toContain('lead-management-table-toolbar-left')
    expect(page.indexOf('lead-management-batch-toolbar')).toBeLessThan(page.indexOf('lead-management-table-filter-toolbar'))
    expect(page).toContain('keys.slice(0, 100)')
    expect(page).toContain('tableAlertRender={false}')
    expect(page).toContain('tableAlertOptionRender={false}')
    expect(page).not.toContain('lead-management-batch-float')
    expect(page).toContain('<Dropdown menu={{ items: batchMenuItems }}')
    expect(page).toContain('>批量操作</Button>')
    expect(page).toContain('lead-management-batch-toolbar')

    expect(page).toContain('setSelectedRowKeys([])')
    expect(page).toContain('setSelectedLeadMap(new Map())')
    const styles = readFileSync(new URL('../styles/pages/lead-management.css', import.meta.url), 'utf8')
    expect(styles).toMatch(/\.lead-management-table-toolbar-left \{[^}]*flex-wrap: nowrap;/)
    expect(styles).toMatch(/\.lead-management-table-toolbar-left \{[^}]*min-width: 1040px;/)
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
