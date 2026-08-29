import { AUTH_STORAGE_KEYS, type AuthPlatform } from '../constants'

export type RealtimeStatus = 'connecting' | 'open' | 'closed'

export type RealtimeMessage = {
  type: string
  content: unknown
}

export function getRealtimeAccessToken(
  storage: Pick<Storage, 'getItem'>,
  platform: AuthPlatform,
) {
  return storage.getItem(AUTH_STORAGE_KEYS[platform].accessToken)
}

export function buildWebSocketUrl(apiBaseUrl: string, origin: string, accessToken: string) {
  const apiUrl = new URL(apiBaseUrl, origin)
  apiUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  apiUrl.pathname = '/infra/ws'
  apiUrl.search = new URLSearchParams({ token: accessToken }).toString()
  apiUrl.hash = ''
  return apiUrl.toString()
}

export function parseRealtimeMessage(data: string): RealtimeMessage | null {
  if (!data || data === 'pong') return null
  try {
    const envelope = JSON.parse(data) as { type?: unknown; content?: unknown }
    if (typeof envelope.type !== 'string' || !envelope.type) return null
    let content = envelope.content
    if (typeof content === 'string') {
      try {
        content = JSON.parse(content)
      } catch {
        /* Keep plain-text payloads compatible. */
      }
    }
    return { type: envelope.type, content }
  } catch {
    return null
  }
}
