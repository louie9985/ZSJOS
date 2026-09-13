<script setup lang="ts">
import { computed, ref, onActivated, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { getCashbackSummary, type CashbackSummary } from '@/api/cashback'
import {
  getHomeStatistics,
  getHomeStatisticsDetails,
  type HomeStatistics,
  type HomeStatisticsDetailItem,
  type HomeStatisticsLeadDetail,
  type HomeStatisticsMetric,
  type HomeStatisticsPeriod,
  type HomeStatisticsWithdrawalDetail
} from '@/api/home'
import { getLeadFollowUpSummary, getMyLeadPage, type LeadFollowUpSummary, type LeadListItem } from '@/api/lead'
import { getPartnerMe, type PartnerInfo } from '@/api/profile'
import { getLeaderboard, getLeaderboardConfig, type LeaderboardConfig, type LeaderboardData, type LeaderboardMember } from '@/api/leaderboard'
import { getUnreadCount } from '@/api/message'
import { formatAmount, formatDateTime, formatLeadNo, formatLeadStatus } from '@/utils/format'
import { formatLeaderboardPeriodLabel, formatLeaderboardTitle, formatLeaderboardValue } from '@/utils/leaderboard'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'
import SmartAvatar from '@/components/SmartAvatar.vue'
import LeaderboardCrown from '@/components/LeaderboardCrown.vue'
import { leadAvatarSeed, partnerAvatarSeed } from '@/config/avatar'

defineOptions({ name: 'Home' })

const router = useRouter()
const userStore = useUserStore()
let homeMounted = false

const partner = ref<PartnerInfo>()
const summary = ref<CashbackSummary>()
const recentLeads = ref<LeadListItem[]>([])
const unreadCount = ref<number>()
const partnerError = ref('')
const summaryLoading = ref(true)
const summaryError = ref('')
const recentLeadsLoading = ref(true)
const recentLeadsError = ref('')
const leadFollowUpSummary = ref<LeadFollowUpSummary>()
const leadFollowUpSummaryLoading = ref(true)
const leadFollowUpSummaryError = ref('')
const leaderboardConfig = ref<LeaderboardConfig>()
const leaderboard = ref<LeaderboardData>()
const leaderboardConfigLoading = ref(true)
const leaderboardLoading = ref(false)
const leaderboardConfigError = ref('')
const leaderboardError = ref('')
const leaderboardConfigStatus = ref<number>()
const statisticsPeriod = ref<HomeStatisticsPeriod>('total')
const statistics = ref<HomeStatistics>()
const statisticsLoading = ref(true)
const statisticsError = ref('')
const statisticsDetailVisible = ref(false)
const statisticsDetailMetric = ref<HomeStatisticsMetric>()
const statisticsDetailItems = ref<HomeStatisticsDetailItem[]>([])
const statisticsDetailSelected = ref<HomeStatisticsDetailItem>()
const statisticsDetailTotal = ref(0)
const statisticsDetailTotalAmount = ref(0)
const statisticsDetailPageNo = ref(1)
const statisticsDetailInitialLoading = ref(false)
const statisticsDetailLoading = ref(false)
const statisticsDetailRefreshing = ref(false)
const statisticsDetailFinished = ref(false)
const statisticsDetailError = ref('')
let statisticsRequestVersion = 0
let statisticsDetailRequestVersion = 0
let statisticsDetailRequestInFlight = false

const statisticsDetailPageSize = 10

const statisticsPeriods: Array<{ key: HomeStatisticsPeriod; label: string }> = [
  { key: 'today', label: '今日' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '全年' },
  { key: 'total', label: '累计' }
]

const statisticsMetrics: Record<HomeStatisticsMetric, { label: string; permission: string; unit: 'count' | 'money' }> = {
  lead_count: { label: '客资数', permission: 'zsjos:lead:query-submitted', unit: 'count' },
  withdrawn_amount: { label: '已提现金额', permission: 'zsjos:withdrawal:my-query', unit: 'money' },
  valid_lead_count: { label: '有效客资', permission: 'zsjos:lead:query-submitted', unit: 'count' },
  converted_lead_count: { label: '成交客资', permission: 'zsjos:lead:query-submitted', unit: 'count' }
}

const leaderboardVisible = computed(() => leaderboardConfigLoading.value || leaderboardConfig.value?.enabled !== false || !!leaderboardConfigError.value)
const canViewLeads = computed(() => userStore.hasPermission('zsjos:lead:query-submitted'))
const canViewEarnings = computed(() => userStore.hasPermission('zsjos:cashback:my-query'))
const earningsSnapshot = computed(() => {
  const value = summary.value
  if (!value) return null
  return {
    estimatedIncome: (value.pendingAmount || 0) + (value.availableAmount || 0) + (value.withdrawingAmount || 0)
  }
})
const statisticsPeriodLabel = computed(() => statisticsPeriods.find(item => item.key === statisticsPeriod.value)?.label || '累计')
const statisticsDetailTitle = computed(() => statisticsDetailMetric.value ? statisticsMetrics[statisticsDetailMetric.value].label : '统计明细')
const statisticsDetailSubtitle = computed(() => {
  if (!statisticsDetailMetric.value) return statisticsPeriodLabel.value
  const metric = statisticsMetrics[statisticsDetailMetric.value]
  if (metric.unit === 'money') {
    return `${statisticsPeriodLabel.value} · ${statisticsDetailTotal.value} 笔 · ${formatMoney(statisticsDetailTotalAmount.value)}`
  }
  return `${statisticsPeriodLabel.value} · ${statisticsDetailTotal.value} 条`
})
const leaderboardCardTitle = computed(() => {
  const configuredType = leaderboardConfig.value?.typeOptions.find(option => option.key === leaderboardConfig.value?.defaultType)
  return formatLeaderboardTitle(leaderboard.value?.typeLabel || configuredType?.label)
})
const leaderboardPeriodLabel = computed(() => formatLeaderboardPeriodLabel(
  leaderboard.value?.period || leaderboardConfig.value?.defaultPeriod
))
const leaderboardTop3 = computed<LeaderboardMember[]>(() => (leaderboard.value?.top3 || []).slice(0, 3))
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

async function loadPartner() {
  partnerError.value = ''
  try {
    partner.value = await getPartnerMe()
  } catch (cause) {
    partnerError.value = cause instanceof Error ? cause.message : '伙伴资料加载失败'
  }
}

async function loadSummary() {
  if (!canViewEarnings.value) return
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await getCashbackSummary()
  } catch (cause) {
    summary.value = undefined
    summaryError.value = cause instanceof Error ? cause.message : '收益概览加载失败'
  } finally {
    summaryLoading.value = false
  }
}

async function loadRecentLeads() {
  if (!canViewLeads.value) return
  recentLeadsLoading.value = true
  recentLeadsError.value = ''
  try {
    recentLeads.value = (await getMyLeadPage({ pageNo: 1, pageSize: 3 })).list
  } catch (cause) {
    recentLeads.value = []
    recentLeadsError.value = cause instanceof Error ? cause.message : '最近提交加载失败'
  } finally {
    recentLeadsLoading.value = false
  }
}

async function loadLeadFollowUpSummary() {
  if (!canViewLeads.value) return
  leadFollowUpSummaryLoading.value = true
  leadFollowUpSummaryError.value = ''
  try {
    leadFollowUpSummary.value = await getLeadFollowUpSummary()
  } catch (cause) {
    leadFollowUpSummary.value = undefined
    leadFollowUpSummaryError.value = cause instanceof Error ? cause.message : '客资提醒加载失败'
  } finally {
    leadFollowUpSummaryLoading.value = false
  }
}

async function loadUnreadCount() {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    closeToast()
  }
}

function statisticsFailureMessage(cause: unknown) {
  const message = cause instanceof Error ? cause.message : ''
  const status = errorStatus(cause) ?? (/登录已失效/.test(message) ? 401 : undefined)
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '暂无权限查看数据统计'
  if (status === 500) return '数据统计加载失败，请重试'
  if (/后端接口暂未提供|接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(message)) {
    return '首页统计接口暂未提供'
  }
  return message || '数据统计加载失败，请重试'
}

