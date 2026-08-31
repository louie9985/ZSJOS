import { describe, expect, it } from 'vitest'
import { dispatchActionLabel, dispatchModeLabel, isDispatchPageActive, resolveDispatchWarning } from './salesDispatch'

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

  it('prioritizes load errors, offline state, then paused preference', () => {
    expect(resolveDispatchWarning(accepting, false, '状态读取失败', true)).toEqual({ kind: 'error', message: '状态读取失败' })
    expect(resolveDispatchWarning(accepting, false, '', false)).toEqual({ kind: 'offline', message: '当前已【页面离线】，自动派单无法接收' })
    expect(resolveDispatchWarning({ ...accepting, mode: 'paused' }, false, '', true)).toEqual({ kind: 'paused', message: '当前已【暂停接单】，自动派单无法接收' })
    expect(resolveDispatchWarning(accepting, false, '', true)).toBeUndefined()
    expect(resolveDispatchWarning({ ...accepting, eligible: false }, false, '', false)).toBeUndefined()
  })
})
