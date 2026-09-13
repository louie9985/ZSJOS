<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showDialog } from 'vant'
import {
  getLeaderboard,
  getLeaderboardConfig,
  type LeaderboardConfig,
  type LeaderboardData,
  type LeaderboardMember,
  type LeaderboardPeriod,
  type LeaderboardType
} from '@/api/leaderboard'
import {
  formatLeaderboardChase,
  formatLeaderboardTitle,
  formatLeaderboardValue
} from '@/utils/leaderboard'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'
import SmartAvatar from '@/components/SmartAvatar.vue'
import LeaderboardCrown from '@/components/LeaderboardCrown.vue'
import { partnerAvatarSeed } from '@/config/avatar'

defineOptions({ name: 'Leaderboard' })

const route = useRoute()

type PodiumSlot = 'left' | 'center' | 'right'

const periodOptions: Array<{ key: LeaderboardPeriod; label: string }> = [
  { key: 'today', label: '日榜' },
  { key: 'week', label: '周榜' },
  { key: 'month', label: '月榜' },
  { key: 'total', label: '总榜' }
]

const config = ref<LeaderboardConfig>()
const data = ref<LeaderboardData>()
const period = ref<LeaderboardPeriod>('month')
const type = ref<LeaderboardType>('estimated_income')
const rows = ref<LeaderboardMember[]>([])
const pageNo = ref(1)
const configLoading = ref(true)
const listLoading = ref(false)
const finished = ref(false)
const configError = ref('')
const listError = ref('')
const configStatus = ref<number>()

const configUnavailable = computed(() => /接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(configError.value))
const enabledTypeOptions = computed(() => {
  if (!config.value) return []
  return config.value.typeOptions.filter(option => config.value!.enabledTypes.includes(option.key))
})
const leaderboardTitle = computed(() => {
  const selected = enabledTypeOptions.value.find(option => option.key === type.value)
  return formatLeaderboardTitle(data.value?.typeLabel || selected?.label)
})
const chaseText = computed(() => formatLeaderboardChase(data.value))
const podiumMembers = computed<Array<{ member: LeaderboardMember; slot: PodiumSlot }>>(() => {
  const top3 = (data.value?.top3 || []).slice(0, 3)
  if (top3.length === 0) return []
  if (top3.length === 1) return [{ member: top3[0], slot: 'center' }]
  const slots: Array<{ member?: LeaderboardMember; slot: PodiumSlot }> = [
    { member: top3[1], slot: 'left' },
    { member: top3[0], slot: 'center' },
    { member: top3[2], slot: 'right' }
  ]
  return slots.filter((item): item is { member: LeaderboardMember; slot: PodiumSlot } => !!item.member)
})
const rankingRows = computed(() => {
  const podiumIds = new Set((data.value?.top3 || []).map(member => member.partnerId))
  return rows.value.filter(member => !podiumIds.has(member.partnerId))
})

function previousMember(member: LeaderboardMember) {
  const index = rows.value.findIndex(row => row.partnerId === member.partnerId)
  return index > 0 ? rows.value[index - 1] : undefined
}

function rankingGapLabel(member: LeaderboardMember) {
  if (member.rank <= 1) return '榜首'
  const previous = previousMember(member)
  const gap = member.gapToPrevious ?? (previous ? Math.max(0, previous.value - member.value) : null)
  if (gap == null) return '距上一名 --'
  if (gap === 0) return '与上一名并列'
  return `距上一名 ${formatLeaderboardValue(gap, data.value?.valueUnit)}`
}

function errorStatus(cause: unknown): number | undefined {
  if (!cause || typeof cause !== 'object') return undefined
  const value = cause as { status?: unknown; response?: { status?: unknown } }
  const status = value.status ?? value.response?.status
  return typeof status === 'number' ? status : undefined
}

function requestFailureMessage(cause: unknown, fallback: string): string {
  const message = cause instanceof Error ? cause.message : ''
  const status = errorStatus(cause) ?? (/登录已失效/.test(message) ? 401 : undefined)
  configStatus.value = status
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '暂无权限查看排行榜'
  if (status === 500) return '排行榜加载失败，请重试'
  if (/接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(message)) return message
  return fallback
}

