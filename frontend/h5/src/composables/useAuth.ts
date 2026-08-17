import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login as loginApi, getPermissionInfo } from '@/api/auth'

/**
 * 兼职端本期仅支持账号密码登录。
 */
export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(false)
  const error = ref('')

  /**
   * 初始化认证：
   * 1. 已有 token → 尝试获取用户信息
   * 2. 否则需要登录
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

    return false
  }

  /**
   * 账号密码登录
   */
  async function loginWithPassword(username: string, password: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await loginApi({ username, password, platform: 'MOBILE' })
      userStore.setTokens(result.accessToken, result.refreshToken, result.clientId)
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
    loginWithPassword,
    fetchUserInfo,
    doLogout
  }
}
