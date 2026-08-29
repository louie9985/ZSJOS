<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTheme, THEMES } from '@/composables/useTheme'
import { getPartnerMe, getProfile } from '@/api/profile'
import { getCashbackSummary, type CashbackSummary } from '@/api/cashback'
import { logout } from '@/api/auth'
import { formatAmount, maskMobile } from '@/utils/format'
import { showConfirmDialog, showToast } from 'vant'

defineOptions({ name: 'Profile' })

const router = useRouter()
const userStore = useUserStore()
const { currentTheme } = useTheme()
const currentThemeInfo = computed(() => THEMES.find(t => t.key === currentTheme()) || THEMES[0])

const profileLoading = ref(true)
const profileError = ref('')
const profile = ref<{ nickname: string; mobile: string; avatar?: string }>()
const partner = ref<{ name: string; mobile: string }>()
const summary = ref<CashbackSummary>()
const summaryLoading = ref(true)
const summaryError = ref('')

const accountEntries = computed(() => [
  { title: '个人信息', icon: 'contact', to: '/profile/edit' },
  { title: '提现记录', icon: 'underway-o', to: '/withdrawal', show: userStore.hasPermission('zsjos:withdrawal:my-query') },
  { title: '银行卡管理', icon: 'credit-pay', to: '/profile/bank-cards', show: userStore.hasPermission('zsjos:withdrawal:apply') },
  { title: '修改密码', icon: 'lock', to: '/profile/password' }
].filter(item => item.show !== false))

const otherEntries = computed(() => [
  { title: '消息通知', icon: 'bell', to: '/messages', badge: 37 },
  { title: '主题设置', icon: 'fire-o', to: '/profile/theme', value: currentThemeInfo.value.label },
  { title: '系统反馈', icon: 'comment-o', to: '/feedback' },
  { title: '投诉记录', icon: 'records-o', to: '/complaints', show: userStore.hasPermission('zsjos:lead-complaint:create') },
  { title: '版本控制与更新', icon: 'info-o', to: '/profile/theme', value: 'v0.1.0' }
].filter(item => item.show !== false))

const avatarFallback = computed(() => (profile.value?.nickname || userStore.nickname || '我').trim()[0] || '我')
const identityName = computed(() => profile.value?.nickname || userStore.nickname || partner.value?.name || '兼职伙伴')
const identitySubtitle = computed(() => maskMobile(profile.value?.mobile || partner.value?.mobile || ''))

async function loadProfile() {
  profileLoading.value = true
  profileError.value = ''
  try {
    const [profileData, partnerData] = await Promise.all([getProfile(), getPartnerMe()])
    profile.value = profileData
    partner.value = partnerData
  } catch (cause) {
    profileError.value = cause instanceof Error ? cause.message : '资料加载失败'
  } finally {
    profileLoading.value = false
  }
}

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await getCashbackSummary()
  } catch (cause) {
    summaryError.value = cause instanceof Error ? cause.message : '收益加载失败'
  } finally {
    summaryLoading.value = false
  }
}

onMounted(() => {
  void loadProfile()
  void loadSummary()
})

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

function goWithdraw() {
  router.push('/withdrawal/apply')
}
</script>

