import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login as loginApi, getPermissionInfo } from '@/api/auth'
import { isInWecom, getWecomCodeFromUrl } from '@/utils/wecom'
import request from '@/api/request'

/**
 * 认证 composable — 封装企微 OAuth + 密码登录双路径
 */
export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(false)
  const error = ref('')

  /**
   * 初始化认证：
   * 1. 已有 token → 尝试获取用户信息
   * 2. 企微环境且 URL 带 code → 用 code 换 token
   * 3. 否则需要登录
   */
  async function initAuth(): Promise<boolean> {
    // 已有 token，验证有效性
    if (userStore.isLoggedIn) {
      try {
        await fetchUserInfo()
        return true
      } catch {
        userStore.logout()
      }
    }

    // 企微环境，检查 URL 中的 OAuth code
    if (isInWecom()) {
      const code = getWecomCodeFromUrl()
      if (code) {
        return await loginWithWecomCode(code)
      }
    }

    return false
  }

  /**
   * 企微 OAuth code 换 token
   */
  async function loginWithWecomCode(code: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await request.post<never, { userId: number; accessToken: string; refreshToken: string }>(
        '/zsjos/auth/wecom-login',
        { code }
      )
      userStore.setTokens(result.accessToken, result.refreshToken)
      userStore.setUserInfo({ userId: result.userId, nickname: '' })
      await fetchUserInfo()
      // 清除 URL 中的 code 参数
      const url = new URL(window.location.href)
      url.searchParams.delete('code')
      url.searchParams.delete('state')
      window.history.replaceState({}, '', url.toString())
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '企微登录失败'
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 账号密码登录
   */
  async function loginWithPassword(username: string, password: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await loginApi({ username, password, platform: 'MOBILE' })
      userStore.setTokens(result.accessToken, result.refreshToken)
      userStore.setUserInfo({ userId: result.userId, nickname: username })
      await fetchUserInfo()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '登录失败'
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取用户信息和权限
   */
  async function fetchUserInfo() {
    const info = await getPermissionInfo()
    userStore.setUserInfo({
      userId: info.user.id,
      nickname: info.user.nickname,
      avatar: info.user.avatar,
      permissions: info.permissions
    })
  }

  /**
   * 退出登录
   */
  function doLogout() {
    userStore.logout()
    router.replace('/login')
  }

  return {
    loading,
    error,
    initAuth,
    loginWithWecomCode,
    loginWithPassword,
    fetchUserInfo,
    doLogout
  }
}
