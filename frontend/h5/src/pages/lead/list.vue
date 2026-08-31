<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { usePageList } from '@/composables/usePageList'
import { getDictByType, getLeadCatalog, getMyLeadPage, getPartnerLeadFilterOptions, type LeadFilterOption, type LeadListItem } from '@/api/lead'
import type { DictItem } from '@/stores/app'
import { formatDate, formatLeadNo, formatLeadStatus } from '@/utils/format'

defineOptions({ name: 'LeadList' })

const router = useRouter()
const activeTab = ref('all')
const keywordInput = ref('')
const keyword = ref('')
const showFilters = ref(false)
const optionLoading = ref(false)
const optionError = ref('')
const sourceChannels = ref<DictItem[]>([])
const leadCategories = ref<DictItem[]>([])
const productOptions = ref<Array<{ label: string; value: string }>>([])
const appealOptions = ref<LeadFilterOption[]>([])
const orderOptions = ref<LeadFilterOption[]>([])
const advancedOptionError = ref('')
const advancedFiltersAvailable = ref(false)
const filters = reactive({ simpleStatus: '', assignmentStatus: '', sourceChannel: '', leadCategory: '', startDate: '', endDate: '', mainProductRef: '', appealStatus: '', orderReviewStatus: '' })

const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'submitted', label: '待判定' },
  { key: 'valid', label: '有效' },
  { key: 'invalid', label: '无效' },
  { key: 'won', label: '已成交' },
  { key: 'closed', label: '已关闭' }
]
const assignmentOptions = [
  { value: '', label: '全部' }, { value: 'unassigned', label: '未分配' },
  { value: 'pending_acceptance', label: '待接单' }, { value: 'owned', label: '已归属' },
  { value: 'public_pool', label: '公海' }, { value: 'closed', label: '已结束' }
]
const stageOptions = [
  { value: '', label: '全部' }, { value: 'first_follow_pending', label: '待首次跟进' },
  { value: 'qualification_pending', label: '资格判定中' }, { value: 'following', label: '跟进中' },
  { value: 'deal_pending_approval', label: '成交待审核' }, { value: 'won', label: '已成交' },
  { value: 'invalid', label: '无效' }, { value: 'closed', label: '已关闭' },
  { value: 'suspended', label: '已挂起' }
]

const advancedCount = computed(() => Object.values(filters).filter(Boolean).length)
const filterParams = computed(() => ({
  ...(activeTab.value !== 'all' ? { status: activeTab.value } : {}),
  ...(filters.simpleStatus ? { simpleStatus: filters.simpleStatus } : {}),
  ...(keyword.value ? { keyword: keyword.value } : {}),
  ...(filters.assignmentStatus ? { assignmentStatus: filters.assignmentStatus } : {}),
  ...(filters.sourceChannel ? { sourceChannel: filters.sourceChannel } : {}),
  ...(filters.leadCategory ? { leadCategory: filters.leadCategory } : {}),
  ...(filters.startDate && filters.endDate ? { submittedAt: [`${filters.startDate} 00:00:00`, `${filters.endDate} 23:59:59`] as [string, string] } : {}),
  ...(filters.mainProductRef ? { mainProductRef: filters.mainProductRef } : {}),
  ...(filters.appealStatus ? { appealStatus: filters.appealStatus } : {}),
  ...(filters.orderReviewStatus ? { orderReviewStatus: filters.orderReviewStatus } : {})
}))

const { list, total, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getMyLeadPage(params as Parameters<typeof getMyLeadPage>[0]), filterParams
)

const activeFilterSummary = computed(() => {
  const values = [
    optionText(stageOptions, filters.simpleStatus),
    optionText(assignmentOptions, filters.assignmentStatus),
    optionText(sourceChannels.value, filters.sourceChannel),
    optionText(leadCategories.value, filters.leadCategory),
    optionText(productOptions.value, filters.mainProductRef),
    optionText(appealOptions.value, filters.appealStatus),
    optionText(orderOptions.value, filters.orderReviewStatus),
    filters.startDate && filters.endDate ? `${filters.startDate.slice(5)} 至 ${filters.endDate.slice(5)}` : ''
  ].filter(Boolean)
  return values.join(' · ')
})
const activeTabLabel = computed(() => statusTabs.find(item => item.key === activeTab.value)?.label || '全部')
const hasActiveQuery = computed(() => activeTab.value !== 'all' || !!keyword.value || advancedCount.value > 0)
const listErrorText = computed(() => {
  if (/401|登录已失效/i.test(error.value)) return '登录已失效，请重新登录'
  if (/403|没有操作权限|暂无权限/i.test(error.value)) return '暂无权限查看客资'
  if (/500|status code 500/i.test(error.value)) return '客资加载失败，请重试'
  return error.value
})

