import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens, sourceHasCall } from '../test/sourceGuard'

const componentSource = readFileSync(new URL('./MessageCenter.tsx', import.meta.url), 'utf8')
const stylesSource = readFileSync(new URL('../styles/pages/message-inbox.css', import.meta.url), 'utf8')

describe('message center popup guard', () => {
  it('loads unread messages when the bell popup opens and keeps the full inbox entry', () => {
    expect(sourceHasCall(componentSource, 'api.myNotifyMessageCursor')).toBe(true)
    expectSourceToContainTokens(componentSource, "buildNotifyMessageCursorParams('unread'")
    expectSourceToContainTokens(componentSource, 'if (nextOpen) void loadUnreadMessages(false)')
    expect(componentSource).toContain('onScroll={handleListScroll}')
    expect(componentSource).toContain('executeNotifyMessageAction(item')
    expectSourceToContainTokens(componentSource, 'navigate(APP_ROUTES.ALL_MESSAGES)')
  })

  it('uses a fixed popup body with an independently scrollable message list', () => {
    expect(stylesSource).toMatch(/\.message-center-popup\s*\{[^}]*height:\s*min\(440px,/s)
    expect(stylesSource).toMatch(/\.message-center-popup-list\s*\{[^}]*overflow-y:\s*auto/s)
  })
})
