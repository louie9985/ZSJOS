<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCashbackSummary, type CashbackSummary } from '@/api/cashback'
import { getMyLeadPage, type LeadListItem } from '@/api/lead'
import { getPartnerMe, type PartnerInfo } from '@/api/profile'
import { getLeaderboard, getLeaderboardConfig, type LeaderboardConfig, type LeaderboardData } from '@/api/leaderboard'
import { getUnreadCount } from '@/api/message'
import { wasMockedEndpoint } from '@/api/mock'
import { formatDate, formatLeadNo, formatLeadStatus } from '@/utils/format'

defineOptions({ name: 'Home' })

const router = useRouter()
const userStore = useUserStore()

const partner = ref<PartnerInfo>()
const summary = ref<CashbackSummary>()
const recentLeads = ref<LeadListItem[]>([])
const loading = ref(true)
const loadError = ref('')
const unreadCount = ref(0)
const leaderboardConfig = ref<LeaderboardConfig>()
const leaderboard = ref<LeaderboardData>()
const leaderboardConfigLoading = ref(true)
const leaderboardLoading = ref(false)
const leaderboardConfigError = ref('')
const leaderboardError = ref('')
const leaderboardConfigStatus = ref<number>()

const leaderboardVisible = computed(() => leaderboardConfigLoading.value || !!leaderboardConfigError.value || leaderboardConfig.value?.enabled === true)
const leaderboardPreview = computed(() => (leaderboard.value?.top3?.length ? leaderboard.value.top3 : leaderboard.value?.list || []).slice(0, 3))
const leaderboardUsingMock = computed(() => wasMockedEndpoint('/zsjos/partner/leaderboard'))
const leaderboardConfigUnavailable = computed(() => {
  if ([401, 403, 500].includes(leaderboardConfigStatus.value || 0)) return false
  return [404, 405, 501].includes(leaderboardConfigStatus.value || 0)
    || /接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(leaderboardConfigError.value)
})

function errorStatus(cause: unknown): number | undefined {
  if (!cause || typeof cause !== 'object') return undefined
  const value = cause as { status?: unknown; response?: { status?: unknown } }
  const status = value.status ?? value.response?.status
  return typeof status === 'number' ? status : undefined
}

function leaderboardFailureMessage(cause: unknown, fallback: string): string {
  const message = cause instanceof Error ? cause.message : ''
  const status = errorStatus(cause) ?? (/登录已失效/.test(message) ? 401 : undefined)
  leaderboardConfigStatus.value = status
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '暂无权限查看排行榜'
  if (status === 500) return '排行榜加载失败，请重试'
  if (/接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(message)) return message
  return fallback
}