function optionText(options: Array<{ label: string; value: string }>, value: string) {
  if (!value) return ''
  return options.find(item => item.value === value)?.label || value
}

async function loadOptions() {
  if (optionLoading.value) return
  optionLoading.value = true
  optionError.value = ''
  try {
    if (!sourceChannels.value.length) {
      const [sources, categories, catalog] = await Promise.all([
        getDictByType('zsjos_lead_source_channel'), getDictByType('zsjos_lead_category'), getLeadCatalog()
      ])
      sourceChannels.value = sources
      leadCategories.value = categories
      productOptions.value = catalog.spus.map(item => ({ label: item.spuName, value: item.spuRef }))
    }
    advancedOptionError.value = ''
    advancedFiltersAvailable.value = false
    try {
      const advanced = await getPartnerLeadFilterOptions()
      appealOptions.value = advanced.appealStatuses
      orderOptions.value = advanced.orderReviewStatuses
      advancedFiltersAvailable.value = true
    } catch (cause) {
      advancedOptionError.value = cause instanceof Error ? cause.message : '申诉和订单筛选暂不可用'
    }
  } catch (cause) {
    optionError.value = cause instanceof Error ? cause.message : '筛选项加载失败'
  } finally {
    optionLoading.value = false
  }
}

function openFilters() { showFilters.value = true; void loadOptions() }
function submitSearch() { keyword.value = keywordInput.value.trim(); refresh() }
function clearSearch() { keywordInput.value = ''; keyword.value = ''; refresh() }
function selectStatus(status: string) { activeTab.value = status; refresh() }
function applyFilters() {
  if (Boolean(filters.startDate) !== Boolean(filters.endDate)) { showToast('请选择完整的提交时间范围'); return }
  if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) { showToast('开始日期不能晚于结束日期'); return }
  showFilters.value = false
  refresh()
}
function resetFilters() {
  Object.assign(filters, { simpleStatus: '', assignmentStatus: '', sourceChannel: '', leadCategory: '', startDate: '', endDate: '', mainProductRef: '', appealStatus: '', orderReviewStatus: '' })
  refresh()
}
function clearQuery() {
  activeTab.value = 'all'
  keywordInput.value = ''
  keyword.value = ''
  Object.assign(filters, { simpleStatus: '', assignmentStatus: '', sourceChannel: '', leadCategory: '', startDate: '', endDate: '', mainProductRef: '', appealStatus: '', orderReviewStatus: '' })
  refresh()
}
function goDetail(id: number) { router.push(`/lead/${id}`) }
function goSubmit() { router.push('/lead/submit') }

function statusClass(status: string) {
  return ['valid', 'won'].includes(status) ? 'success'
    : status === 'invalid' ? 'danger'
      : status === 'closed' ? 'muted'
        : status === 'suspended' ? 'warning' : 'primary'
}

function primaryCourse(item: LeadListItem) {
  return item.primaryProduct?.spuName
    || item.intendedProducts?.find(product => product.primary)?.spuName
    || item.intendedProducts?.[0]?.spuName
    || '未填写课程'
}

function stageText(item: LeadListItem) {
  return optionText(stageOptions, item.handlingStage || '') || formatLeadStatus(item.status)
}

function cardDate(item: LeadListItem) {
  if (item.status === 'won' && item.convertedAt) return { label: '成交', value: item.convertedAt }
  if (item.status === 'closed' && item.closedAt) return { label: '关闭', value: item.closedAt }
  return { label: '提交', value: item.submittedAt }
}

function avatarText(item: LeadListItem) {
  return (item.submittedName || '客')[0] || '客'
}
</script>