async function loadStatistics(period = statisticsPeriod.value) {
  const requestVersion = ++statisticsRequestVersion
  statisticsLoading.value = true
  statisticsError.value = ''
  try {
    const data = await getHomeStatistics(period)
    if (requestVersion !== statisticsRequestVersion || period !== statisticsPeriod.value) return
    statistics.value = data
  } catch (cause) {
    if (requestVersion !== statisticsRequestVersion || period !== statisticsPeriod.value) return
    statistics.value = undefined
    statisticsError.value = statisticsFailureMessage(cause)
  } finally {
    if (requestVersion === statisticsRequestVersion) statisticsLoading.value = false
  }
}

function selectStatisticsPeriod(period: HomeStatisticsPeriod) {
  if (statisticsPeriod.value === period && statistics.value && !statisticsError.value) return
  statisticsPeriod.value = period
  void loadStatistics(period)
}

function canOpenStatisticsMetric(metric: HomeStatisticsMetric) {
  return userStore.hasPermission(statisticsMetrics[metric].permission)
}

function statisticsDetailFailureMessage(cause: unknown) {
  const message = cause instanceof Error ? cause.message : ''
  const status = errorStatus(cause) ?? (/登录已失效/.test(message) ? 401 : undefined)
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '暂无权限查看该项统计明细'
  if (/后端接口暂未提供|接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(message)) {
    return import.meta.env.DEV ? '演示明细加载失败，请重试' : '统计明细功能暂未开放'
  }
  return message || '统计明细加载失败，请重试'
}

async function loadStatisticsDetails(reset = false) {
  const metric = statisticsDetailMetric.value
  if (!metric || !canOpenStatisticsMetric(metric)) return
  if (reset) {
    statisticsDetailRequestVersion += 1
    statisticsDetailPageNo.value = 1
    statisticsDetailItems.value = []
    statisticsDetailTotal.value = 0
    statisticsDetailTotalAmount.value = 0
    statisticsDetailFinished.value = false
    statisticsDetailError.value = ''
  } else if (statisticsDetailRequestInFlight || statisticsDetailFinished.value) {
    return
  }
  const requestVersion = statisticsDetailRequestVersion
  const pageNo = statisticsDetailPageNo.value
  if (reset) statisticsDetailInitialLoading.value = true
  else statisticsDetailLoading.value = true
  statisticsDetailRequestInFlight = true
  try {
    const data = await getHomeStatisticsDetails({
      period: statisticsPeriod.value,
      metric,
      pageNo,
      pageSize: statisticsDetailPageSize
    })
    if (requestVersion !== statisticsDetailRequestVersion || metric !== statisticsDetailMetric.value) return
    statisticsDetailItems.value = reset ? data.list : [...statisticsDetailItems.value, ...data.list]
    statisticsDetailTotal.value = data.total
    statisticsDetailTotalAmount.value = data.totalAmount || 0
    statisticsDetailPageNo.value = pageNo + 1
    statisticsDetailFinished.value = statisticsDetailItems.value.length >= data.total || data.list.length < statisticsDetailPageSize
    statisticsDetailError.value = ''
  } catch (cause) {
    if (requestVersion !== statisticsDetailRequestVersion || metric !== statisticsDetailMetric.value) return
    statisticsDetailError.value = statisticsDetailFailureMessage(cause)
    if (!statisticsDetailItems.value.length) statisticsDetailFinished.value = false
  } finally {
    if (requestVersion === statisticsDetailRequestVersion) {
      statisticsDetailRequestInFlight = false
      statisticsDetailInitialLoading.value = false
      statisticsDetailLoading.value = false
      statisticsDetailRefreshing.value = false
    }
  }
}

function openStatisticsDetails(metric: HomeStatisticsMetric) {
  if (!canOpenStatisticsMetric(metric)) {
    showToast('暂无权限查看该项明细')
    return
  }
  statisticsDetailMetric.value = metric
  statisticsDetailSelected.value = undefined
  statisticsDetailVisible.value = true
  void loadStatisticsDetails(true)
}

function refreshStatisticsDetails() {
  statisticsDetailRefreshing.value = true
  void loadStatisticsDetails(true)
}

function closeStatisticsDetails() {
  if (statisticsDetailSelected.value) {
    statisticsDetailSelected.value = undefined
    return
  }
  statisticsDetailVisible.value = false
}

function resetStatisticsDetails() {
  statisticsDetailRequestVersion += 1
  statisticsDetailSelected.value = undefined
  statisticsDetailMetric.value = undefined
  statisticsDetailItems.value = []
  statisticsDetailTotal.value = 0
  statisticsDetailTotalAmount.value = 0
  statisticsDetailError.value = ''
}

function isLeadStatisticsDetail(item: HomeStatisticsDetailItem): item is HomeStatisticsLeadDetail {
  return item.kind === 'lead'
}

function isWithdrawalStatisticsDetail(item: HomeStatisticsDetailItem): item is HomeStatisticsWithdrawalDetail {
  return item.kind === 'withdrawal'
}

