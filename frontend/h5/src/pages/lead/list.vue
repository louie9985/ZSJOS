<script setup lang="ts">
import ProductSpecs from '../../components/ProductSpecs.vue'
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { usePageList } from '@/composables/usePageList'
import { getDictByType, getLeadCatalog, getMyLeadPage, getPartnerLeadFilterOptions, type LeadFilterOption, type LeadListItem } from '@/api/lead'
import type { DictItem } from '@/stores/app'
import { formatDate, formatLeadNo, formatLeadStatus } from '@/utils/format'
import SmartAvatar from '@/components/SmartAvatar.vue'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'
import { leadAvatarSeed } from '@/config/avatar'

defineOptions({ name: 'LeadList' })

const router = useRouter()
const activeTab = ref('all')
const keywordInput = ref('')
const keyword = ref('')
const showFilters = ref(false)
const showPicker = ref(false)
const pickerKind = ref<'option' | 'date'>('option')
const pickerField = ref<FilterKey>()
const pickerModel = ref<string[]>([])
const optionLoading = ref(false)
const optionError = ref('')
const sourceChannels = ref<DictItem[]>([])
const leadCategories = ref<DictItem[]>([])
const productOptions = ref<Array<{ label: string; value: string }>>([])
const appealOptions = ref<LeadFilterOption[]>([])
const orderOptions = ref<LeadFilterOption[]>([])
const advancedOptionError = ref('')
const advancedFiltersAvailable = ref(false)
type FilterKey = 'simpleStatus' | 'assignmentStatus' | 'sourceChannel' | 'leadCategory' | 'mainProductRef' | 'appealStatus' | 'orderReviewStatus' | 'startDate' | 'endDate'
type FilterValues = Record<FilterKey, string>
const emptyFilters = (): FilterValues => ({ simpleStatus: '', assignmentStatus: '', sourceChannel: '', leadCategory: '', startDate: '', endDate: '', mainProductRef: '', appealStatus: '', orderReviewStatus: '' })
const filters = reactive<FilterValues>(emptyFilters())
const draftFilters = reactive<FilterValues>(emptyFilters())

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

const advancedCount = computed(() => {
  const dateCount = filters.startDate || filters.endDate ? 1 : 0
  return Object.entries(filters).filter(([key, value]) => value && key !== 'startDate' && key !== 'endDate').length + dateCount
})
const pickerTitle = computed(() => pickerField.value ? ({
  simpleStatus: '业务环节', assignmentStatus: '分配状态', sourceChannel: '来源渠道', leadCategory: '客资分类',
  mainProductRef: '主课程', appealStatus: '申诉状态', orderReviewStatus: '订单状态', startDate: '开始日期', endDate: '结束日期'
}[pickerField.value]) : '')
const pickerColumns = computed(() => {
  if (!pickerField.value || pickerKind.value === 'date') return []
  return filterOptions(pickerField.value).map(item => ({ text: item.label, value: item.value }))
})
const pickerMinDate = new Date(2020, 0, 1)
const pickerMaxDate = new Date()
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

const { list, total, pageNo, pageCount, loading, refreshing, error, loadPage, refresh } = usePageList(
  (params) => getMyLeadPage(params as Parameters<typeof getMyLeadPage>[0]), filterParams,
  { mode: 'page', pageSize: 20 }
)

const savedListPosition = ref<{ scrollTop: number }>()
const lastScrollTop = ref(0)
const failedPage = ref<number>()

function rememberScrollPosition() {
  lastScrollTop.value = window.scrollY
}