function routeQueryValue(value: unknown) {
  return Array.isArray(value) ? value[0] : value
}

function requestedPeriod(): LeaderboardPeriod | undefined {
  const value = routeQueryValue(route.query.period)
  return periodOptions.some(option => option.key === value) ? value as LeaderboardPeriod : undefined
}

function requestedType(enabledTypes: LeaderboardType[]): LeaderboardType | undefined {
  const value = routeQueryValue(route.query.type)
  return enabledTypes.includes(value as LeaderboardType) ? value as LeaderboardType : undefined
}

async function loadConfig() {
  configLoading.value = true
  configError.value = ''
  configStatus.value = undefined
  try {
    const result = await getLeaderboardConfig()
    if (result.enabled && (!result.enabledTypes.length || !result.typeOptions.length)) {
      throw new Error('排行榜配置不完整')
    }
    config.value = result
    if (!result.enabled) return
    period.value = requestedPeriod() || result.defaultPeriod
    type.value = requestedType(result.enabledTypes)
      || (result.enabledTypes.includes(result.defaultType) ? result.defaultType : result.enabledTypes[0])
    await resetLeaderboard()
  } catch (cause) {
    config.value = undefined
    configError.value = requestFailureMessage(cause, '排行榜暂不可用')
  } finally {
    configLoading.value = false
  }
}

async function loadMore() {
  if (!config.value?.enabled || listLoading.value || finished.value) return
  listLoading.value = true
  listError.value = ''
  try {
    const result = await getLeaderboard({
      period: period.value,
      type: type.value,
      pageNo: pageNo.value,
      pageSize: config.value.pageSize
    })
    data.value = result
    rows.value.push(...result.list.filter(item => !rows.value.some(row => row.partnerId === item.partnerId)))
    pageNo.value += 1
    finished.value = rows.value.length >= result.total || result.list.length === 0
  } catch (cause) {
    listError.value = requestFailureMessage(cause, '榜单加载失败')
  } finally {
    listLoading.value = false
  }
}

async function resetLeaderboard() {
  pageNo.value = 1
  rows.value = []
  data.value = undefined
  finished.value = false
  listError.value = ''
  await loadMore()
}

function selectPeriod(value: LeaderboardPeriod) {
  if (period.value === value) return
  period.value = value
  void resetLeaderboard()
}

function selectType(value: LeaderboardType) {
  if (type.value === value) return
  type.value = value
  void resetLeaderboard()
}

function openRules() {
  void showDialog({ title: '榜单规则', message: data.value?.ruleText || '排行榜规则暂未返回。', confirmButtonText: '知道了' })
}

onMounted(loadConfig)
</script>