function openStatisticsItem(item: HomeStatisticsDetailItem) {
  statisticsDetailVisible.value = false
  void router.push(item.kind === 'lead' ? `/lead/${item.id}` : `/withdrawal/${item.id}`)
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

onMounted(() => {
  void loadPartner()
  void loadSummary()
  void loadRecentLeads()
  void loadLeadFollowUpSummary()
  void loadLeaderboard()
  void loadStatistics()
  homeMounted = true
})
onActivated(() => {
  void loadUnreadCount()
  if (homeMounted) void loadLeadFollowUpSummary()
})

function goEarnings() {
  router.push('/earnings')
}

function goLeadList() {
  router.push('/lead/list')
}

type LeadFollowUpView = 'follow_up_pending' | 'unreachable' | 'invalid'

function goLeadFollowUp(view: LeadFollowUpView) {
  router.push({ path: '/lead/follow-up', query: { view } })
}

function goMessages() { router.push('/messages') }

function goLeaderboard() {
  const current = leaderboard.value
  if (!current) {
    void router.push('/leaderboard')
    return
  }
  void router.push({ path: '/leaderboard', query: { type: current.type, period: current.period } })
}

function formatMoney(value: number) {
  return `¥${value.toFixed(2)}`
}

function goLeadDetail(id: number) {
  router.push(`/lead/${id}`)
}

function statusClass(status: string) {
  return ['valid', 'won'].includes(status) ? 'success'
    : status === 'invalid' ? 'danger'
      : status === 'closed' ? 'muted'
        : status === 'suspended' ? 'warning' : 'primary'
}
</script>

<template>
  <div class="page-container home-page norem">
    <header class="home-header">
      <div class="home-header__copy">
        <h1>Hi，{{ partner?.name || userStore.nickname || '兼职伙伴' }}</h1>
        <p>今天也要加油哦 <span aria-hidden="true">💪</span></p>
      </div>
      <div class="home-header__actions">
        <van-badge :content="unreadCount || undefined" :max="99">
          <button type="button" class="home-header__bell" aria-label="消息中心" @click="goMessages">
            <van-icon name="bell" size="22" />
          </button>
        </van-badge>
        <SmartAvatar
          class="home-header__avatar"
          :seed="partnerAvatarSeed(partner?.id)"
          :src="userStore.avatar"
          :size="54"
          loading="eager"
          label=""
        />
      </div>
    </header>

    <van-notice-bar v-if="partnerError" class="home-profile-error" color="var(--h5-danger)" background="#fff1f0" left-icon="warning-o">
      <span>{{ partnerError }}</span>
      <button type="button" @click="loadPartner">重试</button>
    </van-notice-bar>

    <section v-if="canViewEarnings" class="home-card home-earnings-card" aria-label="收益概览">
      <van-skeleton :loading="summaryLoading" :row="2">
        <div v-if="summaryError" class="home-inline-state">
          <span>{{ summaryError }}</span>
          <button type="button" @click="loadSummary">重试</button>
        </div>
        <template v-else>
          <div class="home-earnings-main">
            <div class="home-earnings-primary">
              <span class="home-earnings-label">预计收入</span>
              <strong class="home-earnings-amount">{{ formatMoney(earningsSnapshot?.estimatedIncome || 0) }}</strong>
            </div>
            <button type="button" class="home-withdraw-button" @click="goEarnings">查看收益</button>
          </div>
          <div v-if="summary" class="home-earnings-row">
            <div class="home-earnings-metric">
              <span class="home-earnings-metric__label">兑现收入</span>
              <strong class="home-earnings-metric__value">{{ formatMoney(summary.pendingAmount || 0) }}</strong>
            </div>
            <div class="home-earnings-metric home-earnings-metric--available">
              <span class="home-earnings-metric__label">可提现收入</span>
              <strong class="home-earnings-metric__value">{{ formatMoney(summary.availableAmount || 0) }}</strong>
            </div>
          </div>
        </template>
      </van-skeleton>
    </section>

    <section v-else class="home-card home-earnings-card home-earnings-card--locked" aria-label="收益概览">
      <span class="home-earnings-label">预计收入</span>
      <strong class="home-earnings-amount">--</strong>
      <p class="home-earnings-sub"><span>暂无权限查看收益</span></p>
    </section>

    <section v-if="canViewLeads" class="home-card home-follow-card" aria-label="客资跟进提醒">
      <div class="home-follow-card__header">
        <h2>客资跟进提醒</h2>
        <button v-if="leadFollowUpSummaryError" type="button" class="home-follow-card__retry" @click="loadLeadFollowUpSummary">重试</button>
      </div>
      <p v-if="leadFollowUpSummaryError" class="home-follow-card__error">客资提醒加载失败</p>
      <div v-else class="home-follow-card__metrics">
        <button type="button" class="home-follow-card__metric home-follow-card__metric--pending" :disabled="leadFollowUpSummaryLoading" @click="goLeadFollowUp('follow_up_pending')">
          <strong>{{ leadFollowUpSummaryLoading ? '--' : leadFollowUpSummary?.followUpPendingCount ?? 0 }}</strong>
          <span>待跟进</span>
        </button>
        <button type="button" class="home-follow-card__metric home-follow-card__metric--unreachable" :disabled="leadFollowUpSummaryLoading" @click="goLeadFollowUp('unreachable')">
          <strong>{{ leadFollowUpSummaryLoading ? '--' : leadFollowUpSummary?.unreachableCount ?? 0 }}</strong>
          <span>未联系上</span>
        </button>
        <button type="button" class="home-follow-card__metric home-follow-card__metric--invalid" :disabled="leadFollowUpSummaryLoading" @click="goLeadFollowUp('invalid')">
          <strong>{{ leadFollowUpSummaryLoading ? '--' : leadFollowUpSummary?.invalidCount ?? 0 }}</strong>
          <span>已判无效</span>
        </button>
      </div>
    </section>

    <section class="home-card home-statistics" aria-label="客资数">
      <div class="home-section-title statistics-header">
        <div class="statistics-title-wrap">
          <h2>客资数</h2>
        </div>
        <LiquidSegmentedControl
          class="period-tabs"
          :model-value="statisticsPeriod"
          :items="statisticsPeriods"
          ariaLabel="客资周期"
          compact
          @change="selectStatisticsPeriod($event as HomeStatisticsPeriod)"
        />
      </div>

      <div class="statistics-body" :aria-busy="statisticsLoading">
        <div v-if="statisticsLoading" class="home-block-state">
          <van-loading size="22" color="var(--h5-primary)">加载中</van-loading>
        </div>
        <div v-else-if="statisticsError" class="home-block-state">
          <span>{{ statisticsError }}</span>
          <button type="button" @click="loadStatistics()">重试</button>
        </div>
        <template v-else-if="statistics">
          <div class="statistics-metrics">
            <button
              type="button"
              class="statistics-metric"
              :class="{ 'is-locked': !canOpenStatisticsMetric('lead_count') }"
              @click="openStatisticsDetails('lead_count')"
            >
              <span>提交客资</span>
              <strong class="statistics-metric__value">{{ Math.round(statistics.leadCount) }}</strong>
              <i><van-icon :name="canOpenStatisticsMetric('lead_count') ? 'arrow' : 'lock'" size="15" /></i>
            </button>
            <button
              type="button"
              class="statistics-metric"
              :class="{ 'is-locked': !canOpenStatisticsMetric('valid_lead_count') }"
              @click="openStatisticsDetails('valid_lead_count')"
            >
              <span>有效客资</span>
              <strong class="statistics-metric__value">{{ Math.round(statistics.validLeadCount) }}</strong>
              <i><van-icon :name="canOpenStatisticsMetric('valid_lead_count') ? 'arrow' : 'lock'" size="15" /></i>
            </button>
            <button
              type="button"
              class="statistics-metric"
              :class="{ 'is-locked': !canOpenStatisticsMetric('converted_lead_count') }"
              @click="openStatisticsDetails('converted_lead_count')"
            >
              <span>成交客资</span>
              <strong class="statistics-metric__value">{{ Math.round(statistics.convertedLeadCount) }}</strong>
              <i><van-icon :name="canOpenStatisticsMetric('converted_lead_count') ? 'arrow' : 'lock'" size="15" /></i>
            </button>
          </div>
        </template>
      </div>
    </section>

    <section v-if="canViewLeads" class="home-card home-recent" aria-label="最近提交">
      <div class="home-section-title">
        <h2>最近提交</h2>
        <button type="button" @click="goLeadList">查看全部 <van-icon name="arrow" size="15" /></button>
      </div>
      <van-skeleton :loading="recentLeadsLoading" :row="3">
        <div v-if="recentLeadsError" class="home-block-state home-block-state--compact">
          <span>{{ recentLeadsError }}</span>
          <button type="button" @click="loadRecentLeads">重试</button>
        </div>
        <div v-else-if="recentLeads.length === 0" class="home-empty-row">暂无提交记录</div>
        <div v-else class="recent-list">
          <button
            v-for="lead in recentLeads.slice(0, 3)"
            :key="lead.id"
            type="button"
            class="recent-lead"
            @click="goLeadDetail(lead.id)"
          >
            <SmartAvatar class="recent-lead__avatar" :seed="leadAvatarSeed(lead.id)" :size="34" shape="rounded" label="" />
            <span class="recent-lead__name">{{ lead.submittedName || '未命名客户' }}</span>
            <span class="recent-lead__status" :class="`recent-lead__status--${statusClass(lead.status)}`">
              {{ formatLeadStatus(lead.status) }}
            </span>
            <van-icon class="recent-lead__arrow" name="arrow" size="16" />
          </button>
        </div>
      </van-skeleton>
    </section>

    <section v-if="leaderboardVisible" class="home-card home-leaderboard hero-surface" aria-label="排行榜">
      <header class="leaderboard-card-header">
        <span class="leaderboard-card-header__icon"><van-icon name="medal-o" size="20" /></span>
        <span class="leaderboard-card-header__copy">
          <strong>{{ leaderboardCardTitle }}</strong>
          <small v-if="leaderboardPeriodLabel" class="leaderboard-card-header__period">{{ leaderboardPeriodLabel }}</small>
        </span>
        <button type="button" @click="goLeaderboard">
          查看全部 <van-icon name="arrow" size="14" />
        </button>
      </header>

      <div v-if="leaderboardTop3.length" class="leaderboard-compact-list">
        <button
          v-for="item in leaderboardTop3"
          :key="item.partnerId"
          type="button"
          class="leaderboard-compact-row"
          :class="[`rank-${item.rank}`, { 'is-me': item.isMe }]"
          @click="goLeaderboard"
        >
          <LeaderboardCrown v-if="item.rank === 1" :rank="item.rank" tone="theme" class="leaderboard-compact-row__crown" />
          <span v-else class="leaderboard-compact-row__rank">{{ item.rank }}</span>
          <SmartAvatar class="leaderboard-compact-row__avatar" :seed="partnerAvatarSeed(item.partnerId)" :size="34" label="" />
          <span class="leaderboard-compact-row__name">
            <strong>{{ item.displayName }}</strong>
            <small v-if="item.isMe">我</small>
          </span>
          <span class="leaderboard-compact-row__value">
            <van-icon name="fire-o" size="15" />
            {{ formatLeaderboardValue(item.value, leaderboard?.valueUnit) }}
          </span>
        </button>
      </div>
      <div v-else class="leaderboard-compact-state">
        <van-loading v-if="leaderboardConfigLoading || leaderboardLoading" size="20" color="var(--h5-primary)">榜单加载中</van-loading>
        <template v-else-if="leaderboardConfigError">
          <span>{{ leaderboardConfigUnavailable ? '排行榜功能暂不可用' : leaderboardConfigError }}</span>
          <button type="button" @click="loadLeaderboard">重试</button>
        </template>
        <template v-else-if="leaderboardError">
          <span>{{ leaderboardError }}</span>
          <button type="button" @click="loadLeaderboard">重试</button>
        </template>
        <span v-else>排行榜暂未开启</span>
      </div>

      <button type="button" class="leaderboard-card-self" @click="goLeaderboard">
        <SmartAvatar
          class="leaderboard-card-self__avatar"
          :seed="partnerAvatarSeed(leaderboard?.myRank?.partnerId ?? partner?.id)"
          :src="userStore.avatar"
          :size="32"
          label=""
        />
        <span class="leaderboard-card-self__copy">
          <span>我的排名 <b>{{ leaderboard?.myRank?.rank ?? '未上榜' }}</b></span>
          <i />
          <span v-if="leaderboard?.myRank?.rank === 1">继续保持领先</span>
          <span v-else-if="leaderboard?.previousGap?.targetReached">已并列上一名</span>
          <span v-else>距上一名 <b>{{ leaderboard?.previousGap?.displayValue || '--' }}</b></span>
        </span>
        <strong>去冲榜 <van-icon name="arrow" size="14" /></strong>
      </button>
    </section>
    <van-popup
      v-model:show="statisticsDetailVisible"
      class="statistics-detail-popup"
      position="bottom"
      round
      close-on-popstate
      :style="{ height: '82%' }"
      @closed="resetStatisticsDetails"
    >
      <div class="statistics-detail-shell">
        <header class="statistics-detail-header">
          <button type="button" :aria-label="statisticsDetailSelected ? '返回明细列表' : '关闭明细'" @click="closeStatisticsDetails">
            <van-icon :name="statisticsDetailSelected ? 'arrow-left' : 'cross'" size="20" />
          </button>
          <div>
            <strong>{{ statisticsDetailSelected ? '记录详情' : statisticsDetailTitle }}</strong>
            <span v-if="!statisticsDetailSelected">{{ statisticsDetailSubtitle }}</span>
          </div>
          <span class="statistics-detail-header__spacer" />
        </header>

        <div v-if="statisticsDetailSelected?.kind === 'lead'" class="statistics-record-detail">
          <div class="statistics-record-hero">
            <SmartAvatar class="statistics-record-hero__avatar" :seed="leadAvatarSeed(statisticsDetailSelected.id)" :size="48" label="" />
            <div>
              <strong>{{ statisticsDetailSelected.submittedName }}</strong>
              <span>{{ formatLeadNo(statisticsDetailSelected.leadNo) }}</span>
            </div>
            <span class="recent-lead__status" :class="`recent-lead__status--${statusClass(statisticsDetailSelected.status)}`">
              {{ formatLeadStatus(statisticsDetailSelected.status) }}
            </span>
          </div>
          <van-cell-group inset>
            <van-cell title="意向课程" :value="statisticsDetailSelected.courseName || '--'" />
            <van-cell title="来源渠道" :value="statisticsDetailSelected.sourceLabel || '--'" />
            <van-cell title="联系方式" :value="statisticsDetailSelected.mobileMasked || '--'" />
            <van-cell title="所在地区" :value="statisticsDetailSelected.location || '--'" />
            <van-cell title="提交时间" :value="formatDateTime(statisticsDetailSelected.submittedAt)" />
          </van-cell-group>
          <section v-if="statisticsDetailSelected.timeline?.length" class="statistics-record-timeline">
            <h3>流程时间线</h3>
            <div v-for="(event, index) in statisticsDetailSelected.timeline" :key="event.id" class="statistics-record-timeline__item">
              <span :class="{ 'is-current': index === statisticsDetailSelected.timeline.length - 1 }" />
              <div>
                <strong>{{ event.title }}</strong>
                <p v-if="event.description">{{ event.description }}</p>
                <time>{{ formatDateTime(event.occurredAt) }}</time>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="statisticsDetailSelected?.kind === 'withdrawal'" class="statistics-record-detail">
          <div class="statistics-withdrawal-hero">
            <span>实付金额</span>
            <strong>¥{{ formatAmount(statisticsDetailSelected.approvedAmount) }}</strong>
            <small>已打款</small>
          </div>
          <van-cell-group inset>
            <van-cell title="申请编号" :value="statisticsDetailSelected.withdrawalNo" />
            <van-cell title="申请金额" :value="`¥${formatAmount(statisticsDetailSelected.applicationAmount)}`" />
            <van-cell title="开户名" :value="statisticsDetailSelected.accountNameSnapshot || '--'" />
            <van-cell title="收款银行" :value="statisticsDetailSelected.bankNameSnapshot" />
            <van-cell title="收款卡号" :value="statisticsDetailSelected.maskedCardNumber" />
            <van-cell title="申请时间" :value="formatDateTime(statisticsDetailSelected.submittedAt)" />
            <van-cell title="打款时间" :value="formatDateTime(statisticsDetailSelected.paidAt)" />
          </van-cell-group>
        </div>

        <div v-else class="statistics-detail-content">
          <div v-if="statisticsDetailInitialLoading" class="statistics-detail-state">
            <van-loading size="24" color="var(--h5-primary)">明细加载中</van-loading>
          </div>
          <van-empty
            v-else-if="statisticsDetailError && !statisticsDetailItems.length"
            image="error"
            :description="statisticsDetailError"
          >
            <van-button size="small" type="primary" @click="loadStatisticsDetails(true)">重新加载</van-button>
          </van-empty>
          <van-empty v-else-if="!statisticsDetailItems.length" description="当前周期暂无明细" />
          <van-pull-refresh v-else v-model="statisticsDetailRefreshing" @refresh="refreshStatisticsDetails">
            <div v-if="statisticsDetailError" class="statistics-detail-inline-error">
              <span>{{ statisticsDetailError }}</span>
              <button type="button" @click="loadStatisticsDetails(false)">重试</button>
            </div>
            <van-list
              v-model:loading="statisticsDetailLoading"
              :finished="statisticsDetailFinished"
              finished-text="没有更多了"
              :immediate-check="false"
              @load="loadStatisticsDetails(false)"
            >
              <button
                v-for="item in statisticsDetailItems"
                :key="item.id"
                type="button"
                class="statistics-detail-row"
                @click="openStatisticsItem(item)"
              >
                <SmartAvatar v-if="isLeadStatisticsDetail(item)" class="statistics-detail-row__avatar" :seed="leadAvatarSeed(item.id)" :size="40" label="" />
                <span v-else class="statistics-detail-row__avatar is-withdrawal">¥</span>
                <span v-if="isLeadStatisticsDetail(item)" class="statistics-detail-row__main">
                  <span class="statistics-detail-row__head">
                    <strong>{{ item.submittedName }}</strong>
                    <small :class="`is-${statusClass(item.status)}`">{{ formatLeadStatus(item.status) }}</small>
                  </span>
                  <span>{{ formatLeadNo(item.leadNo) }} · {{ item.courseName }}</span>
                  <time>提交于 {{ formatDateTime(item.submittedAt) }}</time>
                </span>
                <span v-else-if="isWithdrawalStatisticsDetail(item)" class="statistics-detail-row__main">
                  <span class="statistics-detail-row__head">
                    <strong>¥{{ formatAmount(item.approvedAmount) }}</strong>
                    <small class="is-success">已打款</small>
                  </span>
                  <span>{{ item.withdrawalNo }} · {{ item.bankNameSnapshot }}</span>
                  <time>打款于 {{ formatDateTime(item.paidAt) }}</time>
                </span>
                <van-icon name="arrow" class="statistics-detail-row__arrow" />
              </button>
            </van-list>
          </van-pull-refresh>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.statistics-detail-popup {
  right: auto;
  left: 50%;
  width: 100%;
  max-width: 10rem;
  overflow: hidden;
  background: var(--h5-glass-surface-strong);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
  transform: translate3d(-50%, 0, 0);
}
.statistics-detail-shell {
  display: flex;
  height: 100%;
  flex-direction: column;
}
.statistics-detail-header {
  z-index: 2;
  display: grid;
  flex: 0 0 auto;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--h5-divider);
  background: color-mix(in srgb, var(--h5-card-bg) 80%, transparent);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
}
.statistics-detail-header > button {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
}
.statistics-detail-header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}
.statistics-detail-header strong,
.statistics-detail-header span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.statistics-detail-header strong { font-size: 16px; }
.statistics-detail-header > div > span { color: var(--h5-text-secondary); font-size: 11px; }
.statistics-detail-header__spacer { width: 42px; }
.statistics-detail-content,
.statistics-record-detail {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
}
.statistics-detail-state {
  display: flex;
  min-height: 300px;
  align-items: center;
  justify-content: center;
}
.statistics-detail-content :deep(.van-pull-refresh) { min-height: 100%; }
.statistics-detail-content :deep(.van-list__finished-text),
.statistics-detail-content :deep(.van-list__loading) { color: var(--h5-text-placeholder); font-size: 11px; }
.statistics-detail-inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 10px 16px 0;
  padding: 9px 10px;
  border-radius: 8px;
  background: #fff1f0;
  color: var(--h5-danger);
  font-size: 11px;
}
.statistics-detail-inline-error button {
  flex: 0 0 auto;
  padding: 3px;
  border: 0;
  background: transparent;
  color: var(--h5-primary);
  font: inherit;
}
.statistics-detail-row {
  display: grid;
  width: calc(100% - 32px);
  min-height: 92px;
  grid-template-columns: 40px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 10px;
  margin: 0 16px;
  padding: 12px 0;
  border: 0;
  border-bottom: 1px solid var(--h5-divider);
  background: transparent;
  color: var(--h5-text-primary);
  font: inherit;
  text-align: left;
}
.statistics-detail-row__avatar {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--h5-primary-light);
  color: var(--h5-primary-dark);
  font-size: 14px;
  font-weight: 700;
}
.statistics-detail-row__avatar.is-withdrawal { background: #fff8e6; color: var(--h5-warning); }
.statistics-detail-row__main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.statistics-detail-row__main > span,
.statistics-detail-row__main > time {
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.statistics-detail-row__main > time { color: var(--h5-text-placeholder); font-size: 10px; }
.statistics-detail-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.statistics-detail-row__head strong {
  min-width: 0;
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.statistics-detail-row__head small {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 10px;
  line-height: 16px;
}
.statistics-detail-row__head small.is-success { background: #f0f9eb; color: var(--h5-success); }
.statistics-detail-row__head small.is-danger { background: #fff1f0; color: var(--h5-danger); }
.statistics-detail-row__head small.is-warning { background: #fff8e6; color: var(--h5-warning); }
.statistics-detail-row__head small.is-muted { background: var(--h5-glass-sunken); color: var(--h5-text-secondary); }
.statistics-detail-row__arrow { color: var(--h5-text-placeholder); }
.statistics-record-detail { padding: 16px 0 28px; }
.statistics-record-hero {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin: 0 16px 14px;
  padding: 16px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 12px;
  background: var(--h5-glass-surface-strong);
}
.statistics-record-hero__avatar {
  display: flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--h5-primary-light);
  color: var(--h5-primary-dark);
  font-size: 16px;
  font-weight: 700;
}
.statistics-record-hero > div { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.statistics-record-hero > div strong,
.statistics-record-hero > div span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.statistics-record-hero > div strong { font-size: 16px; }
.statistics-record-hero > div span { color: var(--h5-text-secondary); font-size: 11px; }
.statistics-record-detail :deep(.van-cell-group--inset) { margin: 0 16px; }
.statistics-record-detail :deep(.van-cell__title) { flex: 0 0 78px; color: var(--h5-text-secondary); }
.statistics-record-detail :deep(.van-cell__value) { overflow-wrap: anywhere; color: var(--h5-text-primary); }
.statistics-withdrawal-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  margin: 0 16px 14px;
  padding: 22px 16px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 12px;
  background: var(--h5-glass-surface-strong);
}
.statistics-withdrawal-hero > span { color: var(--h5-text-secondary); font-size: 12px; }
.statistics-withdrawal-hero > strong { color: var(--h5-warning); font-size: 30px; font-variant-numeric: tabular-nums; }
.statistics-withdrawal-hero > small { color: var(--h5-success); font-size: 12px; }
.statistics-record-timeline {
  margin: 14px 16px 0;
  padding: 16px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 12px;
  background: var(--h5-glass-surface-strong);
}
.statistics-record-timeline h3 { margin: 0 0 14px; font-size: 15px; }
.statistics-record-timeline__item {
  position: relative;
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 10px;
  padding-bottom: 18px;
}
.statistics-record-timeline__item:last-child { padding-bottom: 0; }
.statistics-record-timeline__item::before {
  position: absolute;
  top: 12px;
  bottom: 0;
  left: 5px;
  width: 1px;
  background: var(--h5-divider);
  content: '';
}
.statistics-record-timeline__item:last-child::before { display: none; }
.statistics-record-timeline__item > span {
  z-index: 1;
  width: 11px;
  height: 11px;
  margin-top: 3px;
  border: 2px solid var(--h5-card-bg);
  border-radius: 50%;
  background: var(--h5-text-placeholder);
  box-shadow: 0 0 0 1px var(--h5-divider);
}
.statistics-record-timeline__item > span.is-current { background: var(--h5-primary); box-shadow: 0 0 0 2px var(--h5-primary-opacity); }
.statistics-record-timeline__item > div { min-width: 0; }
.statistics-record-timeline__item strong { display: block; font-size: 13px; }
.statistics-record-timeline__item p { margin: 3px 0; color: var(--h5-text-secondary); font-size: 11px; line-height: 1.5; }
.statistics-record-timeline__item time { color: var(--h5-text-placeholder); font-size: 10px; }

/* 首页 1:1 复刻：按参考图固定顺序、卡片密度和移动端第一屏比例。 */
.home-page {
  min-height: 100vh;
  padding: 20px 14px 108px;
  background: transparent;
}

.home-card,
.home-follow-card {
  margin: 0 0 12px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 16px;
  background: var(--h5-glass-surface);
  box-shadow: var(--h5-glass-shadow);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
}

.home-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin: 0;
  padding: 0 4px 16px;
  border-radius: 0;
  background: transparent;
  color: var(--h5-text-primary);
}

.home-header__copy { min-width: 0; }
.home-header__copy h1 {
  margin: 0;
  overflow: hidden;
  color: #08090c;
  font-size: 26px;
  font-weight: 800;
  line-height: 34px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-header__copy p {
  margin: 2px 0 0;
  color: #7f8792;
  font-size: 14px;
  line-height: 20px;
}

.home-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.home-header__bell {
  display: flex;
  width: 46px;
  height: 46px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--h5-glass-border);
  border-radius: 50%;
  background: var(--h5-glass-surface-subtle);
  color: #68707c;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), 0 8px 22px rgba(31, 35, 48, 0.1);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
}

.home-header__bell:active { transform: scale(0.98); }
.home-header__actions :deep(.van-badge) {
  min-width: 20px;
  border: 2px solid #fff;
  background: var(--h5-primary);
  font-variant-numeric: tabular-nums;
}

.home-header__avatar {
  display: flex;
  width: 54px;
  height: 54px;
  align-items: center;
  justify-content: center;
  border: 3px solid #fff;
  border-radius: 50%;
  background: var(--h5-primary-light);
  color: var(--h5-primary-dark);
  font-size: 19px;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(31, 35, 48, 0.12);
}

.home-profile-error {
  height: auto;
  margin: -4px 0 12px;
  border-radius: 12px;
}

.home-profile-error :deep(.van-notice-bar__content) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.home-profile-error button,
.home-inline-state button,
.home-block-state button {
  flex: 0 0 auto;
  padding: 4px 8px;
  border: 0;
  border-radius: 999px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font: inherit;
  font-size: 12px;
}

.home-earnings-card {
  min-height: 184px;
  padding: 15px 18px 13px;
}

.home-earnings-card--locked { display: flex; flex-direction: column; }

.home-earnings-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
}

.home-earnings-main > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.home-earnings-primary { min-width: 0; }

.home-earnings-label {
  color: #7e8794;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.home-earnings-amount {
  display: block;
  margin-top: 8px;
  color: var(--h5-primary);
  font-size: 40px;
  font-weight: 800;
  line-height: 54px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.home-withdraw-button {
  flex: 0 0 auto;
  min-width: 118px;
  height: 44px;
  padding: 0 22px;
  border: 0;
  border-radius: 999px;
  background: var(--h5-gradient);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--h5-primary) 28%, transparent);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.home-earnings-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(125, 133, 143, 0.14);
}

.home-earnings-metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  text-align: center;
  border-right: 1px solid rgba(125, 133, 143, 0.12);
}

.home-earnings-metric:last-child { border-right: 0; }

.home-earnings-metric__label {
  color: #7e8794;
  font-size: 12px;
  line-height: 16px;
}

.home-earnings-metric__value {
  overflow: hidden;
  color: #11151b;
  font-size: 17px;
  font-weight: 800;
  line-height: 22px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-earnings-metric--available .home-earnings-metric__value {
  color: var(--h5-primary);
}

.home-earnings-sub {
  display: flex;
  min-width: 0;
  gap: 14px;
  margin: 10px 0 0;
  color: #747b86;
  font-size: 13px;
  line-height: 20px;
}

.home-earnings-sub span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-inline-state,
.home-block-state,
.home-empty-row {
  display: flex;
  min-height: 86px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--h5-text-secondary);
  font-size: 13px;
  text-align: center;
}

.home-block-state { flex-direction: column; min-height: 126px; }
.home-block-state--compact { min-height: 128px; }
.home-empty-row { min-height: 128px; color: var(--h5-text-placeholder); }

.home-follow-card {
  width: 100%;
  min-height: 112px;
  padding: 15px 18px 13px;
}

.home-follow-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.home-follow-card__header h2 {
  margin: 0;
  color: var(--h5-text-primary);
  font-size: 15px;
  font-weight: 700;
  line-height: 21px;
}

.home-follow-card__retry {
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--h5-primary);
  font: inherit;
  font-size: 12px;
}

