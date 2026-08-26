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
import { wasMockedEndpoint } from '@/api/mock'

defineOptions({ name: 'Leaderboard' })

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

const usingMock = computed(() => wasMockedEndpoint('/zsjos/partner/leaderboard'))
const configUnavailable = computed(() => /接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(configError.value))
const enabledTypeOptions = computed(() => {
  if (!config.value) return []
  return config.value.typeOptions.filter(option => config.value!.enabledTypes.includes(option.key))
})
const chaseText = computed(() => {
  const mine = data.value?.myRank
  if (!mine) return { primary: '暂未上榜', secondary: '提交有效客资后可参与榜单' }
  if (mine.rank === 1) return { primary: '当前第 1 名', secondary: '继续保持领先' }
  if (data.value?.previousGap?.targetReached) return { primary: `并列第 ${mine.rank} 名`, secondary: '继续冲击更高名次' }
  return {
    primary: `当前第 ${mine.rank} 名`,
    secondary: data.value?.previousGap?.displayValue
      ? `距上一名 ${data.value.previousGap.displayValue}`
      : '继续冲击更高名次'
  }
})

function formatValue(value: number | undefined, unit = data.value?.valueUnit) {
  if (value == null) return '--'
  return unit === 'count' ? `${Math.round(value)} 条` : `¥${value.toFixed(2)}`
}

function gapText(item: LeaderboardMember) {
  if (item.rank === 1) return '榜首'
  if (item.gapToPrevious === 0) return '与上一名并列'
  return item.gapToPrevious == null ? '' : `距上一名 ${formatValue(item.gapToPrevious)}`
}

