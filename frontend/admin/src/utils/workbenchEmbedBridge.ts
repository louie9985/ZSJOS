import type { RouteLocationNormalizedLoaded } from 'vue-router'
import router from '@/router'
import { useTagsViewStoreWithOut } from '@/store/modules/tagsView'
import { isWorkbenchEmbed } from '@/utils/workbenchEmbed'

export const ADMIN_EMBED_MESSAGE = {
  READY: 'zsjos:admin-embed:ready',
  NAVIGATE: 'zsjos:admin-embed:navigate',
  ROUTE_CHANGED: 'zsjos:admin-embed:route-changed',
  CLOSE_ROUTE: 'zsjos:admin-embed:close-route'
} as const

type WorkbenchCommand =
  | { type: typeof ADMIN_EMBED_MESSAGE.NAVIGATE; path: string }
  | { type: typeof ADMIN_EMBED_MESSAGE.CLOSE_ROUTE; path: string }

const isInternalPath = (path: string) => path.startsWith('/') && !path.startsWith('//')

export const isWorkbenchCommand = (data: unknown): data is WorkbenchCommand => {
  if (!data || typeof data !== 'object') return false
  const message = data as Record<string, unknown>
  return (
    (message.type === ADMIN_EMBED_MESSAGE.NAVIGATE ||
      message.type === ADMIN_EMBED_MESSAGE.CLOSE_ROUTE) &&
    typeof message.path === 'string' &&
    isInternalPath(message.path)
  )
}

/** 注册 Workbench 单 iframe 与 Vue Router 之间的同源路由桥。 */
export const setupWorkbenchEmbedBridge = () => {
  if (!isWorkbenchEmbed() || window.parent === window) return

  const parentWindow = window.parent
  const tagsViewStore = useTagsViewStoreWithOut()
  const postToWorkbench = (message: Record<string, string>) => {
    parentWindow.postMessage(message, window.location.origin)
  }
  const rememberRoute = (route: RouteLocationNormalizedLoaded) => {
    if (!route.name) return
    tagsViewStore.setSelectedTag(route)
    tagsViewStore.addView(route)
  }
  const forgetRoute = (path: string) => {
    const views = tagsViewStore.getVisitedViews.filter((view) => view.path === path)
    views.forEach((view) => tagsViewStore.delVisitedView(view))
    tagsViewStore.addCachedView()
  }

  const removeAfterEach = router.afterEach((to) => {
    rememberRoute(to)
    postToWorkbench({ type: ADMIN_EMBED_MESSAGE.ROUTE_CHANGED, path: to.path })
  })

  const handleMessage = (event: MessageEvent) => {
    if (
      event.origin !== window.location.origin ||
      event.source !== parentWindow ||
      !isWorkbenchCommand(event.data)
    )
      return

    if (event.data.type === ADMIN_EMBED_MESSAGE.CLOSE_ROUTE) {
      forgetRoute(event.data.path)
      return
    }

    if (router.currentRoute.value.path !== event.data.path) {
      void router.push(event.data.path)
    }
  }

  window.addEventListener('message', handleMessage)
  rememberRoute(router.currentRoute.value)
  postToWorkbench({
    type: ADMIN_EMBED_MESSAGE.READY,
    path: router.currentRoute.value.path
  })

  return () => {
    window.removeEventListener('message', handleMessage)
    removeAfterEach()
  }
}
