import { describe, expect, it } from 'vitest'
import { STORAGE_KEYS } from '../constants'
import { buildWebSocketUrl, getRealtimeAccessToken, parseRealtimeMessage } from './realtime'

describe('buildWebSocketUrl', () => {
  it('keeps the websocket endpoint outside the admin API prefix', () => {
    expect(buildWebSocketUrl('/admin-api', 'http://127.0.0.1:5174', 'access value')).toBe('ws://127.0.0.1:5174/infra/ws?token=access+value')
  })

  it('uses secure websocket for an HTTPS API', () => {
    expect(buildWebSocketUrl('https://api.example.com/admin-api', 'http://localhost', 'token')).toBe('wss://api.example.com/infra/ws?token=token')
  })
})

describe('getRealtimeAccessToken', () => {
  it('uses the access token rather than the refresh token for websocket authentication', () => {
    const values = new Map<string, string>([
      [STORAGE_KEYS.ACCESS_TOKEN, 'access-token'],
      [STORAGE_KEYS.REFRESH_TOKEN, 'refresh-token']
    ])
    expect(getRealtimeAccessToken({ getItem: (key) => values.get(key) ?? null })).toBe('access-token')
  })
})

describe('parseRealtimeMessage', () => {
  it('parses the nested JSON content used by the backend', () => {
    expect(parseRealtimeMessage('{"type":"notify-message-new","content":"{\\"messageId\\":12}"}')).toEqual({ type: 'notify-message-new', content: { messageId: 12 } })
  })

  it('ignores heartbeat and malformed payloads', () => {
    expect(parseRealtimeMessage('pong')).toBeNull()
    expect(parseRealtimeMessage('not-json')).toBeNull()
  })
})