async function loadHome() {
  loading.value = true
  loadError.value = ''
  try {
    const [partnerData, summaryData, leadsData] = await Promise.all([
      getPartnerMe(),
      getCashbackSummary(),
      getMyLeadPage({ pageNo: 1, pageSize: 3 })
    ])
    partner.value = partnerData
    summary.value = summaryData
    recentLeads.value = leadsData.list
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '工作台加载失败'
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  try { unreadCount.value = await getUnreadCount() } catch { unreadCount.value = 0 }
}

async function loadLeaderboard() {
  leaderboardConfigLoading.value = true
  leaderboardConfigError.value = ''
  leaderboardConfigStatus.value = undefined
  leaderboardError.value = ''
  leaderboard.value = undefined
  try {
    leaderboardConfig.value = await getLeaderboardConfig()
  } catch (cause) {
    leaderboardConfig.value = undefined
    leaderboardConfigError.value = leaderboardFailureMessage(cause, '排行榜加载失败，请重试')
    return
  } finally {
    leaderboardConfigLoading.value = false
  }
  if (!leaderboardConfig.value.enabled) return
  leaderboardLoading.value = true
  try {
    leaderboard.value = await getLeaderboard({
      period: leaderboardConfig.value.defaultPeriod,
      type: leaderboardConfig.value.defaultType,
      pageNo: 1,
      pageSize: 3
    })
  } catch (cause) {
    leaderboardError.value = cause instanceof Error ? cause.message : '榜单摘要加载失败'
  } finally {
    leaderboardLoading.value = false
  }
}

onMounted(() => { void loadHome(); void loadUnreadCount(); void loadLeaderboard() })

function goSubmit() {
  router.push('/lead/submit')
}

function goEarnings() {
  router.push('/earnings')
}

function goWithdraw() {
  router.push('/withdrawal/apply')
}

function goLeadList() {
  router.push('/lead/list')
}

function goMessages() { router.push('/messages') }

function goLeaderboard() { router.push('/leaderboard') }

function formatLeaderboardValue(value: number) {
  return leaderboard.value?.valueUnit === 'count' ? `${Math.round(value)} 条` : `¥${value.toFixed(2)}`
}

function goLeadDetail(id: number) {
  router.push(`/lead/${id}`)
}

const statusColor: Record<string, string> = {
  submitted: 'var(--h5-info)',
  valid: 'var(--h5-success)',
  invalid: 'var(--h5-danger)',
  suspended: 'var(--h5-warning)',
  won: 'var(--h5-success)',
  converted: 'var(--h5-primary)',
  closed: 'var(--h5-text-secondary)'
}
</script>

<template>
  <div class="page-container">
    <van-empty v-if="!loading && loadError" :description="loadError" image="error">
      <van-button size="small" type="primary" @click="loadHome">重新加载</van-button>
    </van-empty>
    <template v-else>
    <!-- 渐变头部 -->
    <div class="page-header-gradient home-header">
      <div class="home-header__top">
        <div class="home-header__greeting">Hi, {{ partner?.name || userStore.nickname || '兼职伙伴' }} 👋</div>
        <van-badge :content="unreadCount || undefined" :max="99">
          <button type="button" class="home-header__bell" aria-label="消息中心" @click="goMessages"><van-icon name="bell" size="22" /></button>
        </van-badge>
      </div>
      <div class="home-header__sub">
        编号: {{ partner?.partnerNo || '--' }}
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="card home-shortcuts">
      <div class="home-shortcuts__item" @click="goSubmit">
        <van-icon name="edit" size="28" color="var(--h5-primary)" />
        <span>提交客资</span>
      </div>
      <div class="home-shortcuts__item" @click="goEarnings">
        <van-icon name="gold-coin-o" size="28" color="var(--h5-warning)" />
        <span>查看收益</span>
      </div>
      <div v-if="leaderboardVisible" class="home-shortcuts__item" @click="goLeaderboard">
        <van-icon name="medal-o" size="28" color="var(--h5-success)" />
        <span>排行榜</span>
      </div>
    </div>

    <!-- 收益概览 -->
    <div class="card">
      <div class="section-title">收益概览</div>
      <van-skeleton :loading="loading" :row="2">
        <div class="home-earnings">
          <div class="home-earnings__item">
            <div class="home-earnings__value">¥{{ summary?.pendingAmount?.toFixed(2) || '0.00' }}</div>
            <div class="home-earnings__label">待结算</div>
          </div>
          <div class="home-earnings__item">
            <div class="home-earnings__value amount--primary">¥{{ summary?.availableAmount?.toFixed(2) || '0.00' }}</div>
            <div class="home-earnings__label">可提现</div>
          </div>
          <div class="home-earnings__item">
            <div class="home-earnings__value">¥{{ summary?.withdrawnAmount?.toFixed(2) || '0.00' }}</div>
            <div class="home-earnings__label">已提现</div>
          </div>
        </div>
        <van-button
          v-if="summary && summary.availableAmount > 0"
          type="primary"
          size="small"
          round
          class="home-withdraw-btn"
          @click="goWithdraw"
        >
          去提现 →
        </van-button>
      </van-skeleton>
    </div>

    <!-- 排行榜摘要 -->
    <div v-if="leaderboardVisible" class="card home-leaderboard">
      <div class="section-title section-title--link">
        <span>排行榜</span>
        <button type="button" @click="goLeaderboard">查看全部 &gt;</button>
      </div>
      <van-notice-bar v-if="leaderboardUsingMock" class="leaderboard-notice" color="#8a6100" background="#fff7df" left-icon="info-o">
        开发环境演示数据
      </van-notice-bar>
      <van-skeleton :loading="leaderboardConfigLoading || leaderboardLoading" :row="3">
        <van-empty v-if="leaderboardConfigError" :description="leaderboardConfigError" image="error" :image-size="56">
          <p v-if="leaderboardConfigUnavailable" class="leaderboard-unavailable">后端尚未提供排行榜接口，当前功能不可用。</p>
          <van-button size="mini" type="primary" @click="loadLeaderboard">重试</van-button>
        </van-empty>
        <van-empty v-else-if="leaderboardError" :description="leaderboardError" image="error" :image-size="56">
          <van-button size="mini" type="primary" @click="loadLeaderboard">重试</van-button>
        </van-empty>
        <template v-else-if="leaderboard">
          <div class="leaderboard-summary" @click="goLeaderboard">
            <div>
              <small>{{ leaderboard.periodLabel }}{{ leaderboard.typeLabel }} · {{ leaderboard.total }} 人</small>
              <strong v-if="leaderboard.myRank">我的排名：第 {{ leaderboard.myRank.rank }} 名</strong>
              <strong v-else>暂未上榜</strong>
            </div>
            <van-icon name="arrow" color="var(--h5-text-placeholder)" />
          </div>
          <div v-for="item in leaderboardPreview" :key="item.partnerId" class="leaderboard-preview" :class="{ 'is-me': item.isMe }" @click="goLeaderboard">
            <span class="leaderboard-preview__rank">{{ item.rank }}</span>
            <span class="leaderboard-preview__name">{{ item.displayName }}<small v-if="item.isMe">（我）</small></span>
            <strong>{{ formatLeaderboardValue(item.value) }}</strong>
          </div>
        </template>
      </van-skeleton>
    </div>

    <!-- 最近提交 -->
    <div class="card">
      <div class="section-title" style="display: flex; justify-content: space-between; align-items: center;">
        最近提交
        <span style="font-size: 12px; color: var(--h5-text-secondary);" @click="goLeadList">查看全部 ></span>
      </div>
      <van-skeleton :loading="loading" :row="3">
        <div v-if="recentLeads.length === 0" style="text-align: center; color: var(--h5-text-placeholder); padding: 20px 0;">
          暂无提交记录
        </div>
        <van-cell-group v-else :border="false">
          <van-cell
            v-for="lead in recentLeads"
            :key="lead.id"
            :title="lead.submittedName"
            :label="`${formatLeadNo(lead.leadNo)} · ${formatDate(lead.submittedAt)}`"
            is-link
            @click="goLeadDetail(lead.id)"
          >
            <template #value>
              <span :style="{ color: statusColor[lead.status] || 'var(--h5-text-secondary)', fontSize: '12px' }">
                {{ formatLeadStatus(lead.status) }}
              </span>
            </template>
          </van-cell>
        </van-cell-group>
      </van-skeleton>
    </div>
    </template>
  </div>
</template>

<style scoped>
.home-header {
  padding: 30px 20px 40px;
}
.home-header__greeting {
  font-size: 20px;
  font-weight: 600;
}
.home-header__sub {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.85;
}

.home-shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(88px, 1fr));
  gap: 16px;
  margin-top: -20px;
}
.home-shortcuts__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  border-radius: 10px;
  background: var(--h5-primary-opacity);
  font-size: 13px;
  color: var(--h5-text-primary);
}