onMounted(() => {
  rememberScrollPosition()
  window.addEventListener('scroll', rememberScrollPosition, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('scroll', rememberScrollPosition))

function saveListPosition() {
  const scrollTop = window.scrollY
  lastScrollTop.value = scrollTop
  savedListPosition.value = { scrollTop }
}

async function restoreListPosition() {
  const saved = savedListPosition.value
  if (!saved) return
  await nextTick()
  const restore = () => window.scrollTo({ top: saved.scrollTop, behavior: 'auto' })
  restore()
  requestAnimationFrame(restore)
  window.setTimeout(restore, 100)
}

onActivated(() => { void restoreListPosition() })
onDeactivated(() => {
  if (!savedListPosition.value) saveListPosition()
})

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

function filterOptions(key: FilterKey) {
  if (key === 'simpleStatus') return stageOptions
  if (key === 'assignmentStatus') return assignmentOptions
  if (key === 'sourceChannel') return [{ value: '', label: '全部' }, ...sourceChannels.value]
  if (key === 'leadCategory') return [{ value: '', label: '全部' }, ...leadCategories.value]
  if (key === 'mainProductRef') return [{ value: '', label: advancedFiltersAvailable.value ? '全部' : '暂不可用' }, ...productOptions.value]
  if (key === 'appealStatus') return [{ value: '', label: advancedFiltersAvailable.value ? '全部' : '暂不可用' }, ...appealOptions.value]
  if (key === 'orderReviewStatus') return [{ value: '', label: advancedFiltersAvailable.value ? '全部' : '暂不可用' }, ...orderOptions.value]
  return []
}

function draftOptionText(key: FilterKey) {
  return optionText(filterOptions(key), draftFilters[key]) || '全部'
}

function formatPickerDate(value: string) {
  return value || '请选择日期'
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

function openFilters() { Object.assign(draftFilters, filters); showFilters.value = true; void loadOptions() }
async function refreshList() {
  failedPage.value = undefined
  await refresh()
}
function submitSearch() { keyword.value = keywordInput.value.trim(); void refreshList() }
function clearSearch() { keywordInput.value = ''; keyword.value = ''; void refreshList() }
function selectStatus(status: string) { activeTab.value = status; void refreshList() }
function applyFilters() {
  if (Boolean(draftFilters.startDate) !== Boolean(draftFilters.endDate)) { showToast('请选择完整的提交时间范围'); return }
  if (draftFilters.startDate && draftFilters.endDate && draftFilters.startDate > draftFilters.endDate) { showToast('开始日期不能晚于结束日期'); return }
  Object.assign(filters, draftFilters)
  showFilters.value = false
  void refreshList()
}
function resetDraftFilters() {
  Object.assign(draftFilters, emptyFilters())
}
function clearActiveFilters() {
  Object.assign(filters, emptyFilters())
  Object.assign(draftFilters, emptyFilters())
  void refreshList()
}
function clearQuery() {
  activeTab.value = 'all'
  keywordInput.value = ''
  keyword.value = ''
  Object.assign(filters, emptyFilters())
  Object.assign(draftFilters, emptyFilters())
  void refreshList()
}
function openOptionPicker(field: FilterKey) {
  pickerField.value = field
  pickerKind.value = 'option'
  pickerModel.value = [draftFilters[field] || filterOptions(field)[0]?.value || '']
  showPicker.value = true
}
function openDatePicker(field: 'startDate' | 'endDate') {
  pickerField.value = field
  pickerKind.value = 'date'
  const value = draftFilters[field] || new Date().toISOString().slice(0, 10)
  pickerModel.value = value.split('-')
  showPicker.value = true
}
function confirmPicker(values?: { selectedValues?: string[] } | string[]) {
  if (!pickerField.value) return
  const field = pickerField.value
  const selectedValues = Array.isArray(values) ? values : values?.selectedValues
  if (pickerKind.value === 'date') {
    const selected = (selectedValues || pickerModel.value).map(value => String(value).padStart(2, '0'))
    draftFilters[field] = selected.join('-')
  } else {
    draftFilters[field] = (selectedValues || pickerModel.value)[0] || ''
  }
  showPicker.value = false
}
function cancelPicker() { showPicker.value = false }
function goDetail(id: number) {
  saveListPosition()
  void router.push(`/lead/${id}`)
}
async function changePage(targetPage: number) {
  if (loading.value) return
  failedPage.value = targetPage
  await loadPage(targetPage)
  if (!error.value) {
    failedPage.value = undefined
    window.scrollTo({ top: 0, behavior: 'auto' })
  }
}
async function retryCurrentPage() {
  await loadPage(failedPage.value ?? pageNo.value)
  if (!error.value) failedPage.value = undefined
}
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

</script>

<template>
  <div class="page-container lead-list-page">
    <section class="lead-toolbar">
      <div class="lead-search">
        <van-search v-model="keywordInput" class="lead-search__field" placeholder="搜索姓名、手机号或客资编号" shape="round" @search="submitSearch" @clear="clearSearch" @click-left-icon="submitSearch" />
        <button type="button" class="lead-filter-button" :class="{ active: advancedCount > 0 }" @click="openFilters">
          <van-icon name="filter-o" size="17" />
          <span>筛选</span>
          <em v-if="advancedCount">{{ advancedCount }}</em>
        </button>
      </div>

      <LiquidSegmentedControl
        class="status-segments"
        :model-value="activeTab"
        :items="statusTabs"
        ariaLabel="客资状态"
        compact
        @change="selectStatus"
      />

      <button v-if="advancedCount" type="button" class="lead-filter-summary" @click="openFilters">
        <span class="filter-summary__title">筛选条件</span>
        <span class="filter-summary__text">{{ activeFilterSummary || '按课程、来源或业务环节筛选' }}</span>
        <span class="filter-summary__clear" @click.stop="clearActiveFilters">清空</span>
        <van-icon name="arrow" size="14" />
      </button>
    </section>

    <van-pull-refresh v-model="refreshing" @refresh="refreshList">
        <div v-if="loading && list.length === 0" class="lead-list lead-list--skeleton">
          <div v-for="index in 3" :key="index" class="page-list-card lead-card lead-card--skeleton"><van-skeleton title :row="3" /></div>
        </div>

        <div v-else-if="!loading && error && list.length === 0" class="page-empty-card">
          <van-empty :description="listErrorText" image="error" :image-size="72">
            <van-button type="primary" round size="small" @click="retryCurrentPage">重新加载</van-button>
          </van-empty>
        </div>

        <div v-else-if="!loading && list.length === 0" class="page-empty-card">
          <van-empty :description="hasActiveQuery ? '没有符合条件的客资' : '暂无客资记录'" :image-size="72">
            <van-button v-if="hasActiveQuery" plain type="primary" round size="small" @click="clearQuery">清空筛选</van-button>
            <van-button v-else type="primary" round size="small" @click="goSubmit">去提交客资</van-button>
          </van-empty>
        </div>

        <div v-else class="lead-list">
          <button v-for="item in list" :key="item.id" :data-lead-id="item.id" type="button" class="page-list-card lead-card" @click="goDetail(item.id)">
            <div class="lead-card__head">
              <SmartAvatar class="lead-card__avatar" :seed="leadAvatarSeed(item.id)" :size="36" shape="rounded" label="" />
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
              <ProductSpecs :product="item.primaryProduct || item.intendedProducts?.find(p => p.primary) || item.intendedProducts?.[0] || {}" />
              <div class="lead-card__foot">
                <div class="lead-card__stage"><span>当前环节</span><strong>{{ stageText(item) }}</strong></div>
                <time>{{ cardDate(item).label }} {{ formatDate(cardDate(item).value) }}</time>
                <van-icon name="arrow" size="15" />
              </div>
            </div>
          </button>
          <div v-if="error" class="page-inline-error">
            <span>{{ listErrorText }}</span>
            <van-button size="mini" plain type="primary" @click="retryCurrentPage">重试</van-button>
          </div>
        </div>
        <div v-if="!loading && !error && total > 0" class="lead-pagination">
          <van-pagination :model-value="pageNo" :page-count="pageCount" mode="simple" :disabled="loading" @change="changePage" />
        </div>
    </van-pull-refresh>

    <button type="button" class="fab-btn" aria-label="提交客资" @click="goSubmit"><van-icon name="plus" size="25" color="#fff" /></button>

    <van-popup
      v-model:show="showFilters"
      position="bottom"
      round
      teleport="body"
      class="filter-popup norem"
      overlay-class="h5-glass-overlay"
      safe-area-inset-bottom
    >
      <div class="filter-popup__grip" aria-hidden="true" />
      <div class="filter-header">
        <div><strong>筛选条件</strong><small v-if="advancedCount">已选择 {{ advancedCount }} 项</small></div>
        <div class="filter-header__actions">
          <button type="button" class="filter-reset" @click="resetDraftFilters">重置</button>
          <button type="button" class="filter-close" aria-label="关闭筛选" title="关闭" @click="showFilters = false"><van-icon name="cross" size="20" /></button>
        </div>
      </div>
      <van-loading v-if="optionLoading" class="filter-loading">加载筛选项...</van-loading>
      <van-empty v-else-if="optionError" :description="optionError" image="error" :image-size="56"><van-button size="mini" type="primary" @click="loadOptions">重试</van-button></van-empty>
      <div v-else class="filter-form">
        <van-notice-bar v-if="advancedOptionError" class="filter-notice" wrapable>申诉和订单筛选暂不可用</van-notice-bar>
        <button type="button" class="filter-row" @click="openOptionPicker('simpleStatus')"><span>业务环节</span><strong>{{ draftOptionText('simpleStatus') }}</strong><van-icon name="arrow" /></button>
        <button type="button" class="filter-row" @click="openOptionPicker('assignmentStatus')"><span>分配状态</span><strong>{{ draftOptionText('assignmentStatus') }}</strong><van-icon name="arrow" /></button>
        <button type="button" class="filter-row" @click="openOptionPicker('sourceChannel')"><span>来源渠道</span><strong>{{ draftOptionText('sourceChannel') }}</strong><van-icon name="arrow" /></button>
        <button type="button" class="filter-row" @click="openOptionPicker('leadCategory')"><span>客资分类</span><strong>{{ draftOptionText('leadCategory') }}</strong><van-icon name="arrow" /></button>
        <button type="button" class="filter-row" :disabled="!advancedFiltersAvailable" @click="openOptionPicker('mainProductRef')"><span>主课程</span><strong>{{ draftOptionText('mainProductRef') }}</strong><van-icon name="arrow" /></button>
        <button type="button" class="filter-row" :disabled="!advancedFiltersAvailable" @click="openOptionPicker('appealStatus')"><span>申诉状态</span><strong>{{ draftOptionText('appealStatus') }}</strong><van-icon name="arrow" /></button>
        <button type="button" class="filter-row" :disabled="!advancedFiltersAvailable" @click="openOptionPicker('orderReviewStatus')"><span>订单状态</span><strong>{{ draftOptionText('orderReviewStatus') }}</strong><van-icon name="arrow" /></button>
        <div class="date-range"><span>提交时间</span><div><button type="button" class="date-trigger" @click="openDatePicker('startDate')">{{ formatPickerDate(draftFilters.startDate) }}<van-icon name="arrow" /></button><em>至</em><button type="button" class="date-trigger" @click="openDatePicker('endDate')">{{ formatPickerDate(draftFilters.endDate) }}<van-icon name="arrow" /></button></div></div>
      </div>
      <div class="filter-actions"><van-button block round type="primary" @click="applyFilters">确认</van-button></div>
    </van-popup>

    <van-popup
      v-model:show="showPicker"
      position="bottom"
      round
      teleport="body"
      class="picker-popup norem"
      overlay-class="h5-glass-overlay"
      safe-area-inset-bottom
    >
      <div class="picker-popup__grip" aria-hidden="true" />
      <van-picker
        v-if="pickerKind === 'option'"
        v-model="pickerModel"
        :columns="pickerColumns"
        :title="pickerTitle"
        cancel-button-text="取消"
        confirm-button-text="确认"
        @cancel="cancelPicker"
        @confirm="confirmPicker"
      />
      <van-date-picker
        v-else
        v-model="pickerModel"
        :title="pickerTitle"
        :min-date="pickerMinDate"
        :max-date="pickerMaxDate"
        :columns-type="['year', 'month', 'day']"
        cancel-button-text="取消"
        confirm-button-text="确认"
        @cancel="cancelPicker"
        @confirm="confirmPicker"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.lead-list-page {
  min-height: 100vh;
  padding-bottom: 88px;
  background: transparent;
}

.lead-toolbar {
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  margin-top: 20PX;
  gap: 10px;
  background: transparent;
}

.lead-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 68px;
  align-items: center;
  min-height: 40px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 12%, var(--h5-glass-border));
  border-radius: 14px;
  background: transparent;
}

.lead-search__field {
  min-width: 0;
  height: 40px;
  padding: 0;
  background: transparent;
}

.lead-search__field :deep(.van-search__content) {
  height: 40px;
  align-items: center;
  border-radius: 0;
  background: transparent;
}

.lead-search__field :deep(.van-field__control) {
  color: var(--h5-text-primary);
  font-size: 13px;
}

.lead-search__field :deep(.van-field__left-icon) {
  color: var(--h5-primary);
  cursor: pointer;
}

.lead-search__field :deep(.van-field__control::placeholder) {
  color: var(--h5-text-placeholder);
}

.lead-search__field :deep(.van-field__body) {
  border-bottom: 0;
  padding-bottom: 0;
}

.lead-filter-button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  height: 40px;
  padding: 0 7px;
  border: 0;
  border-left: 1px solid var(--h5-glass-divider);
  border-radius: 0;
  background: transparent;
  color: var(--h5-primary-dark);
  font-size: 11px;
  font-weight: 600;
}

