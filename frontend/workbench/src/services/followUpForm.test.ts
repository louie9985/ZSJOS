import { describe, expect, it } from 'vitest'
import { appendFollowUpNote, followUpChoiceView, followUpOptionsWithSnapshot } from './followUpForm'

describe('follow-up form choices and remarks', () => {
  const options = Array.from({ length: 8 }, (_, i) => ({ value: `value-${i}`, label: `选项 ${i}` }))
  it('keeps server ordering and identifies a hidden selection by value', () => {
    const collapsed = followUpChoiceView(options, 'value-7', false)
    expect(collapsed.visible).toEqual(options.slice(0, 6))
    expect(collapsed.hiddenSelection).toBe('选项 7')
    expect(followUpChoiceView(options, 'value-7', true)).toEqual({ visible: options, hiddenSelection: undefined })
    expect(followUpChoiceView(options, undefined, false).hiddenSelection).toBeUndefined()
  })
  it('does not select a different value with the same label', () => {
    expect(followUpChoiceView(options.map(item => ({ ...item, label: '相同标签' })), 'value-7', false).hiddenSelection).toBe('相同标签')
  })
  it('uses the persisted label for an unchanged selection without mutating dictionary data', () => {
    expect(followUpOptionsWithSnapshot(options, 'value-0', '历史标签')[0].label).toBe('历史标签')
    expect(options[0].label).toBe('选项 0')
    expect(followUpOptionsWithSnapshot(options, 'deleted', '停用前标签')[0]).toEqual({ value: 'deleted', label: '停用前标签', disabled: true })
    expect(followUpOptionsWithSnapshot(options, 'deleted')[0].label).toBe('未记录')
    expect(followUpOptionsWithSnapshot(options, undefined)).toBe(options)
  })
  it('appends with a space and accepts exactly 2000 characters', () => {
    expect(appendFollowUpNote('原备注', '下一步')).toBe('原备注 下一步')
    expect(appendFollowUpNote('', '字'.repeat(2000))).toHaveLength(2000)
    expect(appendFollowUpNote('字'.repeat(1998), '字')).toHaveLength(2000)
    expect(appendFollowUpNote('字'.repeat(1999), '字')).toBeUndefined()
  })
})