<template>
  <div class="page-container lead-list-page">
    <van-nav-bar title="我的客资" />

    <section class="card page-hero lead-hero" aria-label="客资概览">
      <div class="page-hero__head">
        <div>
          <div class="page-hero__title">我的客资</div>
          <div class="page-hero__subtitle">查看已提交客资的状态、跟进进度和历史记录。</div>
          <div class="page-hero__meta">
            <span class="page-chip">{{ total }} 条记录</span>
            <span class="page-chip page-chip--muted">{{ activeTabLabel }}</span>
          </div>
        </div>
        <div class="page-hero__aside">
          <div class="page-hero__avatar"><van-icon name="friends-o" size="22" /></div>
        </div>
      </div>
    </section>

    <section class="card lead-toolbar">
      <div class="lead-search">
        <van-search v-model="keywordInput" class="lead-search__field" placeholder="搜索姓名、手机号或客资编号" shape="round" @search="submitSearch" @clear="clearSearch" />
        <button type="button" class="lead-filter-button" :class="{ active: advancedCount > 0 }" @click="openFilters">
          <van-icon name="filter-o" size="17" />
          <span>筛选<span v-if="advancedCount"> {{ advancedCount }}</span></span>
        </button>
      </div>

      <div class="status-segments" role="tablist" aria-label="客资状态">
        <button
          v-for="tab in statusTabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="{ active: activeTab === tab.key }"
          @click="selectStatus(tab.key)"
        >{{ tab.label }}</button>
      </div>

      <button type="button" class="lead-filter-summary" @click="openFilters">
        <span class="filter-summary__title">筛选条件</span>
        <span class="filter-summary__text">{{ activeFilterSummary || '按课程、来源或业务环节筛选' }}</span>
        <span v-if="advancedCount" class="filter-summary__clear" @click.stop="resetFilters">清空</span>
        <van-icon name="arrow" size="14" />
      </button>
    </section>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-if="loading && list.length === 0" class="lead-list lead-list--skeleton">
          <div v-for="index in 3" :key="index" class="page-list-card lead-card lead-card--skeleton"><van-skeleton title :row="3" /></div>
        </div>

        <div v-else-if="!loading && error" class="page-empty-card">
          <van-empty :description="listErrorText" image="error" :image-size="72">
            <van-button type="primary" round size="small" @click="refresh">重新加载</van-button>
          </van-empty>
        </div>

        <div v-else-if="!loading && list.length === 0" class="page-empty-card">
          <van-empty :description="hasActiveQuery ? '没有符合条件的客资' : '暂无客资记录'" :image-size="72">
            <van-button v-if="hasActiveQuery" plain type="primary" round size="small" @click="clearQuery">清空筛选</van-button>
            <van-button v-else type="primary" round size="small" @click="goSubmit">去提交客资</van-button>
          </van-empty>
        </div>

        <div v-else class="lead-list">
          <button v-for="item in list" :key="item.id" type="button" class="page-list-card lead-card" @click="goDetail(item.id)">
            <div class="lead-card__head">
              <div class="lead-card__avatar">{{ avatarText(item) }}</div>
              <div class="lead-card__identity">
                <strong>{{ item.submittedName || '未命名客户' }}</strong>
                <span>{{ formatLeadNo(item.leadNo) }}</span>
              </div>
              <span class="lead-status" :class="`lead-status--${statusClass(item.status)}`">{{ formatLeadStatus(item.status) }}</span>
            </div>
            <div class="lead-card__body">
              <div class="lead-card__course">
                <span>意向课程</span><strong>{{ primaryCourse(item) }}</strong>
              </div>
              <div class="lead-card__foot">
                <div class="lead-card__stage"><span>当前环节</span><strong>{{ stageText(item) }}</strong></div>
                <time>{{ cardDate(item).label }} {{ formatDate(cardDate(item).value) }}</time>
                <van-icon name="arrow" size="15" />
              </div>
            </div>
          </button>
        </div>
      </van-list>
    </van-pull-refresh>

    <button type="button" class="fab-btn" aria-label="提交客资" @click="goSubmit"><van-icon name="plus" size="25" color="#fff" /></button>

    <van-popup v-model:show="showFilters" position="bottom" round class="filter-popup" safe-area-inset-bottom>
      <div class="filter-header">
        <div><strong>筛选条件</strong><small v-if="advancedCount">已选择 {{ advancedCount }} 项</small></div>
        <button type="button" @click="resetFilters">重置</button>
      </div>
      <van-loading v-if="optionLoading" class="filter-loading">加载筛选项...</van-loading>
      <van-empty v-else-if="optionError" :description="optionError" image="error" :image-size="56"><van-button size="mini" type="primary" @click="loadOptions">重试</van-button></van-empty>
      <div v-else class="filter-form">
        <van-notice-bar v-if="advancedOptionError" color="#8a6100" background="#fff7df">申诉和订单筛选暂不可用</van-notice-bar>
        <label><span>业务环节</span><select v-model="filters.simpleStatus"><option v-for="item in stageOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>分配状态</span><select v-model="filters.assignmentStatus"><option v-for="item in assignmentOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>来源渠道</span><select v-model="filters.sourceChannel"><option value="">全部</option><option v-for="item in sourceChannels" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>客资分类</span><select v-model="filters.leadCategory"><option value="">全部</option><option v-for="item in leadCategories" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>主课程</span><select v-model="filters.mainProductRef" :disabled="!advancedFiltersAvailable"><option value="">{{ advancedFiltersAvailable ? '全部' : '暂不可用' }}</option><option v-for="item in productOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>申诉状态</span><select v-model="filters.appealStatus" :disabled="!advancedFiltersAvailable"><option value="">{{ advancedFiltersAvailable ? '全部' : '暂不可用' }}</option><option v-for="item in appealOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>订单状态</span><select v-model="filters.orderReviewStatus" :disabled="!advancedFiltersAvailable"><option value="">{{ advancedFiltersAvailable ? '全部' : '暂不可用' }}</option><option v-for="item in orderOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <div class="date-range"><span>提交时间</span><div><input v-model="filters.startDate" type="date" aria-label="开始日期" /><em>至</em><input v-model="filters.endDate" type="date" aria-label="结束日期" /></div></div>
      </div>
      <div class="filter-actions"><van-button block round type="primary" @click="applyFilters">查看结果</van-button></div>
    </van-popup>
  </div>
