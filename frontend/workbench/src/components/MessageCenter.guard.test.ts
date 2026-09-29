import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens, sourceHasCall } from '../test/sourceGuard'

const componentSource = readFileSync(new URL('./MessageCenter.tsx', import.meta.url), 'utf8')
const feedSource = readFileSync(new URL('../services/useNotifyMessageFeed.ts', import.meta.url), 'utf8')
const stylesSource = readFileSync(new URL('../styles/pages/message-inbox.css', import.meta.url), 'utf8')

describe('message center popup guard', () => {
  it('loads unread messages when the bell popup opens and keeps the full inbox entry', () => {
    // 游标加载统一收在共享 hook 里，弹窗与消息中心列表页复用同一份实现。
    expect(sourceHasCall(feedSource, 'api.myNotifyMessageCursor')).toBe(true)
    expectSourceToContainTokens(feedSource, "buildNotifyMessageCursorParams(view")
    expectSourceToContainTokens(componentSource, 'useNotifyMessageFeed({ view: \'unread\'')
    // 弹窗打开才拉取：enabled 由关转开时 hook 自行加载，页面不再重复发一次请求。
    expectSourceToContainTokens(componentSource, 'enabled: open')
    expect(feedSource).toContain('if (!enabled) return')
    expect(componentSource).toContain('onScroll={handleListScroll}')
    expect(componentSource).toContain('executeNotifyMessageAction(item')
    expectSourceToContainTokens(componentSource, 'navigate(APP_ROUTES.ALL_MESSAGES)')
  })

  it('uses a fixed popup body with an independently scrollable message list', () => {
    expect(stylesSource).toMatch(/\.message-center-popup\s*\{[^}]*height:\s*min\(440px,/s)
    expect(stylesSource).toMatch(/\.message-center-popup-list\s*\{[^}]*overflow-y:\s*auto/s)
  })
})
