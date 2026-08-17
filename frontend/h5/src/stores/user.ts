import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getToken, setToken, removeToken, getRefreshToken, setRefreshToken, removeRefreshToken,
  setClientId, removeClientId
} from '@/utils/storage'

export const useUserStore = defineStore('user', () => {
  const accessToken = ref(getToken())
  const refreshToken = ref(getRefreshToken())
  const userId = ref<number>()
  const nickname = ref('')
  const avatar = ref('')
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => !!accessToken.value)

  function setTokens(access: string, refresh: string, clientId?: string) {
    accessToken.value = access
    refreshToken.value = refresh
    setToken(access)
    setRefreshToken(refresh)
    if (clientId) setClientId(clientId)
  }

  function setUserInfo(info: { userId: number; nickname: string; avatar?: string; permissions?: string[] }) {
    userId.value = info.userId
    nickname.value = info.nickname
    avatar.value = info.avatar || ''
    permissions.value = info.permissions || []
  }

  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    userId.value = undefined
    nickname.value = ''
    avatar.value = ''
    permissions.value = []
    removeToken()
    removeRefreshToken()
    removeClientId()
  }

  return {
    accessToken,
    refreshToken,
    userId,
    nickname,
    avatar,
    permissions,
    isLoggedIn,
    setTokens,
    setUserInfo,
    logout
  }
})
