import { describe, expect, it } from 'vitest'
import { notifyMessageCategoryOf, notifyMessageMatchesCategory, notifyMessageCategoryLabel, NOTIFY_MESSAGE_CATEGORY_ORDER } from './notifyMessageCategory'

describe('notify message category', () => {
  it('keeps the six visible categories in the expected order', () => {
    expect(NOTIFY_MESSAGE_CATEGORY_ORDER).toEqual(['all', 'lead', 'withdrawal', 'reward', 'appeal', 'system'])
    expect(notifyMessageCategoryLabel).toEqual({
      all: '全部',
      lead: '客资',
      withdrawal: '提现',
      reward: '收益',
      appeal: '申诉',
      system: '系统'
    })
  })

  it('maps the current business message shapes to the six tabs', () => {
    expect(notifyMessageCategoryOf({ bizType: 'lead' })).toBe('lead')
    expect(notifyMessageCategoryOf({ bizType: 'sales_order' })).toBe('lead')
    expect(notifyMessageCategoryOf({ sceneCode: 'zsjos.registration.task_created' })).toBe('lead')
    expect(notifyMessageCategoryOf({ bizType: 'withdrawal', sceneCode: 'zsjos.withdrawal.apply' })).toBe('withdrawal')
    expect(notifyMessageCategoryOf({ sourceEventKey: 'reward_available' })).toBe('reward')
    expect(notifyMessageCategoryOf({ sceneCode: 'zsjos.lead.appeal_submitted' })).toBe('appeal')
    expect(notifyMessageCategoryOf({ bizType: 'bug_feedback' })).toBe('system')
  })

  it('matches the all category without excluding anything', () => {
    const message = { bizType: 'lead', sceneCode: 'zsjos.lead.follow_up_recorded', sourceEventKey: '' }
    expect(notifyMessageMatchesCategory(message, 'all')).toBe(true)
    expect(notifyMessageMatchesCategory(message, 'lead')).toBe(true)
    expect(notifyMessageMatchesCategory(message, 'system')).toBe(false)
  })
})
