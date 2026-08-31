<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { useAuth } from '@/composables/useAuth'
import { wecomAuthorizeUrl } from '@/api/auth'
import { isInWecom } from '@/utils/wecom'

const router = useRouter()
const route = useRoute()
const { loading, error, loginWithPassword, activateWithInvite, loginWithWecom, initAuth } = useAuth()
const wecomAutoLoginKey = 'zsjos_h5_wecom_auto_login_started'

const mobile = ref('')
const password = ref('')
const mode = ref<'login' | 'activate'>('login')
const activateMobile = ref('')
const activatePassword = ref('')
const activateConfirmPassword = ref('')
const inviteCode = ref('')

// 获取登录后跳转地址
const redirectPath = () => (route.query.redirect as string) || '/home'

function buildWecomRedirectUri() {
  const url = new URL('/login', window.location.origin)
  url.searchParams.set('redirect', redirectPath())
  return url.toString()
}

function shouldAutoStartWecomLogin() {
  return isInWecom()
    && !(route.query.code && route.query.state)
    && window.sessionStorage.getItem(wecomAutoLoginKey) !== '1'
}

function clearWecomAutoLoginMarker() {
  window.sessionStorage.removeItem(wecomAutoLoginKey)
}

onMounted(async () => {
  const code = (route.query.code as string | undefined)?.trim()
  const state = (route.query.state as string | undefined)?.trim()
  if (code && state) {
    clearWecomAutoLoginMarker()
    const success = await loginWithWecom(code, state)
    if (success) {
      router.replace(redirectPath())
    }
    return
  }

  const success = await initAuth()
  if (success) {
    router.replace(redirectPath())
    return
  }

  if (shouldAutoStartWecomLogin()) {
    await handleWecomLogin()
  }
})

async function handleLogin() {
  if (!/^1\d{10}$/.test(mobile.value.trim())) return showToast('请输入正确的手机号')
  if (!password.value) return showToast('请输入密码')

  const success = await loginWithPassword(mobile.value.trim(), password.value)
  if (success) {
    router.replace(redirectPath())
  } else if (error.value.includes('尚未激活')) {
    activateMobile.value = mobile.value.trim()
    mode.value = 'activate'
  }
}

async function handleActivate() {
  const normalizedMobile = activateMobile.value.trim()
  const normalizedCode = inviteCode.value.trim().toUpperCase()
  if (!/^1\d{10}$/.test(normalizedMobile)) return showToast('请输入正确的手机号')
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(activatePassword.value)) {
    return showToast('请输入 8-20 位且包含字母和数字的密码')
  }
  if (activatePassword.value !== activateConfirmPassword.value) return showToast('两次输入的密码不一致')
  if (!/^[A-Z]{4}\d{4}$/.test(normalizedCode)) return showToast('请输入四位字母加四位数字的邀请码')

  const success = await activateWithInvite(
    normalizedMobile,
    activatePassword.value,
    activateConfirmPassword.value,
    normalizedCode
  )
  if (success) {
    router.replace(redirectPath())
  }
}

async function handleWecomLogin() {
  try {
    window.sessionStorage.setItem(wecomAutoLoginKey, '1')
    window.location.href = await wecomAuthorizeUrl(buildWecomRedirectUri())
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '企业微信授权失败')
  }
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
      <div class="login-mode">
        <van-button
          size="small"
          :type="mode === 'login' ? 'primary' : 'default'"
          round
          @click="mode = 'login'"
        >
          登录
        </van-button>
        <van-button
          size="small"
          :type="mode === 'activate' ? 'primary' : 'default'"
          round
          @click="mode = 'activate'"
        >
          首次登录去激活
        </van-button>
      </div>

      <van-cell-group v-if="mode === 'login'" inset>
        <van-field
          v-model="mobile"
          type="tel"
          maxlength="11"
          placeholder="请输入手机号"
          left-icon="user-o"
          clearable
          autocomplete="tel"
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

      <van-cell-group v-else inset>
        <van-field
          v-model="activateMobile"
          type="tel"
          maxlength="11"
          placeholder="请输入手机号"
          left-icon="user-o"
          clearable
          autocomplete="tel"
        />
        <van-field
          v-model="activatePassword"
          type="password"
          maxlength="20"
          placeholder="设置新密码"
          left-icon="lock"
          clearable
          autocomplete="new-password"
        />
        <van-field
          v-model="activateConfirmPassword"
          type="password"
          maxlength="20"
          placeholder="确认新密码"
          left-icon="passed"
          clearable
          autocomplete="new-password"
        />
        <van-field
          v-model="inviteCode"
          maxlength="8"
          placeholder="请输入邀请码"
          left-icon="coupon-o"
          clearable
          @update:model-value="inviteCode = String($event).toUpperCase()"
          @keyup.enter="handleActivate"
        />
      </van-cell-group>

      <div class="login-actions" v-if="mode === 'login'">
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

        <van-button block round plain class="login-wecom-btn" :loading="loading" @click="handleWecomLogin">
          企业微信登录
        </van-button>

      </div>

      <div class="login-actions" v-else>
        <van-button
          type="primary"
          block
          round
          :loading="loading"
          loading-text="激活中..."
          @click="handleActivate"
        >
          激活并登录
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

.login-mode {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
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
  color: var(--h5-text-secondary);
  border-color: var(--h5-divider);
}

.login-error {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--h5-danger);
}
</style>
