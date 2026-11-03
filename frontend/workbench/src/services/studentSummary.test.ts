import { describe, expect, it } from 'vitest'
import { visibleSummaryColumns } from './studentSummary'
import { filterAdminRoutes } from '../../../admin/src/utils/adminRouteFilter'
const columns = ['name', 'mobile', 'info.id_card'].map(key => ({ key, label: key, group: 'test' }))
describe('summary export columns', () => {
  it('excludes hidden fields and follows reordered columns', () => {
    expect(visibleSummaryColumns(columns, { name: { order: 2 }, mobile: { show: false }, 'info.id_card': { order: 0 } })).toEqual(['info.id_card', 'name'])
  })
  it('matches pinned column placement', () => {
    expect(visibleSummaryColumns(columns, { name: { fixed: 'right' }, 'info.id_card': { fixed: 'left' } })).toEqual(['info.id_card', 'mobile', 'name'])
  })
  it('restores catalog order and excludes stale preferences', () => {
    expect(visibleSummaryColumns(columns, { old: { order: 0 } })).toEqual(['name', 'mobile', 'info.id_card'])
  })
  it('Vue Admin excludes unsupported summary without hiding its other menus', () => {
    expect(filterAdminRoutes([{ component: 'zsjos/studentSummary/index' }, { component: 'system/user/index' }])).toEqual([{ component: 'system/user/index' }])
  })
})
