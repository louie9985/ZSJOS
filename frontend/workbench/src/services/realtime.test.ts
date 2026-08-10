import { describe, expect, it } from 'vitest'
import { buildWebSocketUrl, parseRealtimeMessage } from './realtime'

describe('buildWebSocketUrl', () => {
  it('keeps the websocket endpoint outside the admin API prefix', () => {
    expect(buildWebSocketUrl('/admin-api', 'http://127.0.0.1:5174', 'refresh value'))
      .toBe('ws://127.0.0.1:5174/infra/ws?token=refresh+value')
  })

  it('uses secure websocket for an HTTPS API', () => {
    expect(buildWebSocketUrl('https://api.example.com/admin-api', 'http://localhost', 'token'))
      .toBe('wss://api.example.com/infra/ws?token=token')
  })
})

describe('parseRealtimeMessage', () => {
  it('parses the nested JSON content used by the backend', () => {
    expect(parseRealtimeMessage('{"type":"notify-message-new","content":"{\\"messageId\\":12}"}'))
      .toEqual({ type: 'notify-message-new', content: { messageId: 12 } })
  })

  it('ignores heartbeat and malformed payloads', () => {
    expect(parseRealtimeMessage('pong')).toBeNull()
    expect(parseRealtimeMessage('not-json')).toBeNull()
  })
})
