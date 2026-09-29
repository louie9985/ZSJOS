import { describe, expect, it } from 'vitest'
import { notifyMessageCategoryLabelOf } from './notifyMessageCategory'

describe('notify message category labels', () => {
  // 分类目录由服务端返回，这里只验证客户端的标签解析，不再重复服务端口径。
  const categories = [
    { key: 'all', label: '全部' },
    { key: 'appeal', label: '申诉' },
    { key: 'withdrawal', label: '提现' },
    { key: 'lead', label: '客资' },
    { key: 'system', label: '系统' }
  ]

  it('resolves a label from the server catalog', () => {
    expect(notifyMessageCategoryLabelOf(categories, 'lead')).toBe('客资')
    expect(notifyMessageCategoryLabelOf(categories, 'withdrawal')).toBe('提现')
    expect(notifyMessageCategoryLabelOf(categories, 'all')).toBe('全部')
  })

  it('falls back to the server-side default category for unknown or absent keys', () => {
    // 后端先上线新分类时不应显示为空白
    expect(notifyMessageCategoryLabelOf(categories, 'reward')).toBe('系统')
    expect(notifyMessageCategoryLabelOf(categories, undefined)).toBe('系统')
    expect(notifyMessageCategoryLabelOf(categories, '')).toBe('系统')
    expect(notifyMessageCategoryLabelOf([], 'lead')).toBe('系统')
  })
})
