import { describe, expect, it } from 'vitest'
import type { NotifyMessage } from './api'
import { applyReadStatus, buildNotifyMessagePageParams } from './notifyMessage'

const messages: NotifyMessage[] = [
  {
    id: 1,
    templateNickname: '系统',
    templateContent: '第一条消息',
    templateType: 1,
    readStatus: false,
    createTime: Date.UTC(2026, 7, 9, 10)
  },
  {
    id: 2,
    templateNickname: '教务中心',
    templateContent: '第二条消息',
    templateType: 1,
    readStatus: false,
    createTime: Date.UTC(2026, 7, 9, 11)
  }
]

describe('notify message inbox state', () => {
  it('fixes the unread route to unread messages while leaving all messages unfiltered', () => {
    expect(buildNotifyMessagePageParams('all', 2, 20)).toEqual({ pageNo: 2, pageSize: 20 })
    expect(buildNotifyMessagePageParams('unread', 1, 10)).toEqual({
      pageNo: 1,
      pageSize: 10,
      readStatus: false
    })
  })

  it('keeps read messages in the all view and removes them from the unread view', () => {
    const readTime = Date.UTC(2026, 7, 9, 12)
    expect(applyReadStatus(messages, [1], 'all', readTime)).toEqual([
      { ...messages[0], readStatus: true, readTime },
      messages[1]
    ])
    expect(applyReadStatus(messages, [1], 'unread', readTime)).toEqual([messages[1]])
  })
})