.lead-filter-button em {
  display: inline-flex;
  min-width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  padding: 0 3px;
  border-radius: 999px;
  background: var(--h5-primary);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  line-height: 16px;
}

.lead-filter-button.active {
  background: var(--h5-primary-opacity);
}

.lead-filter-button:active {
  transform: scale(0.98);
}

.status-segments :deep(.liquid-segmented) {
  border-color: color-mix(in srgb, var(--h5-primary) 12%, var(--h5-glass-border));
  background: transparent;
  box-shadow: none;
}

.lead-filter-summary {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 7px;
  padding: 0 2px;
  border: 0;
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
  gap: 12px;
  padding: 6px 16px 0;
}

.lead-pagination {
  padding: 18px 16px 12px;
}

.lead-pagination :deep(.van-pagination) {
  --van-pagination-background: transparent;
  --van-pagination-button-width: 42%;
  --van-pagination-item-default-color: var(--h5-text-primary);
  --van-pagination-item-disabled-color: var(--h5-text-placeholder);
  --van-pagination-item-disabled-background: transparent;
}

.lead-pagination :deep(.van-pagination__page-desc) {
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.page-inline-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 6px 0 2px;
  color: var(--h5-danger);
  font-size: 12px;
}

.lead-list .page-list-card + .page-list-card {
  margin-top: 0;
}

