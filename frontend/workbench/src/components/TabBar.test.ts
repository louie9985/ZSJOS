import { describe, expect, it } from 'vitest'
import type { WorkbenchMenu } from '../services/api'
import { appendMenuTab, MAX_TABS, type TabItem } from './TabBar'
import { RETAINED_PAGE_PATHS } from '../retainedPagePaths'

const menu = (path: string, name = path): WorkbenchMenu => ({
  id: Number(path.replace(/\D/g, '')) || 1,
  name,
  path,
  parentId: 0,
  hidden: false,
  noCache: false,
  alwaysShow: false,
  children: []
})

describe('Workbench tabs', () => {
  it('never evicts a retained editor when opening more than the tab limit', () => {
    let tabs = appendMenuTab([], menu('/home'))
    for (const path of RETAINED_PAGE_PATHS) tabs = appendMenuTab(tabs, menu(path))
    for (let index = 0; index < MAX_TABS * 2; index++) tabs = appendMenuTab(tabs, menu(`/other/${index}`))
    expect(tabs).toHaveLength(MAX_TABS)
    for (const path of RETAINED_PAGE_PATHS) expect(tabs.some(tab => tab.key === path)).toBe(true)
  })
  it('reuses the menu identity while remembering the latest account address', () => {
    const first = appendMenuTab([], menu('/zsjos/media-students', '媒体学员'), '/zsjos/media-students?personId=1&accountId=2')
    const next = appendMenuTab(first, menu('/zsjos/media-students', '媒体学员'), '/zsjos/media-students?personId=3&accountId=4')
    expect(next).toHaveLength(1)
    expect(next[0].key).toBe('/zsjos/media-students')
    expect(next[0].href).toBe('/zsjos/media-students?personId=3&accountId=4')
    expect(appendMenuTab(next, menu('/zsjos/media-students'), next[0].href)).toBe(next)
  })
  it('adds each menu once and keeps the first tab fixed', () => {
    const first = appendMenuTab([], menu('/page/0', '首页'))
    const duplicate = appendMenuTab(first, menu('/page/0', '首页'))
    const second = appendMenuTab(duplicate, menu('/page/1', '用户管理'))

    expect(first).toEqual([{ key: '/page/0', label: '首页', closable: false }])
    expect(duplicate).toBe(first)
    expect(second[1]).toEqual({ key: '/page/1', label: '用户管理', closable: true })
  })

  it('evicts the earliest closable tab at the shared limit', () => {
    let tabs: TabItem[] = []
    for (let index = 0; index <= MAX_TABS; index += 1) {
      tabs = appendMenuTab(tabs, menu(`/page/${index}`))
    }

    expect(tabs).toHaveLength(MAX_TABS)
    expect(tabs.some(tab => tab.key === '/page/0')).toBe(true)
    expect(tabs.some(tab => tab.key === '/page/1')).toBe(false)
    expect(tabs.some(tab => tab.key === `/page/${MAX_TABS}`)).toBe(true)
  })
})
