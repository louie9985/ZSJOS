<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTheme, THEMES } from '@/composables/useTheme'
import { bindWecom, getPartnerMe, getProfile, updateNotifyChannel, type PartnerInfo, type UserProfile } from '@/api/profile'
import { logout, wecomAuthorizeUrl } from '@/api/auth'
import { getUnreadCount } from '@/api/message'
import { maskMobile } from '@/utils/format'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import SmartAvatar from '@/components/SmartAvatar.vue'
import { partnerAvatarSeed } from '@/config/avatar'
import { checkVersion, currentVersion, useVersion } from '@/services/version'

defineOptions({ name: 'Profile' })

const router = useRouter()
const route = useRoute()
const { hasUpdate } = useVersion()
const userStore = useUserStore()
const { currentTheme } = useTheme()
const currentThemeInfo = computed(() => THEMES.find(t => t.key === currentTheme()) || THEMES[0])

const profileLoading = ref(true)
const profileError = ref('')
const profile = ref<UserProfile>()
const partner = ref<PartnerInfo>()
const wecomBinding = ref(false)
const wecomSaving = ref(false)
const unreadCount = ref<number>()

const accountEntries = computed(() => [
  { title: '个人信息', icon: 'contact', to: '/profile/edit' },
  { title: '提现记录', icon: 'underway-o', to: '/withdrawal', show: userStore.hasPermission('zsjos:withdrawal:my-query') },
  { title: '银行卡管理', icon: 'credit-pay', to: '/profile/bank-cards', show: userStore.hasPermission('zsjos:withdrawal:apply') },
  { title: '修改密码', icon: 'lock', to: '/profile/password' }
].filter(item => item.show !== false))

const otherEntries = computed(() => [
  { title: '消息通知', icon: 'bell', to: '/messages', badge: unreadCount.value || undefined },
  { title: '主题设置', icon: 'fire-o', to: '/profile/theme', value: currentThemeInfo.value.label },
  { title: '系统反馈', icon: 'comment-o', to: '/feedback' },
  { title: '投诉记录', icon: 'records-o', to: '/complaints', show: userStore.hasPermission('zsjos:lead-complaint:create') },
  { title: '版本控制与更新', icon: 'info-o', to: '/profile/version-update', value: hasUpdate.value ? '有更新' : `v${currentVersion.version}` }
].filter(item => item.show !== false))

const identityName = computed(() => profile.value?.name || partner.value?.name || userStore.nickname || '兼职伙伴')
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

async function loadUnreadCount() {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    // 保留上次成功结果，避免请求失败时显示虚假的未读数。
  }
}

async function handleWecomCallback() {
  if (route.query.wecomBind !== '1') return false
  const code = (route.query.code as string | undefined)?.trim()
  const state = (route.query.state as string | undefined)?.trim()
  if (!code || !state) {
    showToast('企业微信授权信息不完整，请重新绑定')
    await router.replace('/profile')
    return true
  }
  wecomBinding.value = true
  try {
    await bindWecom({ code, state })
    showSuccessToast('企业微信绑定成功')
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '企业微信绑定失败')
  } finally {
    wecomBinding.value = false
    await router.replace('/profile')
    await loadProfile()
  }
  return true
}

function buildWecomBindRedirectUri() {
  const url = new URL('/profile', window.location.origin)
  url.searchParams.set('wecomBind', '1')
  return url.toString()
}

async function handleWecomBind() {
  if (wecomBinding.value) return
  wecomBinding.value = true
  try {
    window.location.href = await wecomAuthorizeUrl(buildWecomBindRedirectUri())
  } catch (cause) {
    wecomBinding.value = false
    showToast(cause instanceof Error ? cause.message : '企业微信授权失败')
  }
}

async function handleWecomEnabled(enabled: boolean) {
  if (!profile.value?.wecomBound) {
    showToast('请先绑定企业微信')
    return
  }
  wecomSaving.value = true
  try {
    await updateNotifyChannel({ wecomEnabled: enabled })
    profile.value = { ...profile.value, wecomEnabled: enabled }
    showSuccessToast(enabled ? '已开启企业微信推送' : '已关闭企业微信推送')
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '设置失败')
  } finally {
    wecomSaving.value = false
  }
}

onMounted(async () => {
  const handledWecom = await handleWecomCallback()
  if (!handledWecom) void loadProfile()
})
onActivated(() => { void loadUnreadCount(); void checkVersion() })

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
  <div class="page-container profile-page">
    <section class="card profile-hero">
      <div class="profile-hero__top">
        <div class="profile-hero__identity">
          <SmartAvatar
            class="profile-avatar"
            :seed="partnerAvatarSeed(partner?.id)"
            :src="profile?.avatar || userStore.avatar"
            :size="42"
            loading="eager"
            label=""
          />
          <div class="profile-hero__text">
            <div class="profile-hero__name">{{ identityName }}</div>
            <div class="profile-hero__mobile">{{ identitySubtitle }}</div>
          </div>
        </div>
        <span class="profile-theme-chip">{{ currentThemeInfo.label }}</span>
      </div>
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
        <div class="page-section__title">企业微信</div>
      </div>
      <van-cell-group class="card profile-group" :border="false">
        <van-cell title="账号绑定" icon="wechat">
          <template #value>
            <span v-if="profile?.wecomBound" class="profile-meta">已绑定</span>
            <van-button v-else size="mini" type="primary" round :loading="wecomBinding" @click="handleWecomBind">去绑定</van-button>
          </template>
        </van-cell>
        <van-cell title="接收企业微信推送" icon="bell">
          <template #value>
            <van-switch
              :model-value="Boolean(profile?.wecomBound && profile?.wecomEnabled)"
              :disabled="!profile?.wecomBound"
              :loading="wecomSaving"
              size="22"
              @update:model-value="handleWecomEnabled"
            />
          </template>
        </van-cell>
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
      <van-button block round plain type="default" class="profile-logout-button" @click="handleLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding-bottom: 88px;
  background: transparent;
}

.profile-hero {
  margin-top: 20PX;
  padding: 14px 16px 16px;
  background:
    radial-gradient(circle at 100% 0, var(--h5-primary-opacity) 0, var(--h5-primary-opacity) 62px, transparent 63px),
    var(--h5-glass-surface);
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

.profile-group {
  overflow: hidden;
  border: 1px solid var(--h5-glass-border);
  border-radius: 16px;
  background: var(--h5-glass-surface);
  box-shadow: var(--h5-glass-shadow);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
}

.profile-group :deep(.van-cell) {
  min-height: 50px;
  background: transparent;
}

.profile-group :deep(.van-cell:not(:last-child)::after) {
  border-color: var(--h5-divider);
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

.profile-logout-button {
  --van-button-default-color: var(--h5-text-secondary);
  --van-button-default-border-color: var(--h5-glass-border);
  --van-button-plain-background: var(--h5-glass-surface);
  height: 46px;
  border-color: var(--h5-glass-border);
  background: var(--h5-glass-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6), 0 2px 8px rgba(31, 35, 48, 0.05);
  color: var(--h5-text-secondary);
  font-weight: 500;
  letter-spacing: 0.02em;
  transition: transform 0.16s ease, background 0.16s ease;
}

.profile-logout-button:active {
  transform: scale(0.99);
  background: var(--h5-glass-sunken);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .profile-group,
  .profile-logout-button { background: var(--h5-glass-surface-fallback); }
}

@media (prefers-reduced-transparency: reduce) {
  .profile-group {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .profile-logout-button {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
