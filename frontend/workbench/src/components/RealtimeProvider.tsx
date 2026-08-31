import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { APP_CONFIG, type AuthPlatform } from '../constants'
import { buildWebSocketUrl, getRealtimeAccessToken, parseRealtimeMessage, type RealtimeMessage, type RealtimeStatus } from '../services/realtime'

type RealtimeHandler = (message: RealtimeMessage) => void

type RealtimeContextValue = {
  status: RealtimeStatus
  subscribe: (type: string, handler: RealtimeHandler) => () => void
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null)

export function RealtimeProvider({ children, platform }: PropsWithChildren<{ platform: AuthPlatform }>) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const listeners = useRef(new Map<string, Set<RealtimeHandler>>())

  const subscribe = useCallback((type: string, handler: RealtimeHandler) => {
    const handlers = listeners.current.get(type) ?? new Set<RealtimeHandler>()
    handlers.add(handler)
    listeners.current.set(type, handlers)
    return () => {
      handlers.delete(handler)
      if (handlers.size === 0) listeners.current.delete(type)
    }
  }, [])

  useEffect(() => {
    let active = true
    let socket: WebSocket | undefined
    let reconnectTimer: number | undefined
    let heartbeatTimer: number | undefined
    let retryAttempt = 0

    const clearHeartbeat = () => {
      window.clearInterval(heartbeatTimer)
      heartbeatTimer = undefined
    }
    const connect = () => {
      const accessToken = getRealtimeAccessToken(localStorage, platform)
      if (!active || !accessToken) {
        setStatus('closed')
        return
      }
      setStatus('connecting')
      socket = new WebSocket(buildWebSocketUrl(APP_CONFIG.API_BASE_URL, window.location.origin, accessToken))
      socket.onopen = () => {
        retryAttempt = 0
        setStatus('open')
        socket?.send('ping')
        clearHeartbeat()
        heartbeatTimer = window.setInterval(() => {
          if (socket?.readyState === WebSocket.OPEN) socket.send('ping')
        }, 30_000)
      }
      socket.onmessage = event => {
        const message = parseRealtimeMessage(String(event.data))
        if (!message) return
        listeners.current.get(message.type)?.forEach(handler => handler(message))
      }
      socket.onerror = () => socket?.close()
      socket.onclose = () => {
        clearHeartbeat()
        setStatus('closed')
        if (!active) return
        const delay = Math.min(30_000, 1_000 * 2 ** Math.min(retryAttempt++, 5))
        reconnectTimer = window.setTimeout(connect, delay)
      }
    }

    connect()
    return () => {
      active = false
      window.clearTimeout(reconnectTimer)
      clearHeartbeat()
      socket?.close()
    }
  }, [platform])

  const value = useMemo(() => ({ status, subscribe }), [status, subscribe])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtime() {
  const context = useContext(RealtimeContext)
  if (!context) throw new Error('useRealtime must be used inside RealtimeProvider')
  return context
}

export function useRealtimeEvent(type: string, handler: RealtimeHandler) {
  const { subscribe } = useRealtime()
  const handlerRef = useRef(handler)
  handlerRef.current = handler
  useEffect(() => subscribe(type, message => handlerRef.current(message)), [subscribe, type])
}
