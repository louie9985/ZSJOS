<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { useAuth } from '@/composables/useAuth'
import { isInWecom, redirectToWecomOAuth } from '@/utils/wecom'

const router = useRouter()
const route = useRoute()
const { loading, error, loginWithPassword, initAuth } = useAuth()

const username = ref('')
const password = ref('')
const wecomLoading = ref(false)

// 获取登录后跳转地址
const redirectPath = (route.query.redirect as string) || '/home'

onMounted(async () => {
  // 如果在企微环境且 URL 带 code，尝试自动登录
  const success = await initAuth()
  if (success) {
    router.replace(redirectPath)
  }
})

async function handleLogin() {
  if (!username.value.trim()) return showToast('请输入账号')
  if (!password.value) return showToast('请输入密码')

  const success = await loginWithPassword(username.value.trim(), password.value)
  if (success) {
    router.replace(redirectPath)
  }
}

function handleWecomLogin() {
  // 企微 OAuth 跳转（需要后端配置 corpId 和 agentId）
  // 暂用占位，实际上线需要从配置获取
  const corpId = import.meta.env.VITE_WECOM_CORP_ID || ''
  const agentId = import.meta.env.VITE_WECOM_AGENT_ID || ''
  if (!corpId) {
    showToast('企微配置未就绪')
    return
  }
  const redirectUri = window.location.origin + '/home'
  redirectToWecomOAuth(corpId, agentId, redirectUri)
}
</script>

<template>
  <div class="login-page">
    <div class="login-header">
      <div class="login-header__logo">
        <van-icon name="shop-o" size="48" color="var(--h5-primary)" />
      </div>
      <div class="login-header__title">中世健兼职</div>
      <div class="login-header__subtitle">登录您的账号开始工作</div>
    </div>

    <div class="login-form">
      <van-cell-group inset>
        <van-field
          v-model="username"
          placeholder="请输入账号"
          left-icon="user-o"
          clearable
          autocomplete="username"
        />
        <van-field
          v-model="password"
          type="password"
          placeholder="请输入密码"
          left-icon="lock"
          clearable
          autocomplete="current-password"
          @keyup.enter="handleLogin"
        />
      </van-cell-group>

      <div class="login-actions">
        <van-button
          type="primary"
          block
          round
          :loading="loading"
          loading-text="登录中..."
          @click="handleLogin"
        >
          登录
        </van-button>

        <van-button
          v-if="isInWecom()"
          block
          round
          plain
          :loading="wecomLoading"
          class="login-wecom-btn"
          @click="handleWecomLogin"
        >
          企业微信一键登录
        </van-button>
      </div>

      <div v-if="error" class="login-error">{{ error }}</div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  background: var(--h5-bg);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}
.login-header__logo {
  margin-bottom: 12px;
}
.login-header__title {
  font-size: 26px;
  font-weight: 700;
  color: var(--h5-primary);
}
.login-header__subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: var(--h5-text-secondary);
}

.login-form {
  width: 100%;
}

.login-form :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: 12px;
  overflow: hidden;
}

.login-actions {
  margin-top: 24px;
}
.login-actions .van-button {
  height: 46px;
  font-size: 16px;
}
.login-wecom-btn {
  margin-top: 12px;
  color: var(--h5-primary);
  border-color: var(--h5-primary);
}

.login-error {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--h5-danger);
}
</style>
