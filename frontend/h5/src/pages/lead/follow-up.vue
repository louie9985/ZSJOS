<script setup lang="ts">
import ProductSpecs from '../../components/ProductSpecs.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getLeadFollowUpSummary, getMyLeadPage, type LeadFollowUpSummary, type LeadListItem } from '@/api/lead'
import { usePageList } from '@/composables/usePageList'
import { formatDate, formatLeadNo, formatLeadStatus } from '@/utils/format'
import SmartAvatar from '@/components/SmartAvatar.vue'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'
import { leadAvatarSeed } from '@/config/avatar'

defineOptions({ name: 'LeadFollowUp' })

type FollowUpView = 'follow_up_pending' | 'unreachable' | 'invalid'

const router = useRouter()
const route = useRoute()

function normalizeView(value: unknown): FollowUpView {
  return value === 'unreachable' || value === 'invalid' || value === 'follow_up_pending'
    ? value
    : 'follow_up_pending'
}

const activeView = ref<FollowUpView>(normalizeView(route.query.view))
const summary = ref<LeadFollowUpSummary>()
const summaryError = ref('')
const summaryLoading = ref(true)

const tabs = computed(() => [
  { key: 'follow_up_pending' as const, label: '待跟进', count: summary.value?.followUpPendingCount || 0 },
  { key: 'unreachable' as const, label: '未联系上', count: summary.value?.unreachableCount || 0 },
  { key: 'invalid' as const, label: '已判无效', count: summary.value?.invalidCount || 0 }
])
const activeTab = computed(() => tabs.value.find(tab => tab.key === activeView.value) || tabs.value[0])
const segmentItems = computed(() => tabs.value.map(tab => ({
  key: tab.key,
  label: summaryLoading.value ? tab.label : `${tab.label} · ${tab.count}`
})))
const params = computed(() => ({ view: activeView.value }))
const { list, total, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  request => getMyLeadPage(request), params, { immediate: false }
)

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await getLeadFollowUpSummary()
  } catch (cause) {
    summary.value = undefined
    summaryError.value = cause instanceof Error ? cause.message : '提醒数量加载失败'
  } finally {
    summaryLoading.value = false
  }
}

function selectView(view: FollowUpView) {
  if (activeView.value !== view) activeView.value = view
  if (route.query.view !== view) {
    void router.replace({ query: { ...route.query, view } })
  }
}
function goDetail(id: number) { router.push(`/lead/${id}`) }
function primaryCourse(item: LeadListItem) {
  return item.primaryProduct?.spuName
    || item.intendedProducts?.find(product => product.primary)?.spuName
    || item.intendedProducts?.[0]?.spuName
    || '未填写课程'
}
function stageText(item: LeadListItem) {
  if (activeView.value === 'unreachable') return '最近一次未联系上'
  if (activeView.value === 'invalid') return item.invalidReasonLabelSnapshot || '已判无效'
  if (item.handlingStage === 'first_follow_pending') return '待首次跟进'
  if (item.handlingStage === 'qualification_pending') return '待有效性判定'
  if (item.handlingStage === 'following') return '持续跟进中'
  return formatLeadStatus(item.status)
}
function cardTime(item: LeadListItem) { return item.lastActivityAt || item.qualifiedAt || item.submittedAt }
function statusClass(status: string) {
  return status === 'invalid' ? 'danger'
    : ['valid', 'won'].includes(status) ? 'success'
      : status === 'suspended' ? 'warning' : 'primary'
}

watch(() => route.query.view, value => {
  const view = normalizeView(value)
  if (activeView.value !== view) activeView.value = view
  if (value !== view) {
    void router.replace({ query: { ...route.query, view } })
  }
}, { immediate: true })
watch(activeView, () => { void refresh() }, { immediate: true })
void loadSummary()
</script>