.home-follow-card__error {
  margin: 16px 0 3px;
  color: var(--h5-danger);
  font-size: 12px;
  line-height: 18px;
}

.home-follow-card__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 9px;
}

.home-follow-card__metric {
  display: flex;
  min-width: 0;
  min-height: 53px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0 8px;
  border: 0;
  border-right: 1px solid var(--h5-border);
  background: transparent;
  color: var(--h5-text-secondary);
  font: inherit;
  text-align: center;
}

.home-follow-card__metric:last-child { border-right: 0; }
.home-follow-card__metric:active:not(:disabled) { border-radius: 10px; background: var(--h5-primary-opacity); }
.home-follow-card__metric:disabled { cursor: default; opacity: 1; }
.home-follow-card__metric strong {
  color: var(--h5-text-primary);
  font-size: 21px;
  font-weight: 800;
  line-height: 27px;
  font-variant-numeric: tabular-nums;
}

.home-follow-card__metric span {
  overflow: hidden;
  max-width: 100%;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-follow-card__metric--pending strong { color: var(--h5-primary); }
.home-follow-card__metric--unreachable strong { color: var(--h5-warning); }
.home-follow-card__metric--invalid strong { color: var(--h5-danger); }

@media (max-width: 360px) {
  .home-follow-card { padding-right: 12px; padding-left: 12px; }
  .home-follow-card__metric { padding-right: 4px; padding-left: 4px; }
  .home-follow-card__metric span { font-size: 11px; }
}

.home-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 12px;
}