.lead-card {
  display: block;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 16px;
  background: var(--h5-content-surface);
  box-shadow: var(--h5-glass-shadow);
  color: var(--h5-text-primary);
  text-align: left;
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
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
.lead-status--muted { background: var(--h5-glass-sunken); color: var(--h5-text-secondary); }

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

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .lead-card { background: var(--h5-content-surface); }
}

@media (prefers-reduced-transparency: reduce) {
  .lead-card {
    background: var(--h5-content-surface);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
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
  right: 0;
  left: 0;
  display: flex;
  width: min(100%, 10rem);
  max-height: min(84dvh, 720px);
  flex-direction: column;
  margin: 0 auto;
  overflow: hidden;
  border: 0;
  border-radius: 24px 24px 0 0;
  background: var(--h5-card-bg);
  box-shadow: var(--h5-glass-shadow-floating);
}

.picker-popup {
  --van-picker-background: transparent;
  --van-picker-option-text-color: var(--h5-text-secondary);
  --van-picker-mask-color:
    linear-gradient(180deg, color-mix(in srgb, var(--h5-card-bg) 90%, transparent), transparent),
    linear-gradient(0deg, color-mix(in srgb, var(--h5-card-bg) 90%, transparent), transparent);

  right: 0;
  left: 0;
  width: min(100%, 10rem);
  margin: 0 auto;
  overflow: hidden;
  border: 0;
  border-radius: 24px 24px 0 0;
  background: var(--h5-card-bg);
  box-shadow: var(--h5-glass-shadow-floating);
}

.filter-popup.norem,
.picker-popup.norem {
  border-radius: 24px 24px 0 0;
}

.filter-popup__grip,
.picker-popup__grip {
  width: 32px;
  height: 3px;
  flex: 0 0 auto;
  align-self: center;
  margin-top: 8px;
  border-radius: 999px;
  background: var(--h5-glass-divider);
}

.picker-popup__grip {
  display: none;
}

.picker-popup :deep(.van-picker) {
  border: 0;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
}

.picker-popup :deep(.van-picker__toolbar) {
  min-height: 54px;
  background: transparent;
}

.picker-popup :deep(.van-picker__title) {
  color: var(--h5-text-primary);
  font-size: 17px;
  font-weight: 600;
}

.picker-popup :deep(.van-picker__cancel),
.picker-popup :deep(.van-picker__confirm) {
  height: 34px;
  margin: 0 10px;
  padding: 0 10px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 10px;
  background: var(--h5-glass-sunken);
  font-size: 12px;
}

.picker-popup :deep(.van-picker__cancel) { color: var(--h5-text-secondary); }
.picker-popup :deep(.van-picker__confirm) { color: var(--h5-primary); font-weight: 600; }

.picker-popup :deep(.van-picker__columns) {
  margin: 8px 12px 12px;
  background: transparent;
}

.picker-popup :deep(.van-picker__frame) {
  right: 8px;
  left: 8px;
  border: 0;
  border-radius: 12px;
  background: color-mix(in srgb, var(--h5-text-primary) 6%, transparent);
}

.picker-popup :deep(.van-picker__frame::after) {
  border: 0;
}

.picker-popup :deep(.van-picker-column__item) {
  color: var(--h5-text-secondary);
}

.picker-popup :deep(.van-picker-column__item--selected) {
  position: relative;
  z-index: 3;
  color: var(--h5-text-primary);
  font-weight: 650;
}

.filter-header {
  display: flex;
  min-height: 58px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px 8px 20px;
  border-bottom: 1px solid var(--h5-glass-divider);
  background: transparent;
}

.filter-header > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.filter-header strong {
  color: var(--h5-text-primary);
  font-size: 17px;
  font-weight: 600;
}

.filter-header small {
  color: var(--h5-primary);
  font-size: 11px;
}

.filter-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.filter-header button {
  border: 0;
  font: inherit;
}

.filter-header .filter-reset {
  min-width: 44px;
  height: 32px;
  padding: 0 8px;
  border-radius: 10px;
  background: var(--h5-glass-sunken);
  color: var(--h5-primary);
  font-size: 11px;
}

.filter-close {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 50%;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
}

.filter-reset:active,
.filter-close:active {
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
}

.filter-loading {
  display: flex;
  min-height: 240px;
  justify-content: center;
  align-items: center;
  padding: 40px 0;
  color: var(--h5-primary);
}

.filter-form {
  display: grid;
  min-height: 0;
  flex: 1;
  padding: 8px 16px 12px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.filter-notice {
  min-height: 38px;
  height: auto;
  margin: 2px 0 8px;
  padding: 7px 10px;
  border: 1px solid color-mix(in srgb, var(--h5-warning) 28%, var(--h5-glass-border));
  border-radius: 10px;
  background: color-mix(in srgb, var(--h5-warning) 10%, transparent);
  color: color-mix(in srgb, var(--h5-warning) 76%, var(--h5-text-primary));
  font-size: 11px;
}

.filter-form .filter-row,
.date-range {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  align-items: center;
  min-height: 46px;
  border-bottom: 1px solid var(--h5-glass-divider);
  font-size: 13px;
  color: var(--h5-text-primary);
}

.filter-row {
  position: relative;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.filter-row strong {
  display: flex;
  min-width: 0;
  min-height: 32px;
  align-items: center;
  overflow: hidden;
  padding: 0 30px 0 12px;
  border: 1px solid var(--h5-border);
  border-radius: 11px;
  background: transparent;
  color: var(--h5-text-primary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-row :deep(.van-icon) {
  position: absolute;
  right: 12px;
  color: var(--h5-primary);
  pointer-events: none;
}

.filter-row:active:not(:disabled) strong {
  border-color: color-mix(in srgb, var(--h5-primary) 38%, var(--h5-border));
  background: transparent;
}

.filter-row:disabled { opacity: 0.48; }

.date-range { grid-template-columns: 82px minmax(0, 1fr); }
.date-trigger {
  min-width: 0;
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--h5-border);
  border-radius: 11px;
  outline: 0;
  background: transparent;
  color: var(--h5-text-primary);
  font-size: 12px;
}

.date-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  color: var(--h5-text-primary);
  text-align: left;
}

.date-trigger:empty { color: var(--h5-text-placeholder); }
.date-trigger :deep(.van-icon) { flex: 0 0 auto; color: var(--h5-primary); }

.date-trigger:active {
  border-color: color-mix(in srgb, var(--h5-primary) 38%, var(--h5-border));
  background: transparent;
}

.date-trigger:focus-visible,
.filter-row:focus-visible {
  border-color: var(--h5-primary);
  background: transparent;
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
  flex: 0 0 auto;
  padding: 10px 16px 14px;
  border-top: 1px solid var(--h5-glass-divider);
  background: transparent;
}

.filter-actions :deep(.van-button) {
  height: 44px;
  border: 0;
  background: var(--h5-gradient);
  box-shadow: 0 7px 18px color-mix(in srgb, var(--h5-primary) 24%, transparent);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .filter-popup,
  .picker-popup {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .filter-popup,
  .picker-popup {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (min-width:700px) {
  .page-empty-card {
    margin-left: auto;
    margin-right: auto;
    width: calc(100% - 32px);
  }

  .fab-btn {
    right: calc((100vw - 10rem) / 2 + 20px);
  }
}
</style>
