import { describe, expect, it } from 'vitest'
import { buildMenuTree, type RawMenu } from './services/api'
import { getRetainedPageMenus } from './retainedPagePaths'

const raw = (id: number, path: string, extra: Partial<RawMenu> = {}): RawMenu => ({
  id, path, name: path, parentId: 0, type: 2, visible: true, ...extra
})
const paths = new Set(['/zsjos/on', '/zsjos/off', '/zsjos/unset', '/zsjos/hidden', '/embed', '/admin', '/directory', '/button'])
const retained = (menus: RawMenu[]) => getRetainedPageMenus(buildMenuTree(menus), paths).map(menu => menu.path)

describe('server-owned React page cache policy', () => {
  it('uses keepAlive from the permission response, including relative child paths', () => {
    expect(retained([raw(1, 'zsjos', { type: 1, children: [
      raw(2, 'on', { keepAlive: true }),
      raw(3, 'off', { keepAlive: false }),
      raw(4, 'unset'),
      raw(5, 'hidden', { keepAlive: true, visible: false })
    ] })])).toEqual(['/zsjos/on', '/zsjos/hidden'])
  })
  it('excludes embedded/admin-only pages, directories, buttons and unsupported routes', () => {
    expect(retained([
      raw(1, 'embed', { keepAlive: true, workbenchRenderMode: 'admin_embed' }),
      raw(2, 'admin', { keepAlive: true, workbenchRenderMode: 'admin_only' }),
      raw(3, 'unsupported', { keepAlive: true }),
      raw(4, 'directory', { keepAlive: true, type: 1 }),
      raw(5, 'button', { keepAlive: true, type: 3 })
    ])).toEqual([])
  })
  it('drops disabled and revoked entries when permissions are reloaded', () => {
    const source = raw(2, 'zsjos/on', { keepAlive: true })
    expect(retained([source])).toEqual(['/zsjos/on'])
    expect(retained([{ ...source, keepAlive: false }])).toEqual([])
    expect(retained([])).toEqual([])
  })
  it('does not inherit a directory cache flag or retain pages absent from the runtime', () => {
    const source = buildMenuTree([raw(1, 'zsjos', { type: 1, keepAlive: true, children: [raw(2, 'on')] })])
    expect(getRetainedPageMenus(source, paths)).toEqual([])
    expect(getRetainedPageMenus(buildMenuTree([raw(2, 'zsjos/on', { keepAlive: true })]), new Set())).toEqual([])
  })
})