<template>
  <div class="page-container leaderboard-page" :class="{ 'has-my-rank': data?.myRank }">
    <header class="leaderboard-top">
      <van-nav-bar :title="leaderboardTitle" left-arrow @click-left="$router.back()">
        <template #right>
          <button v-if="data || configError" type="button" class="rule-button" @click="openRules">规则</button>
        </template>
      </van-nav-bar>

      <template v-if="config?.enabled">
        <LiquidSegmentedControl
          class="type-tabs"
          :model-value="type"
          :items="enabledTypeOptions"
          ariaLabel="排行榜指标"
          @change="selectType($event as LeaderboardType)"
        />

        <div class="period-tabs" role="tablist" aria-label="排行榜周期">
          <button
            v-for="option in periodOptions"
            :key="option.key"
            type="button"
            role="tab"
            :class="{ active: period === option.key }"
            :aria-selected="period === option.key"
            @click="selectPeriod(option.key)"
          >{{ option.label }}</button>
        </div>

        <section v-if="data?.top3?.length" class="podium-stage">
          <div class="podium">
            <div
              v-for="item in podiumMembers"
              :key="item.member.partnerId"
              class="podium-item"
              :class="['slot-' + item.slot, { 'is-me': item.member.isMe }]"
            >
              <LeaderboardCrown :rank="item.member.rank" :position="item.slot" />
              <SmartAvatar
                class="podium-avatar"
                :class="['slot-' + item.slot, { pulse: item.slot === 'center' }]"
                :seed="partnerAvatarSeed(item.member.partnerId)"
                :size="item.slot === 'center' ? 72 : 58"
                label=""
              />
              <strong>{{ item.member.displayName }}<small v-if="item.member.isMe">（我）</small></strong>
              <span class="podium-value">{{ formatLeaderboardValue(item.member.value, data?.valueUnit) }}</span>
              <span class="podium-plinth"><b>{{ item.member.rank }}</b></span>
            </div>
          </div>
        </section>
      </template>
    </header>

    <van-skeleton v-if="configLoading" :row="8" style="padding: 20px 16px;" />

    <van-empty v-else-if="configError" :description="configError" image="error">
      <p v-if="configStatus === 403" class="unavailable-tip">暂无权限查看排行榜</p>
      <p v-else-if="configUnavailable" class="unavailable-tip">后端尚未提供排行榜接口，当前功能不可用。</p>
      <van-button size="small" type="primary" @click="loadConfig">重新加载</van-button>
    </van-empty>

    <van-empty v-else-if="config && !config.enabled" description="排行榜已由后台关闭" image="default" />

    <template v-else-if="config?.enabled">
      <section class="ranking-list" :class="{ 'without-podium': !data?.top3?.length }">
        <div class="section-heading">
          <button type="button" class="ranking-rule-link" @click="openRules">
            如何快速上榜？ <van-icon name="question-o" size="14" />
          </button>
          <span>距上一名</span>
        </div>
        <van-list v-model:loading="listLoading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-for="item in rankingRows" :key="item.partnerId" class="rank-row" :class="{ 'is-me': item.isMe }">
            <span class="rank-number" :class="{ top3: item.rank <= 3 }">{{ item.rank }}</span>
            <SmartAvatar class="rank-avatar" :seed="partnerAvatarSeed(item.partnerId)" :size="38" label="" />
            <div class="rank-info">
              <strong>{{ item.displayName }}<small v-if="item.isMe">（我）</small></strong>
              <span class="rank-gap-wrap">
                <span class="rank-gap-text">{{ rankingGapLabel(item) }}</span>
              </span>
            </div>
            <b>{{ formatLeaderboardValue(item.value, data?.valueUnit) }}</b>
          </div>
        </van-list>
        <van-empty v-if="!listLoading && !listError && rows.length === 0" description="本周期暂无上榜数据" :image-size="64">
          <van-button size="small" type="primary" @click="loadMore">去刷新</van-button>
        </van-empty>
        <van-empty v-if="!listLoading && listError" :description="listError" image="error" :image-size="64">
          <van-button size="small" type="primary" @click="loadMore">重试</van-button>
        </van-empty>
      </section>

      <div v-if="data?.myRank" class="my-rank safe-area-bottom">
        <strong>第 {{ data.myRank.rank }} 名</strong>
        <SmartAvatar class="my-rank__avatar" :seed="partnerAvatarSeed(data.myRank.partnerId)" :size="34" label="" />
        <span class="my-rank__name">{{ data.myRank.displayName }}<small>（我）</small></span>
        <b>{{ formatLeaderboardValue(data.myRank.value, data.valueUnit) }}</b>
        <small>{{ chaseText.secondary }}</small>
      </div>
    </template>
  </div>
</template>

<style scoped>
.leaderboard-page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  padding-bottom: 28px;
  background: transparent;
}

.leaderboard-page.has-my-rank {
  padding-bottom: 108px;
}

.leaderboard-top {
  position: relative;
  overflow: hidden;
  padding-bottom: 12px;
  border: 0;
  border-radius: 0 0 20px 20px;
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--h5-primary) 20%, var(--h5-canvas-base)) 0%,
      color-mix(in srgb, var(--h5-primary) 8%, var(--h5-canvas-base)) 55%,
      transparent 100%
    );
  box-shadow: 0 8px 20px rgba(31, 35, 48, 0.05);
  color: var(--h5-text-primary);
}