<template>
  <div class="page-container profile-page">
    <van-nav-bar title="我的" />

    <section class="card profile-hero">
      <div class="profile-hero__top">
        <div class="profile-hero__identity">
          <div class="profile-avatar">
            <van-image
              round
              width="42"
              height="42"
              :src="profile?.avatar || userStore.avatar || ''"
              fit="cover"
            >
              <template #error>
                <div class="profile-avatar__fallback">{{ avatarFallback }}</div>
              </template>
            </van-image>
          </div>
          <div class="profile-hero__text">
            <div class="profile-hero__name">{{ identityName }}</div>
            <div class="profile-hero__mobile">{{ identitySubtitle }}</div>
          </div>
        </div>
        <span class="profile-theme-chip">{{ currentThemeInfo.label }}</span>
      </div>

      <van-skeleton :loading="summaryLoading" :row="2">
        <div v-if="summaryError" class="profile-summary__error">
          <span>{{ summaryError }}</span>
          <van-button size="small" type="primary" round @click="loadSummary">重试</van-button>
        </div>
        <template v-else>
          <div class="profile-hero__panel">
            <div class="profile-hero__label">可提现金额</div>
            <div class="profile-hero__amount">¥{{ formatAmount(summary?.availableAmount) }}</div>
            <van-button
              v-if="userStore.hasPermission('zsjos:withdrawal:apply')"
              type="primary"
              round
              size="small"
              class="profile-hero__withdraw"
              @click="goWithdraw"
            >
              提现
            </van-button>
          </div>

          <div class="profile-stats">
            <div class="profile-stat">
              <div class="profile-stat__value">¥{{ formatAmount(summary?.totalAmount) }}</div>
              <div class="profile-stat__label">总收益</div>
            </div>
            <div class="profile-stat">
              <div class="profile-stat__value">¥{{ formatAmount(summary?.pendingAmount) }}</div>
              <div class="profile-stat__label">待结算</div>
            </div>
          </div>
        </template>
      </van-skeleton>
    </section>

    <section class="page-section">
      <div class="page-section__head profile-section__head">
        <div class="page-section__title">账户与账单</div>
      </div>
      <van-cell-group class="card profile-group" :border="false">
        <van-cell
          v-for="item in accountEntries"
          :key="item.title"
          :title="item.title"
          :icon="item.icon"
          is-link
          :to="item.to"
        />
      </van-cell-group>
    </section>

    <section class="page-section">
      <div class="page-section__head profile-section__head">
        <div class="page-section__title">其他</div>
      </div>
      <van-cell-group class="card profile-group" :border="false">
        <van-cell
          v-for="item in otherEntries"
          :key="item.title"
          :title="item.title"
          :icon="item.icon"
          is-link
          :to="item.to"
        >
          <template v-if="item.badge" #value>
            <span class="profile-badge">{{ item.badge }}</span>
          </template>
          <template v-else-if="item.value" #value>
            <span class="profile-meta">{{ item.value }}</span>
          </template>
        </van-cell>
      </van-cell-group>
    </section>

    <div class="profile-actions">
      <van-button block round plain type="danger" @click="handleLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding-bottom: 88px;
  background: var(--h5-bg);
}

.profile-hero {
  margin-top: 12px;
  padding: 14px 16px 16px;
  background:
    radial-gradient(circle at 100% 0, var(--h5-primary-opacity) 0, var(--h5-primary-opacity) 62px, transparent 63px),
    var(--h5-card-bg);
}

.profile-hero__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.profile-hero__identity {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-avatar {
  flex-shrink: 0;
}

.profile-avatar__fallback {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 16px;
  font-weight: 700;
}

.profile-hero__text {
  min-width: 0;
}

.profile-hero__name {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-hero__mobile {
  margin-top: 4px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.35;
}

.profile-theme-chip {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 12px;
  font-weight: 600;
}

.profile-hero__panel {
  position: relative;
  margin-top: 14px;
  padding: 16px 14px 18px;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 18%, transparent);
  border-radius: 16px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--h5-primary) 5%, var(--h5-card-bg)),
    color-mix(in srgb, var(--h5-primary) 2%, var(--h5-card-bg))
  );
  overflow: hidden;
}

.profile-hero__panel::after {
  content: '';
  position: absolute;
  right: -34px;
  top: -34px;
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: var(--h5-primary-opacity);
}

.profile-hero__label {
  position: relative;
  z-index: 1;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.3;
}

.profile-hero__amount {
  position: relative;
  z-index: 1;
  margin-top: 6px;
  color: var(--h5-text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.profile-hero__withdraw {
  position: absolute;
  right: 14px;
  bottom: 16px;
  z-index: 1;
  height: 34px;
  padding: 0 16px;
  background: linear-gradient(135deg, var(--h5-primary), var(--h5-primary-dark));
  border: 0;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.profile-stat {
  padding: 12px 12px 10px;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 14%, transparent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--h5-card-bg) 92%, var(--h5-primary) 8%);
}

.profile-stat__value {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.profile-stat__label {
  margin-top: 6px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.2;
}

.profile-summary__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--h5-card-bg) 92%, var(--h5-primary) 8%);
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.profile-group {
  overflow: hidden;
}

.profile-group :deep(.van-cell) {
  min-height: 50px;
}

.profile-group :deep(.van-cell__value) {
  flex-shrink: 0;
}

.profile-section__head {
  margin: 0 16px;
}

.profile-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--h5-primary);
  color: #fff;
  font-size: 12px;
  line-height: 1;
}

.profile-meta {
  font-size: 12px;
  color: var(--h5-text-secondary);
}

.profile-actions {
  padding: 24px 16px 0;
}
</style>
