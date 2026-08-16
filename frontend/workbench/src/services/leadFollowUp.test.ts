import { describe, expect, it } from 'vitest'
import {
  addFollowUpDays, appendQuickNote, chunkSnakeRows, defaultLeadDetailTab, filterFollowUps,
  shouldBlockLeadSwitch, snakeColumnsForWidth, snakeRowReversed
} from './leadFollowUp'

describe('lead follow-up form logic', () => {
  it('adds quick notes without overwriting existing remarks', () => {
    expect(appendQuickNote('', '稍后联系')).toBe('稍后联系')
    expect(appendQuickNote('客户在开会', '稍后联系')).toBe('客户在开会 稍后联系')
  })

  it('calculates quick follow-up days from the current local time', () => {
    expect(addFollowUpDays(new Date(2026, 7, 9, 10, 30), 3))
      .toEqual(new Date(2026, 7, 12, 10, 30))
  })

  it('blocks lead switching only while the form is dirty', () => {
    expect(shouldBlockLeadSwitch(true)).toBe(true)
    expect(shouldBlockLeadSwitch(false)).toBe(false)
  })

  it('opens customer details on overview unless a follow-up task requested the form', () => {
    expect(defaultLeadDetailTab(false)).toBe('overview')
    expect(defaultLeadDetailTab(true)).toBe('follow-ups')
  })
})

describe('snake timeline layout', () => {
  it('picks the column count from the container width', () => {
    expect(snakeColumnsForWidth(380)).toBe(1)
    expect(snakeColumnsForWidth(500)).toBe(1)
    expect(snakeColumnsForWidth(501)).toBe(2)
    expect(snakeColumnsForWidth(800)).toBe(2)
    expect(snakeColumnsForWidth(801)).toBe(3)
  })

  it('chunks records into rows and leaves the trailing row short', () => {
    expect(chunkSnakeRows([1, 2, 3, 4, 5], 3)).toEqual([[1, 2, 3], [4, 5]])
    expect(chunkSnakeRows([1, 2, 3], 3)).toEqual([[1, 2, 3]])
    // fewer records than a row: one short row, so no inter-row connector
    expect(chunkSnakeRows([1, 2], 3)).toEqual([[1, 2]])
    expect(chunkSnakeRows([], 3)).toEqual([])
  })

  it('alternates row direction except in a single column', () => {
    expect(snakeRowReversed(0, 3)).toBe(false)
    expect(snakeRowReversed(1, 3)).toBe(true)
    expect(snakeRowReversed(2, 3)).toBe(false)
    // a one-column grid is a plain stack; reversing it would invert nothing
    expect(snakeRowReversed(1, 1)).toBe(false)
  })

  it('filters loaded records by method and result independently', () => {
    const records = [
      { method: 'phone', result: 'interested' },
      { method: 'wechat', result: 'interested' },
      { method: 'phone', result: 'rejected' }
    ]
    expect(filterFollowUps(records)).toHaveLength(3)
    expect(filterFollowUps(records, 'phone')).toHaveLength(2)
    expect(filterFollowUps(records, undefined, 'interested')).toHaveLength(2)
    expect(filterFollowUps(records, 'phone', 'interested')).toEqual([records[0]])
    expect(filterFollowUps(records, 'email')).toEqual([])
  })
})