.home-section-title h2,
.statistics-title-wrap h2 {
  margin: 0;
  color: #11151b;
  font-size: 18px;
  font-weight: 800;
  line-height: 24px;
}

.home-section-title > button {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: #7d858f;
  font: inherit;
  font-size: 13px;
}

.home-statistics {
  min-height: 160px;
  padding: 15px 18px 13px;
}

.statistics-header {
  margin-bottom: 8px;
}

.statistics-title-wrap {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.period-tabs {
  width: 236px;
  min-width: 0;
  flex: 0 1 236px;
  margin-left: auto;
}

.period-tabs :deep(.liquid-segmented) {
  border-color: var(--h5-glass-border);
  background: color-mix(in srgb, var(--h5-glass-sunken) 78%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 42%, transparent);
}

.period-tabs :deep(.liquid-segmented__indicator) {
  border: 1px solid color-mix(in srgb, var(--h5-primary) 42%, var(--h5-glass-border));
  background: color-mix(in srgb, var(--h5-primary) 25%, var(--h5-card-bg));
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 62%, transparent), 0 4px 12px color-mix(in srgb, var(--h5-primary) 18%, transparent);
}

.period-tabs :deep(.liquid-segmented__item.is-active) {
  color: var(--h5-primary-dark);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .period-tabs :deep(.liquid-segmented) {
    background: color-mix(in srgb, var(--h5-primary-light) 52%, var(--h5-card-bg));
  }

  .period-tabs :deep(.liquid-segmented__indicator) {
    background: color-mix(in srgb, var(--h5-primary) 22%, var(--h5-card-bg));
  }
}

