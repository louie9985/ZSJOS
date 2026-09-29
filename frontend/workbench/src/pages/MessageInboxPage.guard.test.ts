import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { sourceHasCall } from '../test/sourceGuard'

// 文件名与 import 路径都按仓库约定用源文件的大小写（Linux 下大小写敏感，
// 写成 messageinboxpage.tsx 会直接 ENOENT 把整个 suite 挂掉）。
const source = readFileSync(new URL('./MessageInboxPage.tsx', import.meta.url), 'utf8')

describe('message inbox category guard', () => {
  it('renders the category filter from the server catalog and keeps deep-linked messages visible', () => {
    expect(source).toContain('Segmented')
    // 分类目录改为服务端下发，页面不再持有本地分类表。
    expect(source).toContain('useNotifyMessageCategories')
    expect(source).not.toContain('notifyMessageCategoryOf')
    expect(sourceHasCall(source, 'api.myNotifyMessage')).toBe(true)
    // 深链选中的消息独立于当前页结果，不随列表加载重新触发。
    expect(source).toContain('深链选中的消息独立于当前页结果')
    expect(source).toContain('加载更多')
  })

  it('resets the table page when the category or search changes', () => {
    // 否则在第 N 页切到条目更少的分类会直接落到空表。
    expect(source).toContain('setCategory(value as string); setTablePage(1)')
    expect(source).toContain("if (!event.target.value) { setKeyword(''); setTablePage(1) }")
  })

  it('retains the opened unread message while its row leaves the list', () => {
    expect(source).toContain("view === 'unread' && selected.readStatus")
    expect(source).toContain('if (useTableLayout || feed.loading) return')
    expect(source).toMatch(/setSelected\(undefined\)[\s\S]*?setDrawerOpen\(false\)[\s\S]*?\[category, keyword, view\]/)
  })

  it('places split-view search before the cards and keeps table search available', () => {
    const listStart = source.indexOf('<div className="business-inbox-scroll"')
    const searchStart = source.indexOf('<div className="message-inbox-list-search">{searchControl}</div>', listStart)
    expect(searchStart).toBeGreaterThan(listStart)
    expect(searchStart).toBeLessThan(source.indexOf('visibleMessages.map', listStart))
    expect(source).toContain('{useTableLayout && searchControl}')
    expect(source.match(/<Input.Search/g)).toHaveLength(1)
  })
})