<template>
  <div class="page-container lead-follow-up-page">
    <van-nav-bar title="客资跟进提醒" left-arrow @click-left="router.back()" />

    <LiquidSegmentedControl
      class="follow-up-tabs"
      :model-value="activeView"
      :items="segmentItems"
      ariaLabel="客资跟进分类"
      compact
      @change="key => selectView(normalizeView(key))"
    />

    <div v-if="summaryError" class="follow-up-summary-error">
      <span>{{ summaryError }}</span><button type="button" @click="loadSummary">重试</button>
    </div>

    <div class="follow-up-list-head">
      <div>
        <h1>{{ activeTab.label }}</h1>
        <p v-if="activeView === 'follow_up_pending'">待首跟、待判定和持续跟进中的客资</p>
        <p v-else-if="activeView === 'unreachable'">最近一次销售跟进仍未联系上的客资</p>
        <p v-else>销售已完成无效判定的客资</p>
      </div>
      <span>{{ total }} 条</span>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-if="loading && list.length === 0" class="follow-up-list">
          <div v-for="index in 3" :key="index" class="card follow-up-card follow-up-card--skeleton"><van-skeleton title :row="3" /></div>
        </div>
        <div v-else-if="!loading && error" class="card follow-up-state">
          <van-empty image="error" description="客资加载失败" :image-size="72"><van-button type="primary" round size="small" @click="refresh">重新加载</van-button></van-empty>
        </div>
        <div v-else-if="!loading && list.length === 0" class="card follow-up-state">
          <van-empty :description="`暂无${activeTab.label}客资`" :image-size="82" />
        </div>
        <div v-else class="follow-up-list">
          <button v-for="item in list" :key="item.id" type="button" class="card follow-up-card" @click="goDetail(item.id)">
            <SmartAvatar class="follow-up-card__avatar" :seed="leadAvatarSeed(item.id)" :size="40" shape="rounded" label="" />
            <span class="follow-up-card__main">
              <span class="follow-up-card__identity"><strong>{{ item.submittedName || '未命名客户' }}</strong><small>{{ formatLeadNo(item.leadNo) }}</small></span>
              <span class="follow-up-card__course">{{ primaryCourse(item) }}</span>
              <ProductSpecs :product="item.primaryProduct || item.intendedProducts?.find(p => p.primary) || item.intendedProducts?.[0] || {}" />
              <span class="follow-up-card__meta"><b>{{ stageText(item) }}</b><time>{{ formatDate(cardTime(item)) }}</time></span>
            </span>
            <span class="follow-up-card__status" :class="`follow-up-card__status--${statusClass(item.status)}`">{{ formatLeadStatus(item.status) }}</span>
            <van-icon class="follow-up-card__arrow" name="arrow" size="16" />
          </button>
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.lead-follow-up-page { min-height: 100vh; padding-bottom: 28px; background: transparent; }
.follow-up-tabs { margin: 12px 16px 0; }
.follow-up-summary-error { display: flex; align-items: center; justify-content: space-between; margin: 8px 16px 0; color: var(--h5-danger); font-size: 11px; }
.follow-up-summary-error button { border: 0; background: transparent; color: var(--h5-primary); }
.follow-up-list-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding: 18px 18px 10px; }
.follow-up-list-head h1 { margin: 0; color: var(--h5-text-primary); font-size: 18px; line-height: 1.4; }
.follow-up-list-head p { margin-top: 3px; color: var(--h5-text-secondary); font-size: 11px; line-height: 1.45; }
.follow-up-list-head > span { flex: 0 0 auto; color: var(--h5-text-placeholder); font-size: 11px; }
.follow-up-list { display: flex; flex-direction: column; gap: 12px; padding: 0 16px; }
.follow-up-card { display: grid; width: 100%; min-height: 104px; margin: 0; grid-template-columns: 40px minmax(0, 1fr) auto 16px; align-items: start; gap: 10px; padding: 14px; border: 1px solid var(--h5-glass-border); border-radius: 16px; background: var(--h5-content-surface); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.68), 0 6px 20px rgba(31, 35, 48, 0.07); color: var(--h5-text-primary); text-align: left; }
button.follow-up-card:active { transform: scale(0.99); }
.follow-up-card--skeleton { display: block; }
.follow-up-card__avatar { display: flex; width: 40px; height: 40px; align-items: center; justify-content: center; border-radius: 13px; background: var(--h5-primary-opacity); color: var(--h5-primary); font-size: 15px; font-weight: 700; }
.follow-up-card__main { display: flex; min-width: 0; flex-direction: column; gap: 7px; }
.follow-up-card__identity { display: flex; min-width: 0; flex-direction: column; gap: 1px; }
.follow-up-card__identity strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.follow-up-card__identity small { overflow: hidden; color: var(--h5-text-placeholder); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.follow-up-card__course { overflow: hidden; color: var(--h5-text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.follow-up-card__meta { display: flex; min-width: 0; align-items: center; gap: 8px; font-size: 10px; }
.follow-up-card__meta b { overflow: hidden; color: var(--h5-primary); font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.follow-up-card__meta time { flex: 0 0 auto; color: var(--h5-text-placeholder); }
.follow-up-card__status { max-width: 74px; overflow: hidden; padding: 3px 8px; border-radius: 999px; font-size: 10px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.follow-up-card__status--primary { background: var(--h5-primary-opacity); color: var(--h5-primary); }
.follow-up-card__status--success { background: rgba(82, 196, 26, 0.1); color: var(--h5-success); }
.follow-up-card__status--danger { background: rgba(255, 77, 79, 0.1); color: var(--h5-danger); }
.follow-up-card__status--warning { background: rgba(250, 173, 20, 0.12); color: #c77d00; }
.follow-up-card__arrow { align-self: center; color: var(--h5-text-placeholder); }
.follow-up-state { margin: 0 16px; padding: 14px; border-radius: 16px; }
@media (max-width: 360px) { .follow-up-tabs { margin-right: 12px; margin-left: 12px; } .follow-up-list { padding-right: 12px; padding-left: 12px; } }
</style>