.leaderboard-top :deep(.van-nav-bar) {
  position: relative;
  border-bottom: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.leaderboard-top :deep(.van-nav-bar::after) {
  border: 0;
}

.leaderboard-top :deep(.van-nav-bar__title),
.leaderboard-top :deep(.van-nav-bar__arrow) {
  color: var(--h5-text-primary);
}

.rule-button {
  min-width: 44px;
  padding: 6px 10px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 8px;
  background: var(--h5-glass-surface-subtle);
  color: var(--h5-primary);
  font-size: 13px;
  font-weight: 600;
}

.unavailable-tip {
  margin: -12px 0 16px;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.type-tabs.liquid-segmented {
  margin: 4px 16px 0;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 12%, transparent);
  background: color-mix(in srgb, var(--h5-primary) 6%, transparent);
}

.type-tabs :deep(.liquid-segmented__indicator) {
  background: var(--h5-glass-surface-strong);
  box-shadow: var(--h5-glass-shadow);
}

.type-tabs :deep(.liquid-segmented__item) {
  padding: 0 4px;
  color: color-mix(in srgb, var(--h5-primary-dark) 45%, var(--h5-text-secondary));
  font-size: 12px;
  font-weight: 600;
}

.type-tabs :deep(.liquid-segmented__item.is-active) {
  color: var(--h5-primary);
  font-weight: 700;
}

.period-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 10px 20px 8px;
}

.period-tabs button {
  position: relative;
  min-width: 0;
  height: 34px;
  padding: 0 4px 7px;
  border: 0;
  background: transparent;
  color: color-mix(in srgb, var(--h5-primary-dark) 45%, var(--h5-text-secondary));
  font: inherit;
  font-size: 13px;
  font-weight: 600;
}

.period-tabs button::after {
  position: absolute;
  bottom: 2px;
  left: 50%;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--h5-primary);
  content: '';
  opacity: 0;
  transform: translateX(-50%);
}

.period-tabs button.active {
  color: var(--h5-primary);
  font-weight: 700;
}

.period-tabs button.active::after {
  opacity: 1;
}

.podium-stage {
}

.podium {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
}

.podium-item {
  display: flex;
  width: calc((100% - 16px) / 3);
  flex-direction: column;
  align-items: center;
  gap: 3px;
  text-align: center;
}

.podium-item.slot-left { order: 1; }
.podium-item.slot-center { order: 2; }
.podium-item.slot-right { order: 3; }

.podium-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--h5-glass-border);
  border-radius: 50%;
  background: var(--h5-glass-surface-subtle);
  color: var(--h5-primary);
  font-size: 18px;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(42, 26, 80, 0.2);
}

.podium-avatar.slot-center {
  width: 72px;
  height: 72px;
  border-color: #ffe75c;
  font-size: 21px;
}

.podium-avatar.slot-left,
.podium-avatar.slot-right {
  width: 58px;
  height: 58px;
}

