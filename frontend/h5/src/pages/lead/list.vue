<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { usePageList } from '@/composables/usePageList'
import { getDictByType, getLeadCatalog, getMyLeadPage, getPartnerLeadFilterOptions, type LeadFilterOption, type LeadListItem } from '@/api/lead'
import type { DictItem } from '@/stores/app'
import { formatDateTime, formatLeadNo, formatLeadStatus } from '@/utils/format'

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
  { key: 'submitted', label: '已提交' },
  { key: 'valid', label: '有效' },
  { key: 'invalid', label: '无效' },
  { key: 'won', label: '已成交' }
]
const assignmentOptions = [
  { value: '', label: '全部' }, { value: 'unassigned', label: '未分配' },
  { value: 'pending_acceptance', label: '待接单' }, { value: 'owned', label: '已归属' },
  { value: 'public_pool', label: '公海' }, { value: 'closed', label: '已结束' }
]
const stageOptions = [
  { value: '', label: '全部' }, { value: 'first_follow_pending', label: '待首次跟进' },
  { value: 'qualification_pending', label: '待判定' }, { value: 'following', label: '跟进中' },
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

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getMyLeadPage(params as Parameters<typeof getMyLeadPage>[0]), filterParams
)

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
function applyFilters() {
  if (Boolean(filters.startDate) !== Boolean(filters.endDate)) { showToast('请选择完整的提交时间范围'); return }
  if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) { showToast('开始日期不能晚于结束日期'); return }
  showFilters.value = false
  refresh()
}
function resetFilters() { Object.assign(filters, { simpleStatus: '', assignmentStatus: '', sourceChannel: '', leadCategory: '', startDate: '', endDate: '', mainProductRef: '', appealStatus: '', orderReviewStatus: '' }); refresh() }
function goDetail(id: number) { router.push(`/lead/${id}`) }
function goSubmit() { router.push('/lead/submit') }

const statusType: Record<string, string> = {
  submitted: 'primary',
  valid: 'success',
  invalid: 'danger',
  suspended: 'warning',
  won: 'success',
  converted: 'primary',
  closed: 'default'
}

</script>

<template>
  <div class="page-container lead-list-page">
    <van-nav-bar title="我的客资" />
    <div class="lead-search">
      <van-search v-model="keywordInput" placeholder="搜索姓名、手机号或客资编号" shape="round" @search="submitSearch" @clear="clearSearch" />
      <van-button class="filter-button" size="small" :type="advancedCount ? 'primary' : 'default'" plain icon="filter-o" @click="openFilters">筛选<span v-if="advancedCount"> {{ advancedCount }}</span></van-button>
    </div>

    <van-tabs v-model:active="activeTab" @change="refresh" shrink sticky>
      <van-tab v-for="tab in statusTabs" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="loadMore"
      >
        <van-cell
          v-for="item in list"
          :key="item.id"
          :title="item.submittedName"
          :label="`${formatLeadNo(item.leadNo)} · ${formatDateTime(item.submittedAt)}`"
          is-link
          @click="goDetail(item.id)"
        >
          <template #value>
            <van-tag :type="(statusType[item.status] as any) || 'default'" plain>
              {{ formatLeadStatus(item.status) }}
            </van-tag>
          </template>
        </van-cell>
        <van-empty v-if="!loading && error" :description="error" image="error"><van-button type="primary" round size="small" @click="refresh">重新加载</van-button></van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无符合条件的客资"><van-button type="primary" round size="small" @click="goSubmit">去提交客资</van-button></van-empty>
      </van-list>
    </van-pull-refresh>

    <button type="button" class="fab-btn" aria-label="提交客资" @click="goSubmit"><van-icon name="plus" size="24" color="#fff" /></button>

    <van-popup v-model:show="showFilters" position="bottom" round class="filter-popup" safe-area-inset-bottom>
      <div class="filter-header"><strong>高级筛选</strong><button type="button" @click="resetFilters">重置</button></div>
      <van-loading v-if="optionLoading" class="filter-loading">加载筛选项...</van-loading>
      <van-empty v-else-if="optionError" :description="optionError" image="error" :image-size="56"><van-button size="mini" type="primary" @click="loadOptions">重试</van-button></van-empty>
      <div v-else class="filter-form">
        <van-notice-bar v-if="advancedOptionError" color="#8a6100" background="#fff7df">申诉和订单筛选待后端补齐</van-notice-bar>
        <label>业务环节<select v-model="filters.simpleStatus"><option v-for="item in stageOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label>分配状态<select v-model="filters.assignmentStatus"><option v-for="item in assignmentOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label>来源渠道<select v-model="filters.sourceChannel"><option value="">全部</option><option v-for="item in sourceChannels" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label>客资分类<select v-model="filters.leadCategory"><option value="">全部</option><option v-for="item in leadCategories" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label>主课程<select v-model="filters.mainProductRef" :disabled="!advancedFiltersAvailable"><option value="">{{ advancedFiltersAvailable ? '全部' : '待后端提供筛选接口' }}</option><option v-for="item in productOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label>申诉状态<select v-model="filters.appealStatus" :disabled="!advancedFiltersAvailable"><option value="">{{ advancedFiltersAvailable ? '全部' : '待后端提供筛选接口' }}</option><option v-for="item in appealOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label>订单状态<select v-model="filters.orderReviewStatus" :disabled="!advancedFiltersAvailable"><option value="">{{ advancedFiltersAvailable ? '全部' : '待后端提供筛选接口' }}</option><option v-for="item in orderOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <div class="date-range"><span>提交时间</span><div><input v-model="filters.startDate" type="date" aria-label="开始日期" /><span>至</span><input v-model="filters.endDate" type="date" aria-label="结束日期" /></div></div>
      </div>
      <div class="filter-actions"><van-button block round type="primary" @click="applyFilters">查看结果</van-button></div>
    </van-popup>
  </div>
</template>

<style scoped>
.lead-list-page{min-height:100vh}.lead-search{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;padding-right:12px;background:var(--h5-card-bg)}.lead-search :deep(.van-search){padding-right:8px}.filter-button{min-width:70px}.fab-btn{position:fixed;right:20px;bottom:80px;width:52px;height:52px;padding:0;border:0;border-radius:50%;background:var(--h5-gradient);box-shadow:0 4px 12px rgba(255,107,129,.4);z-index:100}.filter-popup{max-height:82vh}.filter-header{display:flex;align-items:center;justify-content:space-between;padding:18px 16px 10px;font-size:16px}.filter-header button{padding:4px;border:0;background:transparent;color:var(--h5-primary)}.filter-loading{display:flex;justify-content:center;padding:40px 0}.filter-form{display:grid;max-height:58vh;padding:0 16px;overflow-y:auto}.filter-form label,.date-range{display:grid;grid-template-columns:82px minmax(0,1fr);align-items:center;min-height:52px;border-bottom:1px solid var(--h5-divider);font-size:14px;color:var(--h5-text-primary)}.filter-form select,.date-range input{min-width:0;width:100%;padding:8px;border:1px solid var(--h5-divider);border-radius:6px;background:var(--h5-card-bg);color:var(--h5-text-primary)}.date-range>div{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:6px}.date-range>div span{text-align:center;color:var(--h5-text-secondary)}.filter-actions{padding:14px 16px}
</style>
