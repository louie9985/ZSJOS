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
import { getLeaderboard, getLeaderboardConfig, type LeaderboardConfig, type LeaderboardData } from '@/api/leaderboard'
import { getUnreadCount } from '@/api/message'
import { clearMockedEndpoint, wasMockedEndpoint, wasMockedExactEndpoint } from '@/api/mock'
import { formatAmount, formatDateTime, formatLeadNo, formatLeadStatus } from '@/utils/format'
import { formatLeaderboardValue, leaderboardMemberInitial, leaderboardRowGapText } from '@/utils/leaderboard'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'

defineOptions({ name: 'Home' })

const router = useRouter()
const userStore = useUserStore()

const partner = ref<PartnerInfo>()
const summary = ref<CashbackSummary>()
const recentLeads = ref<LeadListItem[]>([])
const unreadCount = ref(0)
const partnerError = ref('')
const summaryLoading = ref(true)
const summaryError = ref('')
const recentLeadsLoading = ref(true)
const recentLeadsError = ref('')
const leadFollowUpSummary = ref<LeadFollowUpSummary>()
const leadFollowUpSummaryLoading = ref(true)
const leadFollowUpSummaryError = ref('')
const leadFollowUpSummaryEmpty = computed(() => {
  const value = leadFollowUpSummary.value
  return !!value && value.followUpPendingCount === 0 && value.unreachableCount === 0 && value.invalidCount === 0
})
const leaderboardConfig = ref<LeaderboardConfig>()
const leaderboard = ref<LeaderboardData>()
const leaderboardConfigLoading = ref(true)
const leaderboardLoading = ref(false)
const leaderboardConfigError = ref('')
const leaderboardError = ref('')
const leaderboardConfigStatus = ref<number>()
const statisticsPeriod = ref<HomeStatisticsPeriod>('month')
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
  converted_lead_count: { label: '成交客资数', permission: 'zsjos:lead:query-submitted', unit: 'count' }
}