</template>

<style scoped>
.lead-list-page {
  min-height: 100vh;
  padding-bottom: 88px;
  background: var(--h5-bg);
}

.lead-hero {
  margin-top: 12px;
  padding: 16px;
}

.lead-toolbar {
  gap: 12px;
}

.lead-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 74px;
  align-items: center;
  gap: 8px;
}

.lead-search__field {
  padding: 0;
  background: transparent;
}

.lead-search__field :deep(.van-search__content) {
  height: 40px;
  align-items: center;
  border-radius: 14px;
  background: var(--h5-bg);
}

.lead-search__field :deep(.van-field__body) {
  border-bottom: 0;
  padding-bottom: 0;
}

.lead-filter-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 40px;
  border: 1px solid var(--h5-border);
  border-radius: 12px;
  background: var(--h5-card-bg);
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.lead-filter-button.active {
  border-color: color-mix(in srgb, var(--h5-primary) 35%, transparent);
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
}

.status-segments {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 6px;
}

.status-segments button {
  min-width: 0;
  height: 34px;
  padding: 0 4px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: var(--h5-bg);
  color: var(--h5-text-secondary);
  font-size: 11px;
  white-space: nowrap;
}

.status-segments button.active {
  border-color: color-mix(in srgb, var(--h5-primary) 35%, transparent);
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-weight: 600;
}

.lead-filter-summary {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 7px;
  padding: 8px 2px 0;
  border: 0;
  border-top: 1px solid var(--h5-divider);
  background: transparent;
  text-align: left;
}

.filter-summary__title {
  color: var(--h5-text-primary);
  font-size: 12px;
  font-weight: 600;
}

.filter-summary__text {
  overflow: hidden;
  color: var(--h5-text-placeholder);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-summary__clear {
  color: var(--h5-primary);
  font-size: 11px;
}

.lead-filter-summary :deep(.van-icon) {
  color: var(--h5-text-placeholder);
}

.lead-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 16px 0;
}

.lead-list .page-list-card + .page-list-card {
  margin-top: 0;
}