.podium-item strong {
  max-width: 100%;
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.podium-item strong small {
  color: var(--h5-primary);
}

.podium-value {
  color: var(--h5-text-secondary);
  font-size: 11px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.podium-plinth {
  display: flex;
  width: 100%;
  height: 44px;
  margin-top: 8px;
  align-items: center;
  justify-content: center;
  border-radius: 10px 10px 0 0;
  background: color-mix(in srgb, var(--h5-card-bg) 88%, var(--h5-primary));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.podium-plinth b {
  color: var(--h5-primary-dark);
  font-size: 24px;
  font-weight: 800;
}

.podium-item.slot-center .podium-plinth {
  height: 72px;
  background: var(--h5-gradient);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.4), 0 6px 16px rgba(124, 92, 224, 0.32);
}

.podium-item.slot-center .podium-plinth b {
  color: #fff;
  font-size: 30px;
}

.podium-item.slot-right .podium-plinth {
  height: 36px;
}

.ranking-list {
  position: relative;
  z-index: 2;
  flex: 1 0 auto;
  margin: 0;
  padding: 16px;
  background: transparent;
}

.ranking-list.without-podium {
  margin-top: 0;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 28px;
  margin-bottom: 8px;
}

.ranking-rule-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 0;
  border: 0;
  background: transparent;
  color: var(--h5-text-secondary);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
}

.section-heading > span {
  flex: 0 0 auto;
  color: var(--h5-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.rank-row {
  display: grid;
  min-height: 66px;
  grid-template-columns: 30px 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--h5-divider);
}

.rank-row:last-child {
  border-bottom: 0;
}

.rank-row.is-me {
  margin: 0 -8px;
  padding: 0 8px;
  border-radius: 8px;
  background: var(--h5-primary-opacity);
}

.rank-number {
  width: 30px;
  color: #a1a0c9;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.rank-number.top3 {
  color: var(--h5-primary);
}

.rank-avatar {
  display: flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--h5-primary-light);
  color: var(--h5-primary);
  font-size: 14px;
  font-weight: 700;
}

.rank-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.rank-info strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-info strong small {
  color: var(--h5-primary);
}

.rank-gap-wrap {
  min-width: 0;
}

.rank-gap-text {
  min-width: 0;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-row > b {
  color: var(--h5-text-primary);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.my-rank {
  position: fixed;
  right: 12px;
  bottom: max(12px, env(safe-area-inset-bottom));
  left: 12px;
  z-index: 20;
  display: grid;
  grid-template-columns: auto 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 2px 9px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: var(--h5-gradient);
  color: #fff;
  box-shadow: 0 12px 30px rgba(124, 92, 224, 0.36);
}

.my-rank strong {
  min-width: 56px;
  font-size: 15px;
  color: #fff;
}

.my-rank__avatar {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.65);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  font-size: 13px;
  font-weight: 700;
}

.my-rank__name {
  overflow: hidden;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-rank__name small {
  font-size: 10px;
  opacity: 0.85;
}

.my-rank > b {
  color: #fff;
  font-size: 13px;
  white-space: nowrap;
}

.my-rank > small {
  grid-column: 3 / 5;
  overflow: hidden;
  font-size: 10px;
  opacity: 0.78;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.28); }
  70% { box-shadow: 0 0 0 10px rgba(255, 255, 255, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
}

.podium-avatar.pulse {
  animation: pulse-ring 2.4s ease-out infinite;
}

@media (max-width: 390px) {
  .leaderboard-top { padding-bottom: 16px; }
  .type-tabs { margin-right: 12px; margin-left: 12px; }
  .period-tabs { margin-right: 16px; margin-left: 16px; }
  .podium-stage { margin-right: 8px; margin-left: 8px; }
  .ranking-list { padding: 14px 12px; }
  .podium-avatar.slot-center { width: 66px; height: 66px; }
  .podium-avatar.slot-left,
  .podium-avatar.slot-right { width: 54px; height: 54px; }
}

@media (max-width: 360px) {
  .leaderboard-page.has-my-rank { padding-bottom: 104px; }
  .podium { gap: 6px; }
  .podium-item { width: calc((100% - 12px) / 3); }
  .podium-item strong { font-size: 11px; }
  .podium-plinth b { font-size: 22px; }
  .rank-row { grid-template-columns: 26px 34px minmax(0, 1fr) auto; gap: 8px; }
  .rank-number { width: 26px; font-size: 16px; }
  .rank-avatar { width: 34px; height: 34px; }
  .rank-gap-text { font-size: 10px; }
  .rank-row > b { font-size: 14px; }
  .my-rank { grid-template-columns: auto 32px minmax(0, 1fr) auto; gap: 2px 7px; }
  .my-rank strong { min-width: 50px; font-size: 14px; }
  .my-rank__avatar { width: 32px; height: 32px; }
}

@media (prefers-reduced-motion: reduce) {
  .podium-avatar.pulse { animation: none; }
}

</style>