const leaderboardVisible = computed(() => leaderboardConfigLoading.value || leaderboardConfig.value?.enabled !== false || !!leaderboardConfigError.value)
const leaderboardUsingMock = computed(() => wasMockedEndpoint('/zsjos/partner/leaderboard'))
const statisticsUsingMock = computed(() => wasMockedExactEndpoint('/zsjos/partner/home-statistics'))
const statisticsDetailsUsingMock = computed(() => wasMockedExactEndpoint('/zsjos/partner/home-statistics/details'))
const statisticsMockLabel = import.meta.env.DEV ? '演示数据' : ''
const canViewLeads = computed(() => userStore.hasPermission('zsjos:lead:query-submitted'))
const canViewEarnings = computed(() => userStore.hasPermission('zsjos:cashback:my-query'))
const canWithdraw = computed(() => userStore.hasPermission('zsjos:withdrawal:apply'))
const earningsSnapshot = computed(() => {
  const value = summary.value
  if (!value) return null
  return {
    estimatedIncome: (value.pendingAmount || 0) + (value.availableAmount || 0) + (value.withdrawingAmount || 0),
    withdrawableIncome: value.availableAmount || 0
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
const leaderboardTitle = computed(() => leaderboard.value
  ? `${leaderboard.value.periodLabel}${leaderboard.value.typeLabel}`
  : '排行榜')
const leaderboardChase = computed(() => {
  const mine = leaderboard.value?.myRank
  if (!mine) return { primary: '暂未上榜', secondary: '提交有效客资后可参与榜单' }
  if (mine.rank === 1) return { primary: '当前第 1 名', secondary: '继续保持领先' }
  if (leaderboard.value?.previousGap?.targetReached) {
    return { primary: `并列第 ${mine.rank} 名`, secondary: '继续冲击更高名次' }
  }
  return {
    primary: `当前第 ${mine.rank} 名`,
    secondary: leaderboard.value?.previousGap?.displayValue
      ? `距上一名 ${leaderboard.value.previousGap.displayValue}`
      : '继续冲击更高名次'
  }
})
const leaderboardPreviewRows = computed(() => (leaderboard.value?.top3?.length ? leaderboard.value.top3 : leaderboard.value?.list || []).slice(0, 3))
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
    unreadCount.value = 0
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
  clearMockedEndpoint('/zsjos/partner/home-statistics')
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
    clearMockedEndpoint('/zsjos/partner/home-statistics/details')
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
  if (!item.mock) {
    statisticsDetailVisible.value = false
    void router.push(item.kind === 'lead' ? `/lead/${item.id}` : `/withdrawal/${item.id}`)
    return
  }
  statisticsDetailSelected.value = item
}

function statisticsDetailInitial(item: HomeStatisticsDetailItem) {
  return item.kind === 'lead' ? recentLeadInitial(item.submittedName) : '¥'
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
})
onActivated(() => { void loadUnreadCount() })

function goWithdraw() {
  router.push('/withdrawal/apply')
}

function goLeadList() {
  router.push('/lead/list')
}

function goLeadFollowUp() {
  router.push('/lead/follow-up')
}

function goMessages() { router.push('/messages') }

function goLeaderboard() { router.push('/leaderboard') }

function leaderboardPreviewGap(index: number) {
  const item = leaderboardPreviewRows.value[index]
  if (!item) return ''
  return leaderboardRowGapText(item, leaderboardPreviewRows.value[index - 1], leaderboard.value?.valueUnit)
}

function formatMoney(value: number) {
  return `¥${value.toFixed(2)}`
}

function formatPercent(numerator = 0, denominator = 0) {
  if (!denominator) return '0%'
  const value = (numerator / denominator) * 100
  return `${Number.isInteger(value) ? value.toFixed(0) : value.toFixed(2)}%`
}

function goLeadDetail(id: number) {
  router.push(`/lead/${id}`)
}

function recentLeadInitial(name: string) {
  return name.trim().charAt(0) || '客'
}

function recentLeadTone(id: number) {
  return `tone-${Math.abs(id) % 4}`
}

function partnerInitial() {
  return (partner.value?.name || userStore.nickname || '兼').trim().charAt(0) || '兼'
}

function recentLeadCourse(item: LeadListItem) {
  return item.primaryProduct?.spuName
    || item.intendedProducts?.find(product => product.primary)?.spuName
    || item.intendedProducts?.[0]?.spuName
    || '未填写意向课程'
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
        <van-image v-if="userStore.avatar" class="home-header__avatar" round :src="userStore.avatar" fit="cover" />
        <span v-else class="home-header__avatar home-header__avatar--fallback">{{ partnerInitial() }}</span>
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
            <div>
              <span class="home-earnings-label">可提现</span>
              <strong class="home-earnings-amount">{{ formatMoney(summary?.availableAmount || 0) }}</strong>
            </div>
            <button v-if="canWithdraw" type="button" class="home-withdraw-button" @click="goWithdraw">去提现</button>
          </div>
          <div v-if="earningsSnapshot" class="home-earnings-row">
            <div class="home-earnings-metric">
              <span class="home-earnings-metric__label">预计收入</span>
              <strong class="home-earnings-metric__value">{{ formatMoney(earningsSnapshot.estimatedIncome) }}</strong>
            </div>
            <div class="home-earnings-metric home-earnings-metric--primary">
              <span class="home-earnings-metric__label">可提现收入</span>
              <strong class="home-earnings-metric__value">{{ formatMoney(earningsSnapshot.withdrawableIncome) }}</strong>
            </div>
          </div>
          <p class="home-earnings-sub">
            <span>累计已赚 {{ formatMoney(summary?.totalAmount || 0) }}</span>
            <span>待结算 {{ formatMoney(summary?.pendingAmount || 0) }}</span>
          </p>
        </template>
      </van-skeleton>
    </section>

    <section v-else class="home-card home-earnings-card home-earnings-card--locked" aria-label="收益概览">
      <span class="home-earnings-label">可提现</span>
      <strong class="home-earnings-amount">--</strong>
      <p class="home-earnings-sub"><span>暂无权限查看收益</span></p>
    </section>

    <button type="button" class="home-follow-card" @click="goLeadFollowUp">
      <span class="home-follow-card__dot" />
      <span v-if="leadFollowUpSummaryLoading">客资跟进提醒加载中</span>
      <span v-else-if="leadFollowUpSummaryError">客资跟进提醒加载失败</span>
      <span v-else-if="leadFollowUpSummaryEmpty">暂无需要处理的客资</span>
      <span v-else>待跟进客资 {{ leadFollowUpSummary?.followUpPendingCount || 0 }} 条</span>
      <van-icon name="arrow" size="18" />
    </button>

    <section class="home-card home-statistics" aria-label="我的战绩">
      <div class="home-section-title statistics-header">
        <div class="statistics-title-wrap">
          <h2>我的战绩</h2>
          <span v-if="statisticsMockLabel && statisticsUsingMock" class="mock-badge">{{ statisticsMockLabel }}</span>
        </div>
        <LiquidSegmentedControl
          class="period-tabs"
          :model-value="statisticsPeriod"
          :items="statisticsPeriods"
          ariaLabel="我的战绩周期"
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
              <span>提交</span>
              <strong>{{ Math.round(statistics.leadCount) }}</strong>
              <i><van-icon :name="canOpenStatisticsMetric('lead_count') ? 'arrow' : 'lock'" size="15" /></i>
            </button>
            <button
              type="button"
              class="statistics-metric"
              :class="{ 'is-locked': !canOpenStatisticsMetric('valid_lead_count') }"
              @click="openStatisticsDetails('valid_lead_count')"
            >
              <span>有效</span>
              <strong>{{ Math.round(statistics.validLeadCount) }}</strong>
              <i><van-icon :name="canOpenStatisticsMetric('valid_lead_count') ? 'arrow' : 'lock'" size="15" /></i>
            </button>
            <button
              type="button"
              class="statistics-metric"
              :class="{ 'is-locked': !canOpenStatisticsMetric('converted_lead_count') }"
              @click="openStatisticsDetails('converted_lead_count')"
            >
              <span>成交</span>
              <strong>{{ Math.round(statistics.convertedLeadCount) }}</strong>
              <i><van-icon :name="canOpenStatisticsMetric('converted_lead_count') ? 'arrow' : 'lock'" size="15" /></i>
            </button>
          </div>
          <div class="statistics-rates">
            <span>转化率 {{ formatPercent(statistics.validLeadCount, statistics.leadCount) }}</span>
            <span aria-hidden="true">·</span>
            <span>成交率 {{ formatPercent(statistics.convertedLeadCount, statistics.leadCount) }}</span>
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
            <span class="recent-lead__avatar" :class="recentLeadTone(lead.id)">{{ recentLeadInitial(lead.submittedName) }}</span>
            <span class="recent-lead__name">{{ lead.submittedName || '未命名客户' }}</span>
            <span class="recent-lead__product">{{ recentLeadCourse(lead) }}</span>
            <span class="recent-lead__status" :class="`recent-lead__status--${statusClass(lead.status)}`">
              {{ formatLeadStatus(lead.status) }}
            </span>
            <van-icon class="recent-lead__arrow" name="arrow" size="16" />
          </button>
        </div>
      </van-skeleton>
    </section>

    <section v-if="leaderboardVisible" class="home-card home-leaderboard" aria-label="排行榜">
      <van-notice-bar v-if="leaderboardUsingMock" class="leaderboard-notice" color="#8a6100" background="#fff7df" left-icon="info-o">
        开发环境演示数据
      </van-notice-bar>
      <div class="home-section-title home-section-title--tight">
        <h2>排行榜</h2>
        <button type="button" @click="goLeaderboard">
          查看全部 <van-icon name="arrow" size="14" />
        </button>
      </div>
      <button type="button" class="leaderboard-summary-card" @click="goLeaderboard">
        <span class="leaderboard-summary-card__icon"><van-icon name="trophy-o" size="30" /></span>
        <span class="leaderboard-summary-card__main">
          <strong>{{ leaderboardTitle }}</strong>
          <span v-if="leaderboardConfigLoading || leaderboardLoading">榜单加载中</span>
          <span v-else-if="leaderboardConfigError">{{ leaderboardConfigUnavailable ? '排行榜功能暂不可用' : leaderboardConfigError }}</span>
          <span v-else-if="leaderboardError">{{ leaderboardError }}</span>
          <span v-else-if="leaderboard">{{ leaderboardChase.primary }} · {{ leaderboardChase.secondary }}</span>
          <span v-else>排行榜暂未开启</span>
        </span>
        <span v-if="leaderboard?.myRank" class="leaderboard-summary-card__rank">第 {{ leaderboard.myRank.rank }} 名</span>
        <van-icon class="leaderboard-summary-card__arrow" name="arrow" size="18" />
      </button>
      <div v-if="leaderboardPreviewRows.length" class="leaderboard-preview-list">
        <button
          v-for="(item, index) in leaderboardPreviewRows"
          :key="item.partnerId"
          type="button"
          class="leaderboard-preview-row"
          :class="{ 'is-me': item.isMe }"
          @click="goLeaderboard"
        >
          <span class="leaderboard-preview-row__avatar" :class="`rank-${item.rank}`">{{ leaderboardMemberInitial(item.displayName) }}</span>
          <span class="leaderboard-preview-row__main">
            <strong>{{ item.displayName }}<small v-if="item.isMe">（我）</small></strong>
            <small>{{ leaderboardPreviewGap(index) }}</small>
          </span>
          <span class="leaderboard-preview-row__value">{{ formatLeaderboardValue(item.value, leaderboard?.valueUnit) }}</span>
        </button>
      </div>
      <div v-else class="leaderboard-preview-empty">
        <span v-if="leaderboardConfigLoading || leaderboardLoading">排行榜加载中</span>
        <span v-else-if="leaderboardConfigError">{{ leaderboardConfigUnavailable ? '排行榜功能暂不可用' : leaderboardConfigError }}</span>
        <span v-else-if="leaderboardError">{{ leaderboardError }}</span>
        <span v-else>排行榜暂未开启</span>
      </div>
      <button type="button" class="leaderboard-footer-link" @click="goLeaderboard">
        查看完整榜单 <van-icon name="arrow" size="14" />
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
          <span v-if="statisticsMockLabel && statisticsDetailsUsingMock" class="mock-badge">演示数据</span>
          <span v-else class="statistics-detail-header__spacer" />
        </header>

        <div v-if="statisticsDetailSelected?.kind === 'lead'" class="statistics-record-detail">
          <div class="statistics-record-hero">
            <span class="statistics-record-hero__avatar">{{ recentLeadInitial(statisticsDetailSelected.submittedName) }}</span>
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
                <span class="statistics-detail-row__avatar" :class="{ 'is-withdrawal': isWithdrawalStatisticsDetail(item) }">
                  {{ statisticsDetailInitial(item) }}
                </span>
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
  background: var(--h5-bg);
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
  background: var(--h5-card-bg);
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
  background: var(--h5-bg);
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
.statistics-detail-row__head small.is-muted { background: var(--h5-bg); color: var(--h5-text-secondary); }
.statistics-detail-row__arrow { color: var(--h5-text-placeholder); }
.statistics-record-detail { padding: 16px 0 28px; }
.statistics-record-hero {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin: 0 16px 14px;
  padding: 16px;
  border-radius: 12px;
  background: var(--h5-card-bg);
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
  border-radius: 12px;
  background: var(--h5-card-bg);
}
.statistics-withdrawal-hero > span { color: var(--h5-text-secondary); font-size: 12px; }
.statistics-withdrawal-hero > strong { color: var(--h5-warning); font-size: 30px; font-variant-numeric: tabular-nums; }
.statistics-withdrawal-hero > small { color: var(--h5-success); font-size: 12px; }
.statistics-record-timeline {
  margin: 14px 16px 0;
  padding: 16px;
  border-radius: 12px;
  background: var(--h5-card-bg);
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
  background:
    radial-gradient(circle at 50% -80px, rgba(255, 255, 255, 0.98), transparent 260px),
    var(--h5-bg);
}

.home-card,
.home-follow-card {
  margin: 0 0 12px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 16px;
  background: var(--h5-card-bg);
  box-shadow: 0 8px 24px rgba(31, 35, 48, 0.07);
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
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #68707c;
  box-shadow: 0 8px 22px rgba(31, 35, 48, 0.09);
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
  padding: 20px 28px 18px;
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

.home-earnings-label {
  color: #7e8794;
  font-size: 14px;
  line-height: 20px;
}

.home-earnings-amount {
  display: block;
  margin-top: 8px;
  color: var(--h5-primary);
  font-size: 48px;
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
  box-shadow: 0 8px 18px rgba(240, 68, 85, 0.22);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.home-earnings-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(125, 133, 143, 0.14);
}

.home-earnings-metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

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

.home-earnings-metric--primary .home-earnings-metric__value {
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
  display: grid;
  width: 100%;
  height: 46px;
  grid-template-columns: 18px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #1f252d;
  font: inherit;
  font-size: 15px;
  text-align: left;
}

.home-follow-card > span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-follow-card__dot {
  width: 7px;
  height: 7px;
  justify-self: center;
  border-radius: 50%;
  background: var(--h5-primary);
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
  min-height: 214px;
  padding: 18px 30px 14px;
}

.statistics-header {
  margin-bottom: 16px;
}

.statistics-title-wrap {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.mock-badge {
  flex: 0 0 auto;
  padding: 2px 6px;
  border-radius: 999px;
  background: #fff8e6;
  color: var(--h5-warning);
  font-size: 10px;
  font-weight: 600;
}

.period-tabs {
  min-width: 0;
  flex: 1;
}

.statistics-body { min-height: 122px; }

.statistics-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.statistics-metric {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 92px;
  grid-template-columns: minmax(0, 1fr) 30px;
  grid-template-rows: auto 1fr;
  align-items: center;
  row-gap: 5px;
  padding: 0 15px 0 0;
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
  color: #1c2027;
  font-size: clamp(26px, 3vw, 36px);
  font-weight: 800;
  line-height: 42px;
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

.statistics-rates {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 36px;
  margin-top: 2px;
  border-top: 1px solid #edf0f4;
  color: #747c87;
  font-size: 13px;
  line-height: 20px;
}

.home-recent {
  min-height: 216px;
  padding: 16px 30px 8px;
}

.recent-list { margin-top: -2px; }

.recent-lead {
  display: grid;
  width: 100%;
  min-height: 52px;
  grid-template-columns: 38px minmax(0, 1fr) auto 16px;
  grid-template-rows: auto auto;
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

.recent-lead__avatar.tone-1 { background: #e7f8ee; color: var(--h5-success); }
.recent-lead__avatar.tone-2 { background: #fff0df; color: #ff8a2a; }
.recent-lead__avatar.tone-3 { background: #e8f2ff; color: #3c82df; }

.recent-lead__name,
.recent-lead__product {
  min-width: 0;
}

.recent-lead__name {
  grid-column: 2;
  grid-row: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1c2027;
  font-size: 15px;
  font-weight: 700;
}

.recent-lead__product {
  grid-column: 2;
  grid-row: 2;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: normal;
  color: #747c87;
  font-size: 13px;
}

.recent-lead__status {
  grid-column: 3;
  grid-row: 1 / span 2;
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
.recent-lead__arrow { grid-column: 4; grid-row: 1 / span 2; color: #9aa1ab; }

.home-leaderboard {
  min-height: 88px;
  padding: 13px 30px 14px;
}

.leaderboard-notice {
  margin: -4px -18px 8px;
  border-radius: 10px;
}

.home-section-title--tight {
  margin-bottom: 10px;
}

.leaderboard-summary-card {
  display: grid;
  width: 100%;
  min-height: 64px;
  grid-template-columns: 44px minmax(0, 1fr) auto 18px;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, #f04455 0%, #ff7180 100%);
  color: #fff;
  text-align: left;
  box-shadow: 0 10px 22px rgba(240, 68, 85, 0.18);
}

.leaderboard-summary-card__icon {
  display: flex;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
}

.leaderboard-summary-card__main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.leaderboard-summary-card__main strong {
  overflow: hidden;
  font-size: 18px;
  font-weight: 800;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-summary-card__main span {
  overflow: hidden;
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-summary-card__rank {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.leaderboard-summary-card__arrow {
  color: rgba(255, 255, 255, 0.9);
}

.leaderboard-preview-list {
  overflow: hidden;
  margin-top: 12px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(31, 35, 48, 0.06);
}

.leaderboard-preview-row {
  display: grid;
  width: 100%;
  min-height: 62px;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  background: transparent;
  color: var(--h5-text-primary);
  font: inherit;
  text-align: left;
}

.leaderboard-preview-row:last-child {
  border-bottom: 0;
}

.leaderboard-preview-row.is-me {
  background: linear-gradient(90deg, rgba(240, 68, 85, 0.1) 0%, rgba(240, 68, 85, 0.02) 72%, transparent 100%);
}

.leaderboard-preview-row__avatar {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f48e99;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
}

.leaderboard-preview-row__avatar.rank-1 {
  background: #e4485b;
}

.leaderboard-preview-row__avatar.rank-2 {
  background: #e95768;
}

.leaderboard-preview-row__main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.leaderboard-preview-row__main strong {
  overflow: hidden;
  color: #1c2027;
  font-size: 14px;
  font-weight: 800;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-preview-row__main strong small {
  color: var(--h5-primary);
}

.leaderboard-preview-row__main > small {
  overflow: hidden;
  color: #7c8490;
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-preview-row__value {
  color: var(--h5-primary);
  font-size: 14px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.leaderboard-preview-empty {
  padding: 14px 0 4px;
  color: var(--h5-text-secondary);
  font-size: 13px;
  text-align: center;
}

.leaderboard-footer-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 10px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--h5-primary);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 430px) {
  .home-page { padding-right: 16px; padding-left: 16px; }
  .home-earnings-card { padding-right: 30px; padding-left: 30px; }
  .home-statistics,
  .home-recent,
  .home-leaderboard { padding-right: 20px; padding-left: 20px; }
  .statistics-metric + .statistics-metric { padding-left: 12px; }
  .statistics-metric { padding-right: 8px; }
  .statistics-metric strong { font-size: 30px; }
  .home-earnings-amount { font-size: 40px; }
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
  .home-earnings-amount { font-size: 38px; }
  .home-earnings-row { gap: 8px; margin-top: 8px; padding-top: 8px; }
  .home-earnings-metric__value { font-size: 15px; line-height: 20px; }
  .home-withdraw-button { min-width: 82px; padding: 0 14px; }
  .statistics-header { align-items: stretch; flex-direction: column; }
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
  box-shadow: 0 8px 24px rgba(31, 35, 48, 0.07) !important;
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
  padding: 17px 28px 16px !important;
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
  font-size: 45px !important;
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
  height: 46px !important;
  gap: 8px !important;
  padding: 0 18px !important;
}

:global(.home-page.norem .home-statistics) {
  min-height: 198px !important;
  padding: 16px 30px 12px !important;
}

:global(.home-page.norem .statistics-header) {
  gap: 10px !important;
  margin-bottom: 14px !important;
}

:global(.home-page.norem .home-section-title h2),
:global(.home-page.norem .statistics-title-wrap h2) {
  font-size: 18px !important;
  line-height: 24px !important;
}

:global(.home-page.norem .statistics-body) {
  min-height: 116px !important;
}

:global(.home-page.norem .statistics-metrics) {
  gap: 0 !important;
}

:global(.home-page.norem .statistics-metric) {
  min-height: 82px !important;
  row-gap: 4px !important;
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
  font-size: clamp(26px, 3vw, 34px) !important;
  line-height: 40px !important;
}

:global(.home-page.norem .statistics-metric i) {
  width: 30px !important;
  height: 30px !important;
}

:global(.home-page.norem .statistics-rates) {
  min-height: 32px !important;
  margin-top: 0 !important;
  gap: 12px !important;
  font-size: 13px !important;
  line-height: 20px !important;
}

:global(.home-page.norem .home-recent) {
  min-height: 196px !important;
  padding: 14px 30px 6px !important;
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

:global(.home-page.norem .recent-lead__product),
:global(.home-page.norem .leaderboard-summary-card__main span) {
  font-size: 13px !important;
  line-height: 18px !important;
}

:global(.home-page.norem .recent-lead__status) {
  min-width: 0 !important;
  max-width: 74px !important;
  height: 22px !important;
  padding: 0 6px !important;
  font-size: 11px !important;
}

:global(.home-page.norem .recent-lead__product) {
  display: -webkit-box !important;
  overflow: hidden !important;
  -webkit-box-orient: vertical !important;
  -webkit-line-clamp: 2 !important;
  white-space: normal !important;
}

:global(.home-page.norem .home-leaderboard) {
  min-height: 78px !important;
  padding: 10px 30px !important;
}

:global(.home-page.norem .home-section-title--tight) {
  margin-bottom: 8px !important;
}

:global(.home-page.norem .leaderboard-summary-card) {
  min-height: 64px !important;
  gap: 12px !important;
  padding: 12px 14px !important;
}

:global(.home-page.norem .leaderboard-summary-card__main strong) {
  font-size: 18px !important;
  line-height: 22px !important;
}

:global(.home-page.norem .leaderboard-preview-list) {
  margin-top: 10px !important;
}

:global(.home-page.norem .leaderboard-preview-row) {
  min-height: 62px !important;
  gap: 12px !important;
}

:global(.home-page.norem .leaderboard-preview-row__main strong) {
  font-size: 14px !important;
  line-height: 18px !important;
}

:global(.home-page.norem .leaderboard-preview-row__value) {
  font-size: 14px !important;
}

@media (max-width: 430px) {
  :global(.home-page.norem) { padding-right: 16px !important; padding-left: 16px !important; }
  :global(.home-page.norem .home-earnings-card) { padding-right: 30px !important; padding-left: 30px !important; }
  :global(.home-page.norem .home-statistics),
  :global(.home-page.norem .home-recent),
  :global(.home-page.norem .home-leaderboard) { padding-right: 20px !important; padding-left: 20px !important; }
  :global(.home-page.norem .home-earnings-amount) { font-size: 40px !important; }
  :global(.home-page.norem .statistics-metric + .statistics-metric) { padding-left: 12px !important; }
  :global(.home-page.norem .statistics-metric) { padding-right: 8px !important; }
  :global(.home-page.norem .statistics-metric strong) { font-size: 30px !important; }
}

@media (max-width: 360px) {
  :global(.home-page.norem) { padding-right: 12px !important; padding-left: 12px !important; }
  :global(.home-page.norem .home-header__copy h1) { font-size: 23px !important; }
  :global(.home-page.norem .home-header__bell) { width: 42px !important; height: 42px !important; }
  :global(.home-page.norem .home-header__avatar) { width: 48px !important; height: 48px !important; }
  :global(.home-page.norem .home-earnings-card),
  :global(.home-page.norem .home-statistics),
  :global(.home-page.norem .home-recent),
  :global(.home-page.norem .home-leaderboard) { padding-right: 16px !important; padding-left: 16px !important; }
  :global(.home-page.norem .home-earnings-amount) { font-size: 38px !important; }
  :global(.home-page.norem .home-earnings-row) { gap: 8px !important; margin-top: 8px !important; padding-top: 8px !important; }
  :global(.home-page.norem .home-earnings-metric__value) { font-size: 15px !important; line-height: 20px !important; }
  :global(.home-page.norem .home-withdraw-button) { min-width: 82px !important; padding: 0 14px !important; }
  :global(.home-page.norem .statistics-metric) { grid-template-columns: minmax(0, 1fr) 22px; padding-right: 6px !important; }
  :global(.home-page.norem .statistics-metric + .statistics-metric) { padding-left: 6px !important; }
  :global(.home-page.norem .statistics-metric i) { width: 22px !important; height: 22px !important; }
  :global(.home-page.norem .recent-lead__status) { max-width: 66px !important; font-size: 10px !important; }
}
</style>
