<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const route = useRoute()
const { loading, error, loginWithPassword, initAuth } = useAuth()

const mobile = ref('')
const password = ref('')

// 获取登录后跳转地址
const redirectPath = (route.query.redirect as string) || '/home'

onMounted(async () => {
  const success = await initAuth()
  if (success) {
    router.replace(redirectPath)
  }
})

async function handleLogin() {
  if (!/^1\d{10}$/.test(mobile.value.trim())) return showToast('请输入正确的手机号')
  if (!password.value) return showToast('请输入密码')

  const success = await loginWithPassword(mobile.value.trim(), password.value)
  if (success) {
    router.replace(redirectPath)
  }
}

function handleWecomLogin() {
  showToast('企业微信登录暂未开放，请使用账号密码登录')
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

        <van-button block round plain class="login-wecom-btn" @click="handleWecomLogin">
          企业微信登录（暂未开放）
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