async function loadConfig() {
  configLoading.value = true
  configError.value = ''
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
    configError.value = cause instanceof Error ? cause.message : '排行榜暂不可用'
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
    listError.value = cause instanceof Error ? cause.message : '榜单加载失败'
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
      <template #right><button v-if="data" type="button" class="rule-button" @click="openRules">规则</button></template>
    </van-nav-bar>
    <van-notice-bar v-if="usingMock" color="#8a6100" background="#fff7df" left-icon="info-o">
      当前榜单为开发环境演示数据
    </van-notice-bar>

    <van-skeleton v-if="configLoading" :row="8" style="padding: 20px 16px;" />
    <van-empty v-else-if="configError" :description="configError" image="error">
      <p v-if="configUnavailable" class="unavailable-tip">后端尚未提供排行榜接口，当前功能不可用。</p>
      <van-button size="small" type="primary" @click="loadConfig">重新加载</van-button>
    </van-empty>
    <van-empty v-else-if="config && !config.enabled" description="排行榜已由后台关闭" image="default" />

    <template v-else-if="config?.enabled">
      <section class="leader-hero">
        <div class="leader-hero__icon"><van-icon name="medal-o" size="28" /></div>
        <div class="leader-hero__content">
          <small>{{ data?.periodLabel || '榜单' }}{{ data?.typeLabel || '' }} · {{ data?.total || 0 }} 人</small>
          <strong>{{ chaseText.primary }}</strong>
          <span>{{ chaseText.secondary }}</span>
        </div>
        <div v-if="data?.myRank" class="leader-hero__value">{{ formatValue(data.myRank.value) }}</div>
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

      <section v-if="data?.top3.length" class="card top-list">
        <div class="section-heading"><strong>领先伙伴</strong><span>{{ data.periodLabel }}</span></div>
        <div v-for="item in data.top3" :key="item.partnerId" class="top-row" :class="{ 'is-me': item.isMe }">
          <span class="rank-mark" :class="`rank-${item.rank}`">{{ item.rank }}</span>
          <span class="partner-name">{{ item.displayName }}<small v-if="item.isMe">（我）</small></span>
          <strong>{{ formatValue(item.value) }}</strong>
        </div>
      </section>

      <section class="card ranking-list">
        <div class="section-heading"><strong>完整榜单</strong><span>共 {{ data?.total || 0 }} 人</span></div>
        <van-list v-model:loading="listLoading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-for="item in rows" :key="item.partnerId" class="rank-row" :class="{ 'is-me': item.isMe }">
            <span class="rank-number">{{ item.rank }}</span>
            <div class="rank-info">
              <strong>{{ item.displayName }}<small v-if="item.isMe">（我）</small></strong>
              <span>{{ gapText(item) }}</span>
            </div>
            <b>{{ formatValue(item.value) }}</b>
          </div>
        </van-list>
        <van-empty v-if="!listLoading && !listError && rows.length === 0" description="本周期暂无上榜数据" :image-size="64" />
        <van-empty v-if="!listLoading && listError" :description="listError" image="error" :image-size="64">
          <van-button size="small" type="primary" @click="loadMore">重试</van-button>
        </van-empty>
      </section>

      <div v-if="data?.myRank" class="my-rank safe-area-bottom">
        <strong>我 · 第 {{ data.myRank.rank }} 名</strong>
        <span>{{ formatValue(data.myRank.value) }}</span>
        <small>{{ chaseText.secondary }}</small>
      </div>
    </template>
  </div>
</template>

<style scoped>
.leaderboard-page{padding-bottom:24px}.leaderboard-page.has-my-rank{padding-bottom:104px}.rule-button{padding:6px;border:0;background:transparent;color:var(--h5-primary);font-size:13px}.unavailable-tip{margin:-12px 0 16px;color:var(--h5-text-secondary);font-size:12px}.leader-hero{display:flex;align-items:center;gap:12px;margin:14px 16px;padding:16px;border-radius:8px;background:var(--h5-gradient);color:#fff}.leader-hero__icon{display:flex;flex:0 0 48px;width:48px;height:48px;align-items:center;justify-content:center;border-radius:50%;background:rgba(255,255,255,.18)}.leader-hero__content{display:flex;min-width:0;flex:1;flex-direction:column;gap:4px}.leader-hero__content small,.leader-hero__content span{font-size:11px;opacity:.86}.leader-hero__content strong{font-size:18px;letter-spacing:0}.leader-hero__value{flex:0 0 auto;font-size:15px;font-weight:600;font-variant-numeric:tabular-nums}.period-tabs{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:0 16px;padding:3px;border-radius:8px;background:var(--h5-card-bg)}.period-tabs button,.type-tabs button{min-width:0;border:0;color:var(--h5-text-secondary);background:transparent;font-size:12px}.period-tabs button{height:34px;border-radius:6px}.period-tabs button.active{background:var(--h5-primary);color:#fff}.type-tabs{display:flex;gap:8px;padding:12px 16px 0;overflow-x:auto;scrollbar-width:none}.type-tabs button{flex:0 0 auto;padding:7px 12px;border:1px solid var(--h5-border);border-radius:6px;background:var(--h5-card-bg)}.type-tabs button.active{border-color:var(--h5-primary);background:var(--h5-primary-opacity);color:var(--h5-primary)}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.section-heading strong{font-size:15px}.section-heading span{color:var(--h5-text-secondary);font-size:11px}.top-row,.rank-row{display:flex;min-height:52px;align-items:center;gap:10px;border-bottom:1px solid var(--h5-divider)}.top-row:last-child,.rank-row:last-child{border-bottom:0}.top-row.is-me,.rank-row.is-me{margin:0 -8px;padding:0 8px;border-radius:6px;background:var(--h5-primary-opacity)}.rank-mark{display:flex;width:28px;height:28px;align-items:center;justify-content:center;border-radius:50%;background:var(--h5-bg);font-size:13px;font-weight:700}.rank-mark.rank-1{background:#f6c453;color:#6f4b00}.rank-mark.rank-2{background:#d9dde5;color:#4d5667}.rank-mark.rank-3{background:#d79a68;color:#623313}.partner-name{min-width:0;flex:1;overflow:hidden;font-size:14px;text-overflow:ellipsis;white-space:nowrap}.partner-name small,.rank-info small{color:var(--h5-primary)}.top-row>strong,.rank-row>b{flex:0 0 auto;font-size:14px;font-variant-numeric:tabular-nums}.rank-number{width:28px;text-align:center;color:var(--h5-text-secondary);font-size:13px;font-weight:600}.rank-info{display:flex;min-width:0;flex:1;flex-direction:column;gap:4px}.rank-info strong{overflow:hidden;font-size:14px;text-overflow:ellipsis;white-space:nowrap}.rank-info span{color:var(--h5-text-placeholder);font-size:10px}.my-rank{position:fixed;right:0;bottom:0;left:0;z-index:20;display:grid;grid-template-columns:1fr auto;gap:4px 12px;padding:12px 16px;background:var(--h5-card-bg);box-shadow:0 -2px 10px rgba(0,0,0,.08)}.my-rank strong,.my-rank span{font-size:14px}.my-rank span{color:var(--h5-primary);font-weight:600}.my-rank small{grid-column:1/-1;color:var(--h5-text-secondary);font-size:11px}
</style>
