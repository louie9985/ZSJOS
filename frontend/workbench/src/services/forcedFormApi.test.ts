import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, AuthenticationError, FORCED_FORM_REQUIRED_CODE, unwrap } from './api'

class TestCustomEvent<T = unknown> {
  readonly type: string
  readonly detail: T

  constructor(type: string, init?: { detail?: T }) {
    this.type = type
    this.detail = init?.detail as T
  }
}

describe('forced form API blocking contract', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('emits a forced-form event and keeps the business error out of the auth-expiry path', () => {
    const dispatchEvent = vi.fn()
    vi.stubGlobal('CustomEvent', TestCustomEvent)
    vi.stubGlobal('window', { dispatchEvent })

    const pending = [{ id: 9, name: '入职确认' }]
    expect(() =>
      unwrap({
        data: {
          code: FORCED_FORM_REQUIRED_CODE,
          msg: '存在必须完成的强制表单',
          data: pending,
        },
      }),
    ).toThrow(ApiError)

    try {
      unwrap({
        data: {
          code: FORCED_FORM_REQUIRED_CODE,
          msg: '存在必须完成的强制表单',
          data: pending,
        },
      })
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).not.toBeInstanceOf(AuthenticationError)
    }

    expect(dispatchEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'zsjos-forced-form-required',
        detail: pending,
      }),
    )
  })
})