@media (prefers-reduced-transparency: reduce) {
  .period-tabs :deep(.liquid-segmented) {
    background: color-mix(in srgb, var(--h5-primary-light) 52%, var(--h5-card-bg));
  }

  .period-tabs :deep(.liquid-segmented__indicator) {
    background: color-mix(in srgb, var(--h5-primary) 22%, var(--h5-card-bg));
  }
}

.statistics-body { min-height: 72px; }

.statistics-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.statistics-metric {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 72px;
  grid-template-columns: minmax(0, 1fr) 30px;
  grid-template-rows: auto 1fr;
  align-items: center;
  row-gap: 3px;
  padding: 0 10px 0 0;
  border: 0;
  border-right: 1px solid #edf0f4;
  background: transparent;
  color: var(--h5-text-primary);
  font: inherit;
  text-align: left;
}

.statistics-metric:last-child { border-right: 0; padding-right: 0; }
.statistics-metric + .statistics-metric { padding-left: 30px; }
.statistics-metric > span {
  grid-column: 1 / -1;
  color: #747c87;
  font-size: 14px;
  line-height: 20px;
}

.statistics-metric strong {
  min-width: 0;
  max-width: 100%;
  color: #1c2027;
  font-size: clamp(26px, 3vw, 36px);
  font-weight: 800;
  line-height: 36px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.statistics-metric i {
  display: flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f5f6f8;
  color: #8b929d;
  font-style: normal;
}

.statistics-metric.is-locked { opacity: 0.62; }

.home-recent {
  min-height: 216px;
  padding: 15px 18px 13px;
}

.recent-list { margin-top: -2px; }

.recent-lead {
  display: grid;
  width: 100%;
  min-height: 52px;
  grid-template-columns: 38px minmax(0, 1fr) auto 16px;
  align-items: center;
  gap: 12px;
  padding: 7px 0;
  border: 0;
  border-top: 1px solid #edf0f4;
  background: transparent;
  color: var(--h5-text-primary);
  font: inherit;
  text-align: left;
}

.recent-lead:first-child { border-top: 0; }

.recent-lead__avatar {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #ffe7e9;
  color: var(--h5-primary);
  font-size: 14px;
  font-weight: 700;
}

.recent-lead__name {
  grid-column: 2;
  grid-row: 1;
  overflow: hidden;
  min-width: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1c2027;
  font-size: 15px;
  font-weight: 700;
}

.recent-lead__status {
  grid-column: 3;
  grid-row: 1;
  display: inline-flex;
  overflow: hidden;
  min-width: 0;
  max-width: 74px;
  height: 22px;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  border-radius: 6px;
  background: #eaf2ff;
  color: #397ee8;
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-lead__status--success { background: #e8f8ef; color: var(--h5-success); }
.recent-lead__status--warning { background: #fff1df; color: #ff8a2a; }
.recent-lead__status--danger { background: #fff0f1; color: var(--h5-danger); }
.recent-lead__status--muted { background: #f1f2f4; color: #8b929d; }
.recent-lead__arrow { grid-column: 4; grid-row: 1; color: #9aa1ab; }

.home-leaderboard {
  min-height: 250px;
  padding: 15px 18px 13px;
  border: 1px solid var(--h5-glass-border);
  box-shadow: var(--h5-glass-shadow);
  color: var(--h5-text-primary);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .home-card,
  .home-follow-card,
  .home-header__bell {
    background: var(--h5-glass-surface-fallback);
  }

}

@media (prefers-reduced-transparency: reduce) {
  .home-leaderboard {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

.leaderboard-card-header {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 38px;
}

.leaderboard-card-header__icon {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: color-mix(in srgb, var(--h5-primary) 16%, transparent);
  color: var(--h5-primary);
}

.leaderboard-card-header__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 7px;
}

.leaderboard-card-header__copy strong {
  min-width: 0;
  overflow: hidden;
  font-size: 18px;
  line-height: 23px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-card-header__period {
  display: inline-flex;
  min-height: 20px;
  flex: 0 0 auto;
  align-items: center;
  padding: 0 6px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--h5-primary) 14%, transparent);
  color: var(--h5-primary);
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
}

.leaderboard-card-header > button,
.leaderboard-card-self,
.leaderboard-compact-row {
  border: 0;
  font: inherit;
  text-align: left;
}

.leaderboard-card-header > button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
  padding: 4px 0;
  background: transparent;
  color: var(--h5-primary);
  font-size: 12px;
}

.leaderboard-compact-list {
  margin-top: 7px;
}

.leaderboard-compact-row {
  display: grid;
  width: 100%;
  min-height: 47px;
  grid-template-columns: 30px 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 5px 8px;
  border-bottom: 1px solid var(--h5-glass-divider);
  background: transparent;
  color: var(--h5-text-primary);
}

.leaderboard-compact-row:last-child { border-bottom: 0; }
.leaderboard-compact-row.rank-1 { border-radius: 10px; background: color-mix(in srgb, var(--h5-primary) 14%, var(--h5-glass-surface-subtle)); }

.leaderboard-compact-row__rank {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.leaderboard-compact-row.rank-1 .leaderboard-compact-row__rank {
  background: color-mix(in srgb, var(--h5-primary) 38%, var(--h5-card-bg));
  color: var(--h5-primary-dark);
}
.leaderboard-compact-row.rank-2 .leaderboard-compact-row__rank,
.leaderboard-compact-row.rank-3 .leaderboard-compact-row__rank {
  background: color-mix(in srgb, var(--h5-primary-light) 82%, var(--h5-primary));
  color: var(--h5-primary-dark);
}

.leaderboard-compact-row__avatar,
.leaderboard-card-self__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--h5-glass-surface-subtle);
  color: var(--h5-primary);
  font-weight: 800;
}

.leaderboard-compact-row__avatar { width: 34px; height: 34px; border: 2px solid var(--h5-glass-border); font-size: 13px; }
.leaderboard-compact-row.rank-1 .leaderboard-compact-row__avatar {
  border-color: color-mix(in srgb, var(--h5-primary) 68%, var(--h5-glass-border));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--h5-primary) 22%, transparent);
}
.leaderboard-compact-row.rank-2 .leaderboard-compact-row__avatar,
.leaderboard-compact-row.rank-3 .leaderboard-compact-row__avatar { border-color: color-mix(in srgb, var(--h5-primary) 42%, var(--h5-glass-border)); }

.leaderboard-compact-row__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
}

.leaderboard-compact-row__name strong {
  overflow: hidden;
  font-size: 14px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-compact-row__name small {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 8px;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
  font-size: 10px;
  line-height: 15px;
}

.leaderboard-compact-row__value {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--h5-text-primary);
  font-size: 13px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.leaderboard-compact-row__value .van-icon { color: #f5a623; }

.leaderboard-compact-state {
  display: flex;
  min-height: 145px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  text-align: center;
}

.leaderboard-compact-state button {
  padding: 3px 0;
  border: 0;
  background: transparent;
  color: var(--h5-primary);
  font: inherit;
}

.leaderboard-card-self {
  display: grid;
  width: 100%;
  min-height: 44px;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  margin-top: 7px;
  padding: 5px 8px;
  border-radius: 11px;
  background: var(--h5-glass-surface-subtle);
  color: var(--h5-text-primary);
}

.leaderboard-card-self__avatar { width: 32px; height: 32px; border: 2px solid var(--h5-glass-border); font-size: 12px; }
.leaderboard-card-self__avatar :deep(img) { width: 100%; height: 100%; border-radius: 50%; }

.leaderboard-card-self__copy {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  white-space: nowrap;
}

.leaderboard-card-self__copy span { overflow: hidden; text-overflow: ellipsis; }
.leaderboard-card-self__copy b { color: var(--h5-text-primary); font-size: 14px; }
.leaderboard-card-self__copy i { width: 1px; height: 14px; flex: 0 0 auto; background: var(--h5-glass-divider); }

.leaderboard-card-self > strong {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--h5-primary);
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 430px) {
  .home-page { padding-right: 16px; padding-left: 16px; }
  .home-earnings-card { padding-right: 18px; padding-left: 18px; }
  .home-statistics,
  .home-recent,
  .home-leaderboard { padding-right: 20px; padding-left: 20px; }
  .statistics-metric + .statistics-metric { padding-left: 12px; }
  .statistics-metric { padding-right: 8px; }
  .statistics-metric strong { font-size: 30px; }
  .home-earnings-amount { font-size: 36px; }
  .home-earnings-row { gap: 10px; margin-top: 10px; padding-top: 10px; }
  .home-earnings-metric__value { font-size: 16px; line-height: 20px; }
  .recent-lead { gap: 8px; }
  .recent-lead__status { max-width: 66px; font-size: 10px; }
}

@media (max-width: 360px) {
  .home-page { padding-right: 12px; padding-left: 12px; }
  .home-header__copy h1 { font-size: 23px; }
  .home-header__avatar { width: 48px; height: 48px; }
  .home-header__bell { width: 42px; height: 42px; }
  .home-earnings-card,
  .home-statistics,
  .home-recent,
  .home-leaderboard { padding-right: 16px; padding-left: 16px; }
  .home-earnings-amount { font-size: 34px; }
  .home-earnings-row { gap: 8px; margin-top: 8px; padding-top: 8px; }
  .home-earnings-metric__value { font-size: 15px; line-height: 20px; }
  .home-withdraw-button { min-width: 82px; padding: 0 14px; }
  .statistics-header { align-items: stretch; flex-direction: column; }
  .period-tabs { width: 100%; flex: 0 0 auto; margin-left: 0; }
  .statistics-metric { grid-template-columns: minmax(0, 1fr) 22px; padding-right: 6px; text-align: center; }
  .statistics-metric + .statistics-metric { padding-left: 6px; }
  .statistics-metric i { width: 22px; height: 22px; }
  .recent-lead { grid-template-columns: 34px minmax(0, 1fr) auto 14px; }
}

:global(.home-page.norem) {
  padding: 20px 14px 108px !important;
}

:global(.home-page.norem .home-card),
:global(.home-page.norem .home-follow-card) {
  margin-bottom: 12px !important;
  border-radius: 16px !important;
}

:global(.home-page.norem .period-tabs.liquid-segmented) {
  width: 236px !important;
  max-width: 100%;
  flex: 0 1 236px !important;
  margin-left: auto !important;
  border-radius: 16px !important;
}

:global(.home-page.norem .home-header) {
  padding: 0 4px 16px !important;
}

:global(.home-page.norem .home-header__copy h1) {
  font-size: 26px !important;
  line-height: 34px !important;
}

:global(.home-page.norem .home-header__copy p) {
  font-size: 14px !important;
  line-height: 20px !important;
}

:global(.home-page.norem .home-header__bell) {
  width: 46px !important;
  height: 46px !important;
}

:global(.home-page.norem .home-header__avatar) {
  width: 54px !important;
  height: 54px !important;
}

:global(.home-page.norem .home-earnings-card) {
  min-height: 170px !important;
  padding: 15px 18px 13px !important;
}

:global(.home-page.norem .home-earnings-main) {
  gap: 16px !important;
}

:global(.home-page.norem .home-earnings-row) {
  gap: 12px !important;
  margin-top: 10px !important;
  padding-top: 10px !important;
}

:global(.home-page.norem .home-earnings-metric__label) {
  font-size: 12px !important;
  line-height: 16px !important;
}

:global(.home-page.norem .home-earnings-metric__value) {
  font-size: 17px !important;
  line-height: 22px !important;
}

:global(.home-page.norem .home-earnings-amount) {
  margin-top: 8px !important;
  font-size: 40px !important;
  line-height: 50px !important;
}

:global(.home-page.norem .home-withdraw-button) {
  min-width: 118px !important;
  height: 44px !important;
  padding: 0 22px !important;
}

:global(.home-page.norem .home-earnings-sub) {
  gap: 14px !important;
  margin-top: 8px !important;
  font-size: 13px !important;
  line-height: 20px !important;
}

:global(.home-page.norem .home-follow-card) {
  min-height: 112px !important;
  padding: 15px 18px 13px !important;
}

:global(.home-page.norem .home-statistics) {
  min-height: 160px !important;
  padding: 15px 18px 13px !important;
}

.leaderboard-compact-row__crown {
  justify-self: center;
}

:global(.home-page.norem .statistics-header) {
  gap: 10px !important;
  margin-bottom: 8px !important;
}

:global(.home-page.norem .home-section-title h2),
:global(.home-page.norem .statistics-title-wrap h2) {
  font-size: 18px !important;
  line-height: 24px !important;
}

:global(.home-page.norem .statistics-body) {
  min-height: 72px !important;
}

:global(.home-page.norem .statistics-metrics) {
  gap: 0 !important;
}

:global(.home-page.norem .statistics-metric) {
  min-height: 72px !important;
  row-gap: 3px !important;
  padding-right: 10px !important;
}

:global(.home-page.norem .statistics-metric + .statistics-metric) {
  padding-left: 16px !important;
}

:global(.home-page.norem .statistics-metric > span) {
  font-size: 14px !important;
  line-height: 20px !important;
}

:global(.home-page.norem .statistics-metric strong) {
  min-width: 0 !important;
  max-width: 100% !important;
  font-size: 30px !important;
  line-height: 36px !important;
}

:global(.home-page.norem .statistics-metric i) {
  width: 30px !important;
  height: 30px !important;
}

:global(.home-page.norem .home-recent) {
  min-height: 196px !important;
  padding: 15px 18px 13px !important;
}

:global(.home-page.norem .home-section-title) {
  gap: 10px !important;
  margin-bottom: 8px !important;
}

:global(.home-page.norem .home-section-title > button) {
  font-size: 13px !important;
}

:global(.home-page.norem .recent-lead) {
  min-height: 48px !important;
  gap: 8px !important;
  padding: 6px 0 !important;
}

:global(.home-page.norem .recent-lead__avatar) {
  width: 34px !important;
  height: 34px !important;
}

:global(.home-page.norem .recent-lead__name) {
  font-size: 15px !important;
}

:global(.home-page.norem .recent-lead__status) {
  min-width: 0 !important;
  max-width: 74px !important;
  height: 22px !important;
  padding: 0 6px !important;
  font-size: 11px !important;
}

:global(.home-page.norem .home-leaderboard) {
  min-height: 250px !important;
  padding: 15px 18px 13px !important;
}

@media (max-width: 430px) {
  :global(.home-page.norem) { padding-right: 16px !important; padding-left: 16px !important; }
  :global(.home-page.norem .home-earnings-card) { padding-right: 18px !important; padding-left: 18px !important; }
  :global(.home-page.norem .home-statistics),
  :global(.home-page.norem .home-recent),
  :global(.home-page.norem .home-leaderboard) { padding-right: 18px !important; padding-left: 18px !important; }
  :global(.home-page.norem .home-earnings-amount) { font-size: 36px !important; }
  :global(.home-page.norem .statistics-metric + .statistics-metric) { padding-left: 4px !important; }
  :global(.home-page.norem .statistics-metric) { grid-template-columns: minmax(0, 1fr) 24px; padding-right: 4px !important; }
  :global(.home-page.norem .statistics-metric strong) { font-size: 22px !important; }
  :global(.home-page.norem .statistics-metric i) { width: 24px !important; height: 24px !important; }
}

@media (max-width: 360px) {
  :global(.home-page.norem) { padding-right: 12px !important; padding-left: 12px !important; }
  :global(.home-page.norem .home-header__copy h1) { font-size: 23px !important; }
  :global(.home-page.norem .home-header__bell) { width: 42px !important; height: 42px !important; }
  :global(.home-page.norem .home-header__avatar) { width: 48px !important; height: 48px !important; }
  :global(.home-page.norem .home-earnings-card),
  :global(.home-page.norem .home-recent),
  :global(.home-page.norem .home-leaderboard) { padding-right: 18px !important; padding-left: 18px !important; }
  :global(.home-page.norem .home-statistics) { padding-right: 12px !important; padding-left: 12px !important; }
  :global(.home-page.norem .home-earnings-amount) { font-size: 34px !important; }
  :global(.home-page.norem .home-earnings-row) { gap: 8px !important; margin-top: 8px !important; padding-top: 8px !important; }
  :global(.home-page.norem .home-earnings-metric__value) { font-size: 15px !important; line-height: 20px !important; }
  :global(.home-page.norem .home-withdraw-button) { min-width: 82px !important; padding: 0 14px !important; }
  :global(.home-page.norem .period-tabs.liquid-segmented) { width: 100% !important; flex: 0 0 auto !important; margin-left: 0 !important; }
  :global(.home-page.norem .statistics-metric) { grid-template-columns: minmax(0, 1fr) 20px; padding-right: 2px !important; }
  :global(.home-page.norem .statistics-metric + .statistics-metric) { padding-left: 2px !important; }
  :global(.home-page.norem .statistics-metric strong) { font-size: 22px !important; line-height: 34px !important; }
  :global(.home-page.norem .statistics-metric i) { width: 20px !important; height: 20px !important; }
  :global(.home-page.norem .recent-lead__status) { max-width: 66px !important; font-size: 10px !important; }
}
</style>

