import { describe, expect, it } from 'vitest'
import { ApiError, normalizeRequestError, SERVER_CONNECTION_ERROR_MESSAGE } from './api'

const axiosError = (status?: number) => Object.assign(new Error('Request failed with status code 502'), {
  isAxiosError: true,
  response: status === undefined ? undefined : { status },
})

describe('server connection error normalization', () => {
  it.each([
    ['network failure', undefined, 0],
    ['bad gateway', 502, 502],
    ['server error', 500, 500],
  ])('maps %s to the stable Chinese message', (_name, status, code) => {
    const normalized = normalizeRequestError(axiosError(status))

    expect(normalized).toBeInstanceOf(ApiError)
    expect(normalized).toMatchObject({
      code,
      message: SERVER_CONNECTION_ERROR_MESSAGE,
    })
  })

  it('keeps non-server HTTP errors available for distinct handling', () => {
    const forbidden = axiosError(403)

    expect(normalizeRequestError(forbidden)).toBe(forbidden)
  })

  it('maps request timeouts to the same connection message', () => {
    const timeout = Object.assign(axiosError(), { code: 'ECONNABORTED' })

    expect(normalizeRequestError(timeout)).toMatchObject({
      code: 0,
      message: SERVER_CONNECTION_ERROR_MESSAGE,
    })
  })

  it('does not report an intentionally cancelled request as a server failure', () => {
    const cancelled = Object.assign(axiosError(), { __CANCEL__: true })

    expect(normalizeRequestError(cancelled)).toBe(cancelled)
  })

  it('does not replace non-Axios business errors', () => {
    const businessError = new ApiError(1_900_000_001, '业务配置无效')

    expect(normalizeRequestError(businessError)).toBe(businessError)
  })
})
