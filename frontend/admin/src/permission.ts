import router from './router'
import type { RouteRecordRaw } from 'vue-router'
import { isRelogin } from '@/config/axios/service'
import { getAccessToken } from '@/utils/auth'
import { useTitle } from '@/hooks/web/useTitle'
import { useNProgress } from '@/hooks/web/useNProgress'
import { usePageLoading } from '@/hooks/web/usePageLoading'
import { useDictStoreWithOut } from '@/store/modules/dict'
import { useUserStoreWithOut } from '@/store/modules/user'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { parseRouteLocation } from '@/utils/routeParams'
import { resolveAuthenticatedRouteNavigation } from '@/utils/authenticatedLanding'

const { start, done } = useNProgress()

const { loadStart, loadDone } = usePageLoading()

// 路由不重定向白名单
const whiteList = [
  '/login',
  '/social-login',
  '/auth-redirect',
  '/bind',
  '/register',
  '/oauthLogin/gitee'
]
const whiteListPrefixes = ['/pms/kb/document/share']

// 路由加载前
router.beforeEach(async (to, from) => {
  start()
  loadStart()
  try {
    if (getAccessToken()) {
      if (to.path === '/login') {
        return { path: '/', replace: true }
      } else {
        const dictStore = useDictStoreWithOut()
        const userStore = useUserStoreWithOut()
        const permissionStore = usePermissionStoreWithOut()
        let routesJustAdded = false
        // 异步加载字典
        // 另外，间接 issue：https://gitee.com/yudaocode/yudao-ui-admin-vue3/issues/ID9FLI
        if (!dictStore.getIsSetDict) {
          dictStore.setDictMap().then()
        }
        if (!userStore.getIsSetUser) {
          isRelogin.show = true
          await userStore.setUserInfoAction()
          isRelogin.show = false
          // 后端过滤菜单
          await permissionStore.generateRoutes()
          permissionStore.getAddRouters.forEach((route) => {
            router.addRoute(route as unknown as RouteRecordRaw) // 动态添加可访问路由表
          })
          routesJustAdded = true
        }
        const redirect = resolveAuthenticatedRouteNavigation({
          currentPath: to.path,
          currentFullPath: to.fullPath,
          explicitRedirect:
            typeof from.query.redirect === 'string' ? from.query.redirect : undefined,
          defaultLandingPath: permissionStore.getDefaultLandingPath,
          routesJustAdded
        })
        if (!redirect) return true
        return { ...parseRouteLocation(redirect), replace: true }
      }
    } else {
      if (
        whiteList.includes(to.path) ||
        whiteListPrefixes.some((path) => to.path === path || to.path.startsWith(`${path}/`))
      ) {
        return true
      } else {
        return `/login?redirect=${encodeURIComponent(to.fullPath)}` // 否则全部重定向到登录页
      }
    }
  } finally {
    isRelogin.show = false
    done()
    loadDone()
  }
})

router.afterEach((to) => {
  useTitle(to?.meta?.title as string)
  done() // 结束Progress
  loadDone()
})
