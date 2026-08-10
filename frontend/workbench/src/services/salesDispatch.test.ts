import { describe, expect, it } from 'vitest'
import { dispatchActionLabel, dispatchModeLabel, isDispatchPageActive } from './salesDispatch'

const accepting = {
  eligible: true,
  presence: 'online' as const,
  mode: 'accepting' as const,
  effectiveStatus: 'online' as const
}

describe('sales dispatch status', () => {
  it('requires websocket, browser, and backend presence to all be online', () => {
    expect(isDispatchPageActive('open', true, accepting)).toBe(true)
    expect(isDispatchPageActive('closed', true, accepting)).toBe(false)
    expect(isDispatchPageActive('open', false, accepting)).toBe(false)
    expect(isDispatchPageActive('open', true, { ...accepting, presence: 'offline' })).toBe(false)
  })

  it('maps the saved preference to stable labels and inverse action', () => {
    expect(dispatchModeLabel(accepting)).toBe('接单开启')
    expect(dispatchActionLabel(accepting)).toBe('暂停接单')
    expect(dispatchModeLabel({ ...accepting, mode: 'paused' })).toBe('接单暂停')
    expect(dispatchActionLabel({ ...accepting, mode: 'paused' })).toBe('开启接单')
  })
})
