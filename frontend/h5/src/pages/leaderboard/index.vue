<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
  computeMaxStepGap,
  formatLeaderboardChase,
  formatLeaderboardValue,
  leaderboardMemberInitial,
  leaderboardRowGapPercent,
  leaderboardRowGapText
} from '@/utils/leaderboard'

defineOptions({ name: 'Leaderboard' })

type PodiumSlot = 'left' | 'center' | 'right'

const periodOptions: Array<{ key: LeaderboardPeriod; label: string }> = [
  { key: 'today', label: '今日' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
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
const maxGap = computed(() => computeMaxStepGap(rows.value.length ? rows.value : (data.value?.list || [])))

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
    period.value = result.defaultPeriod
    type.value = result.enabledTypes.includes(result.defaultType) ? result.defaultType : result.enabledTypes[0]
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
    <van-nav-bar title="排行榜" left-arrow @click-left="$router.back()">
      <template #right>
        <button v-if="data || configError" type="button" class="rule-button" @click="openRules">规则</button>
      </template>
    </van-nav-bar>

    <van-skeleton v-if="configLoading" :row="8" style="padding: 20px 16px;" />

    <van-empty v-else-if="configError" :description="configError" image="error">
      <p v-if="configStatus === 403" class="unavailable-tip">暂无权限查看排行榜</p>
      <p v-else-if="configUnavailable" class="unavailable-tip">后端尚未提供排行榜接口，当前功能不可用。</p>
      <van-button size="small" type="primary" @click="loadConfig">重新加载</van-button>
    </van-empty>

    <van-empty v-else-if="config && !config.enabled" description="排行榜已由后台关闭" image="default" />

    <template v-else-if="config?.enabled">
      <section class="leader-hero" :class="`tone-${chaseText.tone}`">
        <div class="leader-hero__bg-deco" />
        <div class="leader-hero__icon"><van-icon name="trophy-o" size="28" /></div>
        <div class="leader-hero__content">
          <small>{{ data?.periodLabel || '榜单' }}{{ data?.typeLabel || '' }} · {{ data?.total || 0 }} 人</small>
          <strong>{{ chaseText.primary }}</strong>
          <span>{{ chaseText.secondary }}</span>
        </div>
        <div v-if="data?.myRank" class="leader-hero__value">{{ formatLeaderboardValue(data.myRank.value, data.valueUnit) }}</div>
      </section>

      <div class="period-tabs" role="tablist" aria-label="排行榜周期">
        <button
          v-for="option in periodOptions"
          :key="option.key"
          type="button"
          :class="{ active: period === option.key }"
          @click="selectPeriod(option.key)"
        >{{ option.label }}</button>
      </div>

      <div class="type-tabs" role="tablist" aria-label="排行榜指标">
        <button
          v-for="option in enabledTypeOptions"
          :key="option.key"
          type="button"
          :class="{ active: type === option.key }"
          @click="selectType(option.key)"
        >{{ option.label }}</button>
      </div>

      <section v-if="data?.top3?.length" class="podium-stage">
        <div class="podium-metric">{{ data.valueLabel || data.typeLabel }}</div>
        <div class="podium">
          <div
            v-for="item in podiumMembers"
            :key="item.member.partnerId"
            class="podium-item"
            :class="[`slot-${item.slot}`, `rank-${item.member.rank}`, { 'is-me': item.member.isMe }]"
          >
            <span class="podium-avatar" :class="[`slot-${item.slot}`, { pulse: item.slot === 'center' }]">{{ leaderboardMemberInitial(item.member.displayName) }}</span>
            <span class="podium-rank">第 {{ item.member.rank }} 名</span>
            <strong>{{ item.member.displayName }}<small v-if="item.member.isMe">（我）</small></strong>
            <span class="podium-value">{{ formatLeaderboardValue(item.member.value, data?.valueUnit) }}</span>
          </div>
        </div>
      </section>

      <section class="card ranking-list">
        <div class="section-heading">
          <strong>完整榜单</strong>
          <span>共 {{ data?.total || 0 }} 人</span>
        </div>
        <van-list v-model:loading="listLoading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-for="(item, index) in rows" :key="item.partnerId" class="rank-row" :class="{ 'is-me': item.isMe }">
            <span class="rank-number" :class="{ top3: item.rank <= 3 }">{{ item.rank }}</span>
            <span class="rank-avatar">{{ leaderboardMemberInitial(item.displayName) }}</span>
            <div class="rank-info">
              <strong>{{ item.displayName }}<small v-if="item.isMe">（我）</small></strong>
              <span class="rank-gap-wrap">
                <span class="rank-gap-bar" :class="{ tie: item.gapToPrevious === 0 }">
                  <span
                    class="rank-gap-bar__fill"
                    :style="{ width: `${leaderboardRowGapPercent(item, rows[index - 1], maxGap)}%` }"
                  />
                </span>
                <span class="rank-gap-text">{{ leaderboardRowGapText(item, rows[index - 1], data?.valueUnit) }}</span>
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
        <strong>我 · 第 {{ data.myRank.rank }} 名</strong>
        <span>{{ formatLeaderboardValue(data.myRank.value, data.valueUnit) }}</span>
        <small>{{ chaseText.secondary }}</small>
      </div>
    </template>
  </div>
</template>

<style scoped>
.leaderboard-page {
  min-height: 100vh;
  padding-bottom: 24px;
  background: linear-gradient(180deg, #fff7f8 0%, #f7f8fa 24%, #f7f8fa 100%);
}

.leaderboard-page.has-my-rank {
  padding-bottom: 118px;
}

.rule-button {
  padding: 6px 10px;
  border: 0;
  border-radius: 999px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 13px;
  font-weight: 600;
}

.unavailable-tip {
  margin: -12px 0 16px;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.leader-hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 14px 16px 12px;
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f04455 0%, #ff7180 100%);
  color: #fff;
  overflow: hidden;
  box-shadow: 0 12px 30px rgba(240, 68, 85, 0.18);
}

.leader-hero.tone-champion {
  background: linear-gradient(135deg, #e63b53 0%, #ff7f8e 100%);
}

.leader-hero.tone-tie {
  background: linear-gradient(135deg, #f1707c 0%, #ff8f9c 100%);
}

.leader-hero.tone-unranked {
  background: linear-gradient(135deg, #f6b9c0 0%, #f8d8dc 100%);
  color: #6f3440;
  box-shadow: 0 10px 24px rgba(240, 68, 85, 0.1);
}

.leader-hero__bg-deco {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.22), transparent 34%),
    radial-gradient(circle at 10% 88%, rgba(255, 255, 255, 0.12), transparent 38%);
}

.leader-hero__icon {
  position: relative;
  z-index: 1;
  display: flex;
  width: 54px;
  height: 54px;
  flex: 0 0 54px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
}

.leader-hero__content {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.leader-hero__content small {
  font-size: 11px;
  opacity: 0.88;
}

.leader-hero__content strong {
  font-size: 19px;
  font-weight: 800;
  line-height: 1.3;
}

.leader-hero__content span {
  font-size: 13px;
  opacity: 0.9;
}

.leader-hero__value {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.period-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  margin: 0 16px;
  padding: 4px;
  border-radius: 999px;
  background: rgba(240, 68, 85, 0.08);
}

.period-tabs button,
.type-tabs button {
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 12px;
}

.period-tabs button {
  height: 34px;
  border-radius: 999px;
  color: var(--h5-text-secondary);
  font-weight: 600;
}

.period-tabs button.active {
  background: #fff;
  color: var(--h5-primary);
  box-shadow: 0 6px 14px rgba(240, 68, 85, 0.12);
}

.type-tabs {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding: 0 16px;
  overflow-x: auto;
  scrollbar-width: none;
}

.type-tabs button {
  flex: 0 0 auto;
  padding: 8px 12px;
  border: 1px solid var(--h5-border);
  border-radius: 999px;
  background: var(--h5-card-bg);
  color: var(--h5-text-secondary);
}

.type-tabs button.active {
  border-color: var(--h5-primary);
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-weight: 600;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-heading strong {
  font-size: 15px;
}

.section-heading span {
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.podium-stage {
  margin: 22px 16px 8px;
  padding: 0 6px 6px;
}

.podium-metric {
  width: fit-content;
  min-width: 84px;
  margin: 0 auto 24px;
  padding: 10px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #f04455 0%, #db3a4c 100%);
  box-shadow: 0 10px 22px rgba(240, 68, 85, 0.22);
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  text-align: center;
}

.podium {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 18px;
  padding: 0 0 8px;
}

.podium-item {
  display: flex;
  width: 92px;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  text-align: center;
}

.podium-item.slot-left { order: 1; }
.podium-item.slot-center { order: 2; }
.podium-item.slot-right { order: 3; }

.podium-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 800;
}

.podium-avatar.slot-center {
  position: relative;
  z-index: 1;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f04455 0%, #ff8b97 100%);
  box-shadow: 0 10px 20px rgba(240, 68, 85, 0.16);
}

.podium-avatar.slot-center::before {
  position: absolute;
  z-index: -1;
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: rgba(240, 68, 85, 0.12);
  content: '';
}

.podium-avatar.slot-left,
.podium-avatar.slot-right {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f296a1 0%, #ec6b7a 100%);
  box-shadow: 0 8px 16px rgba(240, 68, 85, 0.12);
}

.podium-avatar.slot-right {
  background: linear-gradient(135deg, #f7a0aa 0%, #f48d99 100%);
}

.podium-avatar.pulse {
  animation: pulse-ring 2.4s ease-out infinite;
}

.podium-rank {
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.podium-item strong {
  max-width: 92px;
  overflow: hidden;
  color: #1a1a1a;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.podium-item strong small {
  color: var(--h5-primary);
}

.podium-value {
  color: var(--h5-primary);
  font-size: 13px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.podium-item.is-me strong {
  color: var(--h5-primary);
}

.ranking-list {
  margin-top: 12px;
}

.rank-row {
  display: flex;
  min-height: 56px;
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
  border-radius: 10px;
  background: var(--h5-primary-opacity);
}

.rank-number {
  width: 28px;
  text-align: center;
  color: var(--h5-text-secondary);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.rank-number.top3 {
  color: var(--h5-primary);
}

.rank-avatar {
  display: flex;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #ffe7e9;
  color: var(--h5-primary);
  font-size: 14px;
  font-weight: 700;
}

.rank-info {
  display: flex;
  min-width: 0;
  flex: 1;
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
  display: flex;
  align-items: center;
  gap: 8px;
}

.rank-gap-bar {
  flex: 1;
  max-width: 112px;
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: #f4d9dd;
}

.rank-gap-bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #f04455 0%, #ff8591 100%);
  transition: width 0.35s ease;
}

.rank-gap-bar.tie .rank-gap-bar__fill {
  background: linear-gradient(90deg, #5cbf8c 0%, #6ed49d 100%);
}

.rank-gap-text {
  color: var(--h5-text-secondary);
  font-size: 11px;
  white-space: nowrap;
}

.rank-row b {
  flex: 0 0 auto;
  color: var(--h5-primary);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.my-rank {
  position: fixed;
  right: 16px;
  bottom: calc(var(--tab-height) + 12px);
  left: 16px;
  z-index: 20;
  display: grid;
  grid-template-columns: auto auto 1fr;
  gap: 4px 12px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f2d4d9;
  box-shadow: 0 10px 24px rgba(31, 35, 48, 0.08);
}

.my-rank strong,
.my-rank span {
  font-size: 13px;
  font-weight: 700;
}

.my-rank strong {
  color: var(--h5-primary);
}

.my-rank span {
  color: #1c2027;
  font-variant-numeric: tabular-nums;
}

.my-rank small {
  grid-column: 1 / -1;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(240, 68, 85, 0.24); }
  70% { box-shadow: 0 0 0 12px rgba(240, 68, 85, 0); }
  100% { box-shadow: 0 0 0 0 rgba(240, 68, 85, 0); }
}

@media (max-width: 430px) {
  .leaderboard-page { padding-bottom: 20px; }
  .leaderboard-page.has-my-rank { padding-bottom: 112px; }
  .leader-hero { margin-right: 12px; margin-left: 12px; padding: 14px; }
  .period-tabs { margin-right: 12px; margin-left: 12px; }
  .type-tabs { padding-right: 12px; padding-left: 12px; }
  .podium-stage,
  .ranking-list { margin-right: 12px; margin-left: 12px; }
  .my-rank { right: 12px; left: 12px; }
  .rank-gap-bar { max-width: 92px; }
}

@media (max-width: 360px) {
  .leaderboard-page { padding-bottom: 16px; }
  .leader-hero__content strong { font-size: 17px; }
  .leader-hero__value { font-size: 14px; }
  .podium { gap: 10px; }
  .podium-item { width: 84px; }
  .rank-info strong { font-size: 13px; }
  .rank-gap-text { font-size: 10px; }
  .my-rank { grid-template-columns: 1fr auto; }
  .my-rank small { grid-column: 1 / -1; }
}
</style>
