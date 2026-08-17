<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTheme, THEMES } from '@/composables/useTheme'
import { logout } from '@/api/auth'
import { showConfirmDialog, showToast } from 'vant'

defineOptions({ name: 'Profile' })

const router = useRouter()
const userStore = useUserStore()
const { currentTheme } = useTheme()

async function handleLogout() {
  try {
    await showConfirmDialog({ title: '确认退出', message: '确定要退出登录吗？' })
  } catch {
    return
  }

  try {
    await logout()
  } catch {
    // 服务端撤销采用尽力而为，本地会话始终退出。
  } finally {
    userStore.logout()
    await router.replace({ name: 'Login' })
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header-gradient profile-header">
      <van-image
        round
        width="60"
        height="60"
        :src="userStore.avatar || ''"
        fit="cover"
      >
        <template #error>
          <van-icon name="user-o" size="30" color="#fff" />
        </template>
      </van-image>
      <div class="profile-header__info">
        <div class="profile-header__name">{{ userStore.nickname || '兼职伙伴' }}</div>
      </div>
    </div>

    <van-cell-group class="card" :border="false">
      <van-cell title="个人资料" icon="user-o" is-link to="/profile/edit" />
      <van-cell title="修改密码" icon="lock" is-link to="/profile/password" />
    </van-cell-group>

    <van-cell-group class="card" :border="false">
      <van-cell
        v-if="userStore.hasPermission('zsjos:cashback:my-query')"
        title="收益中心"
        icon="gold-coin-o"
        is-link
        to="/earnings"
      />
      <van-cell
        v-if="userStore.hasPermission('zsjos:lead-complaint:create')"
        title="投诉记录"
        icon="records-o"
        is-link
        to="/complaints"
      />
      <van-cell
        v-if="userStore.hasPermission('zsjos:withdrawal:my-query')"
        title="提现记录"
        icon="balance-list-o"
        is-link
        to="/withdrawal"
      />
      <van-cell
        v-if="userStore.hasPermission('zsjos:withdrawal:apply')"
        title="银行卡管理"
        icon="credit-pay"
        is-link
        to="/profile/bank-cards"
      />
    </van-cell-group>

    <van-cell-group class="card" :border="false">
      <van-cell title="主题切换" icon="brush-o" is-link to="/profile/theme">
        <template #value>
          <span style="font-size: 12px;">
            {{ THEMES.find(t => t.key === currentTheme())?.emoji }}
            {{ THEMES.find(t => t.key === currentTheme())?.label }}
          </span>
        </template>
      </van-cell>
    </van-cell-group>

    <div style="padding: 24px 16px;">
      <van-button block round plain type="danger" @click="handleLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 30px 20px;
}
.profile-header__name {
  font-size: 18px;
  font-weight: 600;
}
</style>