.home-earnings {
  display: flex;
  justify-content: space-around;
  text-align: center;
  padding: 8px 0;
}
.home-earnings__value {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.home-earnings__label {
  font-size: 12px;
  color: var(--h5-text-secondary);
  margin-top: 4px;
}

.home-withdraw-btn {
  display: block;
  margin: 12px auto 0;
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
  margin-bottom: 12px;
}
.section-title--link{display:flex;align-items:center;justify-content:space-between}.section-title--link button{padding:4px;border:0;background:transparent;color:var(--h5-text-secondary);font-size:12px}.leaderboard-notice{margin:0 -16px 10px}.leaderboard-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:4px 0 10px}.leaderboard-summary>div{display:flex;min-width:0;flex-direction:column;gap:4px}.leaderboard-summary small{color:var(--h5-text-secondary);font-size:11px}.leaderboard-summary strong{font-size:16px;letter-spacing:0}.leaderboard-preview{display:flex;min-height:42px;align-items:center;gap:10px;border-top:1px solid var(--h5-divider);font-size:13px}.leaderboard-preview.is-me{margin:0 -8px;padding:0 8px;background:var(--h5-primary-opacity)}.leaderboard-preview__rank{width:24px;text-align:center;color:var(--h5-primary);font-weight:700}.leaderboard-preview__name{min-width:0;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.leaderboard-preview__name small{color:var(--h5-primary)}.leaderboard-preview>strong{font-variant-numeric:tabular-nums}.leaderboard-unavailable{margin:-8px 0 12px;color:var(--h5-text-secondary);font-size:11px}
.home-header__top{display:flex;align-items:center;justify-content:space-between;gap:12px}.home-header__bell{display:flex;width:36px;height:36px;align-items:center;justify-content:center;padding:0;border:0;border-radius:50%;background:rgba(255,255,255,.18);color:#fff}
</style>
