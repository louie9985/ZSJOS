import { describe, expect, it } from 'vitest'
import type { WorkbenchMenu } from '../services/api'
import { appendMenuTab, MAX_TABS, type TabItem } from './TabBar'

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
