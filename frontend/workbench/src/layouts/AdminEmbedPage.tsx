import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef
} from 'react'
import { APP_CONFIG } from '../constants'

export const ADMIN_EMBED_MESSAGE = {
  READY: 'zsjos:admin-embed:ready',
  NAVIGATE: 'zsjos:admin-embed:navigate',
  ROUTE_CHANGED: 'zsjos:admin-embed:route-changed',
  CLOSE_ROUTE: 'zsjos:admin-embed:close-route'
} as const

type AdminEmbedResponse =
  | { type: typeof ADMIN_EMBED_MESSAGE.READY; path: string }
  | { type: typeof ADMIN_EMBED_MESSAGE.ROUTE_CHANGED; path: string }

type AdminEmbedCommand =
  | { type: typeof ADMIN_EMBED_MESSAGE.NAVIGATE; path: string }
  | { type: typeof ADMIN_EMBED_MESSAGE.CLOSE_ROUTE; path: string }

export interface AdminEmbedFrameHandle {
  closeRoute: (path: string) => void
}

export function isAdminEmbedResponse(data: unknown): data is AdminEmbedResponse {
  if (!data || typeof data !== 'object') return false
  const message = data as Record<string, unknown>
  return (
    (message.type === ADMIN_EMBED_MESSAGE.READY ||
      message.type === ADMIN_EMBED_MESSAGE.ROUTE_CHANGED) &&
    typeof message.path === 'string'
  )
}

export function buildAdminEmbedUrl(path: string) {
  const base = APP_CONFIG.ADMIN_EMBED_BASE.endsWith('/')
    ? APP_CONFIG.ADMIN_EMBED_BASE
    : `${APP_CONFIG.ADMIN_EMBED_BASE}/`
  const relativePath = path.replace(/^\/+/, '')
  return `${base}${relativePath}?embed=workbench`
}

/**
 * 整个 Workbench 会话只创建一个 Admin iframe。首次加载后，页面切换由 Admin
 * 自身的 Vue Router 完成；消息只携带路由，不传递认证信息。
 */
const AdminEmbedFrame = forwardRef<AdminEmbedFrameHandle, {
  activePath?: string
  title?: string
  onRouteChange?: (path: string) => void
}>(function AdminEmbedFrame({ activePath, title = '管理页面', onRouteChange }, ref) {
  const frameRef = useRef<HTMLIFrameElement>(null)
  const initialPathRef = useRef<string | undefined>(undefined)
  const documentPathRef = useRef<string | undefined>(undefined)
  const activePathRef = useRef(activePath)
  const readyRef = useRef(false)
  const loadedRef = useRef(false)
  const pendingClosePathsRef = useRef(new Set<string>())

  activePathRef.current = activePath
  if (!initialPathRef.current && activePath) {
    initialPathRef.current = activePath
    documentPathRef.current = activePath
  }

  const postCommand = useCallback((message: AdminEmbedCommand) => {
    frameRef.current?.contentWindow?.postMessage(message, window.location.origin)
  }, [])

  const navigateDocument = useCallback((path: string) => {
    if (!frameRef.current) return
    loadedRef.current = false
    documentPathRef.current = path
    frameRef.current.src = buildAdminEmbedUrl(path)
  }, [])

  const handleFrameLoad = useCallback(() => {
    loadedRef.current = true
    if (readyRef.current) return
    const targetPath = activePathRef.current
    if (targetPath && targetPath !== documentPathRef.current) {
      navigateDocument(targetPath)
    }
  }, [navigateDocument])

  useImperativeHandle(ref, () => ({
    closeRoute(path: string) {
      if (!initialPathRef.current) return
      if (readyRef.current) {
        postCommand({ type: ADMIN_EMBED_MESSAGE.CLOSE_ROUTE, path })
      } else {
        pendingClosePathsRef.current.add(path)
      }
    }
  }), [postCommand])

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (
        event.origin !== window.location.origin ||
        event.source !== frameRef.current?.contentWindow ||
        !isAdminEmbedResponse(event.data)
      ) return

      if (event.data.type === ADMIN_EMBED_MESSAGE.READY) {
        readyRef.current = true
        documentPathRef.current = event.data.path
        const targetPath = activePathRef.current
        if (targetPath) {
          postCommand({ type: ADMIN_EMBED_MESSAGE.NAVIGATE, path: targetPath })
        }
        pendingClosePathsRef.current.forEach(path => {
          postCommand({ type: ADMIN_EMBED_MESSAGE.CLOSE_ROUTE, path })
        })
        pendingClosePathsRef.current.clear()
        return
      }

      onRouteChange?.(event.data.path)
    }

    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [onRouteChange, postCommand])

  useEffect(() => {
    if (!activePath) return
    if (readyRef.current) {
      postCommand({ type: ADMIN_EMBED_MESSAGE.NAVIGATE, path: activePath })
    } else if (loadedRef.current && activePath !== documentPathRef.current) {
      // 兼容 Admin 尚未部署消息桥的滚动发布窗口，仍只复用一个 iframe 元素。
      navigateDocument(activePath)
    }
  }, [activePath, navigateDocument, postCommand])

  const initialPath = initialPathRef.current
  if (!initialPath) return null

  return (
    <section
      className={`workspace-page admin-embed-page${activePath ? '' : ' admin-embed-page-hidden'}`}
      aria-hidden={!activePath}
    >
      <iframe
        ref={frameRef}
        src={buildAdminEmbedUrl(initialPath)}
        title={title}
        className="admin-embed-frame"
        referrerPolicy="same-origin"
        onLoad={handleFrameLoad}
      />
    </section>
  )
})

export default AdminEmbedFrame
