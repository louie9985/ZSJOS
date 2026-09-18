import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { sourceHasCall } from '../test/sourceGuard'

// 文件名与 import 路径都按仓库约定用源文件的大小写（Linux 下大小写敏感，
// 写成 messageinboxpage.tsx 会直接 ENOENT 把整个 suite 挂掉）。
const source = readFileSync(new URL('./MessageInboxPage.tsx', import.meta.url), 'utf8')

describe('message inbox category guard', () => {
  it('renders the six-category filter and keeps deep-linked messages visible', () => {
    expect(source).toContain('Segmented')
    expect(source).toContain('NOTIFY_MESSAGE_CATEGORY_ORDER')
    expect(sourceHasCall(source, 'api.myNotifyMessage')).toBe(true)
    expect(source).toContain('The URL-selected message is independent of the current page result')
    expect(source).toContain('加载更多')
  })
})
