import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  activate as activateApi,
  login as loginApi,
  wecomLogin as wecomLoginApi,
  getPermissionInfo
} from '@/api/auth'

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
  async function loginWithPassword(mobile: string, password: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await loginApi({ mobile, password, platform: 'MOBILE' })
      userStore.setTokens(result.accessToken, result.refreshToken, result.clientId)
      userStore.setUserInfo({ userId: result.userId, nickname: mobile })
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
   * 首次登录邀请码激活
   */
  async function activateWithInvite(
    mobile: string,
    password: string,
    confirmPassword: string,
    inviteCode: string
  ): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await activateApi({
        mobile,
        password,
        confirmPassword,
        inviteCode,
        platform: 'MOBILE'
      })
      userStore.setTokens(result.accessToken, result.refreshToken, result.clientId)
      userStore.setUserInfo({ userId: result.userId, nickname: mobile })
      await fetchUserInfo()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '激活失败'
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 企业微信登录
   */
  async function loginWithWecom(code: string, state: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await wecomLoginApi({ code, state, platform: 'MOBILE' })
      userStore.setTokens(result.accessToken, result.refreshToken, result.clientId)
      userStore.setUserInfo({ userId: result.userId, nickname: '兼职伙伴' })
      await fetchUserInfo()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '企业微信登录失败'
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
    activateWithInvite,
    loginWithWecom,
    fetchUserInfo,
    doLogout
  }
}