.lead-card {
  display: block;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--h5-border);
  border-radius: 16px;
  background: var(--h5-card-bg);
  box-shadow: 0 6px 20px rgba(31, 35, 48, 0.05);
  color: var(--h5-text-primary);
  text-align: left;
}

.lead-card:active {
  transform: scale(0.99);
}

.lead-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lead-card__avatar {
  display: flex;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 14px;
  font-weight: 700;
}

.lead-card__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.lead-card__identity strong {
  overflow: hidden;
  font-size: 15px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lead-card__identity span {
  overflow: hidden;
  color: var(--h5-text-placeholder);
  font-size: 10px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.lead-status {
  flex: 0 0 auto;
  max-width: 88px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lead-status--primary { background: var(--h5-primary-opacity); color: var(--h5-primary); }
.lead-status--success { background: rgba(82, 196, 26, 0.1); color: var(--h5-success); }
.lead-status--danger { background: rgba(255, 77, 79, 0.1); color: var(--h5-danger); }
.lead-status--warning { background: rgba(250, 173, 20, 0.12); color: #c77d00; }
.lead-status--muted { background: var(--h5-bg); color: var(--h5-text-secondary); }

.lead-card__body {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--h5-divider);
}

.lead-card__course {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.lead-card__course span {
  flex: 0 0 auto;
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.lead-card__course strong {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lead-card__foot {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.lead-card__stage {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: baseline;
  gap: 6px;
}

.lead-card__stage span {
  flex: 0 0 auto;
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.lead-card__stage strong {
  min-width: 0;
  overflow: hidden;
  color: var(--h5-primary);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lead-card__foot time {
  flex: 0 0 auto;
  color: var(--h5-text-placeholder);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.lead-card__foot :deep(.van-icon) {
  flex: 0 0 auto;
  color: var(--h5-text-placeholder);
}

.lead-card--skeleton {
  min-height: 128px;
}

.page-empty-card {
  margin: 12px 16px 0;
}

.fab-btn {
  position: fixed;
  right: max(20px, calc((100vw - 10rem) / 2 + 20px));
  bottom: calc(76px + env(safe-area-inset-bottom));
  z-index: 30;
  display: flex;
  width: 52px;
  height: 52px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--h5-gradient);
  box-shadow: 0 7px 18px color-mix(in srgb, var(--h5-primary) 38%, transparent);
}

.filter-popup {
  right: auto;
  left: 50%;
  width: 100%;
  max-width: 10rem;
  max-height: 84vh;
  background: var(--h5-card-bg);
  transform: translate3d(-50%, 0, 0);
}

.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 16px 10px;
}

.filter-header > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.filter-header strong {
  font-size: 17px;
}

.filter-header small {
  color: var(--h5-primary);
  font-size: 11px;
}

.filter-header button {
  padding: 4px;
  border: 0;
  background: transparent;
  color: var(--h5-primary);
  font-size: 12px;
}

.filter-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.filter-form {
  display: grid;
  max-height: 58vh;
  padding: 0 16px;
  overflow-y: auto;
}

.filter-form label,
.date-range {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  align-items: center;
  min-height: 54px;
  border-bottom: 1px solid var(--h5-divider);
  font-size: 13px;
  color: var(--h5-text-primary);
}

.filter-form select,
.date-range input {
  min-width: 0;
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--h5-border);
  border-radius: 8px;
  outline: 0;
  background: var(--h5-bg);
  color: var(--h5-text-primary);
  font-size: 12px;
}

.filter-form select:focus,
.date-range input:focus {
  border-color: var(--h5-primary);
  background: var(--h5-card-bg);
}

.filter-form select:disabled {
  color: var(--h5-text-placeholder);
}

.date-range > div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
}

.date-range em {
  color: var(--h5-text-placeholder);
  font-size: 11px;
  font-style: normal;
  text-align: center;
}

.filter-actions {
  padding: 14px 16px;
}

@media (min-width:700px) {
  .lead-toolbar, .page-empty-card {
    margin-left: auto;
    margin-right: auto;
    width: calc(100% - 32px);
  }

  .fab-btn {
    right: calc((100vw - 10rem) / 2 + 20px);
  }
}
</style>
