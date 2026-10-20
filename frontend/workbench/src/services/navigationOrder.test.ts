import { describe, expect, it } from 'vitest'
import * as workbench from './navigationOrder'
import * as admin from '../../../admin/src/utils/navigationOrder'

for (const [name, api] of [['workbench', workbench], ['admin', admin]] as const) {
  describe(name + ' personal navigation order', () => {
    const nodes = [{ id: 1, name: 'A', children: [{ id: 3, name: 'C' }, { id: 4, name: 'D' }] }, { id: 2, name: 'B' }]
    it('sorts only authorized siblings, ignores stale keys, and appends newly authorized menus', () => {
      const result = api.applyNavigationOrder(nodes, { groups: [{ parentKey: 'root', keys: ['menu:99', 'menu:2'] }] })
      expect(result.map(n => n.id)).toEqual([2, 1])
      expect(result[1].children?.map(n => n.id)).toEqual([3, 4])
      expect(nodes.map(n => n.id)).toEqual([1, 2])
    })
    it('never moves a child to the root or applies another parent order', () => {
      const result = api.applyNavigationOrder(nodes, { groups: [{ parentKey: 'root', keys: ['menu:4'] }, { parentKey: 'menu:2', keys: ['menu:4', 'menu:3'] }] })
      expect(result).toEqual(nodes)
    })
    it('sorts nested siblings and reset follows the latest default', () => {
      const value = { groups: [{ parentKey: 'menu:1', keys: ['menu:4', 'menu:3'] }] }
      expect(api.applyNavigationOrder(nodes, value)[0].children?.map(n => n.id)).toEqual([4, 3])
      expect(api.applyNavigationOrder(nodes, { groups: [] })).toEqual(nodes)
    })
    it('uses stable layout keys across renames and synthetic id changes', () => {
      expect(api.navigationKey({ id: -12, layoutKey: 'sales', name: 'old' })).toBe(api.navigationKey({ id: -99, layoutKey: 'sales', name: 'new' }))
    })
    it('excludes hidden menus and preserves nested location', () => {
      expect(api.visibleNavigation([{ id: 1, name: 'hidden', visible: false }, { id: 2, name: 'hidden', hidden: true }])).toEqual([])
      expect(api.findNavigationLevel(nodes, ['menu:1']).map(n => n.id)).toEqual([3, 4])
    })
  })
}
