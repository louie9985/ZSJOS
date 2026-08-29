<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import { getCashbackSummary, getCashbackPage, type CashbackSummary, type CashbackItem } from '@/api/cashback'
import { getPartnerLeadActivity, type PartnerLeadActivity } from '@/api/lead'
import { clearMockedEndpoint, wasMockedEndpoint } from '@/api/mock'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatDate, formatDateTime, formatLeadNo } from '@/utils/format'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'

defineOptions({ name: 'Earnings' })

const router = useRouter()
const userStore = useUserStore()
const summary = ref<CashbackSummary>()
const summaryLoading = ref(true)
const summaryError = ref('')
const activeTab = ref('all')
const canWithdraw = computed(() => userStore.hasPermission('zsjos:withdrawal:apply'))
const detailOpen = ref(false)
const selectedCashback = ref<CashbackItem>()
const detailActivity = ref<PartnerLeadActivity>()
const orderLoading = ref(false)
const orderError = ref('')
let orderRequestVersion = 0

const detailActivityEndpoint = computed(() => selectedCashback.value
  ? `/zsjos/lead/${selectedCashback.value.leadId}/partner-activity`
  : '')
const detailUsingMock = computed(() => !!detailActivityEndpoint.value && wasMockedEndpoint(detailActivityEndpoint.value))
const selectedOrder = computed(() => {
  const item = selectedCashback.value
  if (!item?.orderId) return undefined
  const orders = detailActivity.value?.orders || []
  return orders.find(order => order.id === item.orderId) || (detailUsingMock.value ? orders[0] : undefined)
})

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'valid', label: '有效返现' },
  { key: 'deal', label: '成交返现' }
]
const activeTabLabel = computed(() => tabs.find(tab => tab.key === activeTab.value)?.label || '全部')

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await getCashbackSummary()
  } catch (cause) {
    summaryError.value = cause instanceof Error ? cause.message : '收益汇总加载失败'
  } finally {
    summaryLoading.value = false
  }
}

onMounted(loadSummary)

const filterParams = () => {
  const params: Record<string, unknown> = {}
  if (activeTab.value !== 'all') {
    params.type = activeTab.value
  }
  return params
}

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getCashbackPage(params as Parameters<typeof getCashbackPage>[0]),
  filterParams
)

function onTabChange(value: string) {
  activeTab.value = value
  refresh()
}

function goWithdraw() {
  router.push('/withdrawal/apply')
}

function formatRate(value: number | undefined) {
  if (value == null) return '--'
  const percentage = value <= 1 ? value * 100 : value
  return `${Number(percentage.toFixed(2))}%`
}

function orderFailureMessage(cause: unknown) {
  if (isAxiosError(cause)) {
    if (cause.response?.status === 401) return '登录已失效'
    if (cause.response?.status === 403) return '暂无权限查看订单信息'
    if (cause.response?.status === 500) return '订单信息加载失败，请重试'
    const message = cause.response?.data?.msg
    if (typeof message === 'string' && message) return message
  }
  const message = cause instanceof Error ? cause.message : ''
  if (/后端接口暂未提供|接口暂未提供|请求地址不存在|接口不存在|接口未实现|功能不存在/i.test(message)) {
    return '订单详情接口暂未提供'
  }
  return message || '订单信息加载失败，请重试'
}

async function loadOrderInfo() {
  const item = selectedCashback.value
  if (!item || item.type !== 'deal' || !item.orderId) return
  const requestVersion = ++orderRequestVersion
  const endpoint = `/zsjos/lead/${item.leadId}/partner-activity`
  orderLoading.value = true
  orderError.value = ''
  detailActivity.value = undefined
  clearMockedEndpoint(endpoint)
  try {
    const activity = await getPartnerLeadActivity(item.leadId)
    if (requestVersion !== orderRequestVersion || selectedCashback.value?.id !== item.id) return
    detailActivity.value = activity
  } catch (cause) {
    if (requestVersion !== orderRequestVersion || selectedCashback.value?.id !== item.id) return
    orderError.value = orderFailureMessage(cause)
  } finally {
    if (requestVersion === orderRequestVersion) orderLoading.value = false
  }
}

function openDetail(item: CashbackItem) {
  orderRequestVersion += 1
  selectedCashback.value = item
  detailActivity.value = undefined
  orderError.value = ''
  orderLoading.value = false
  detailOpen.value = true
  void loadOrderInfo()
}

function clearDetail() {
  orderRequestVersion += 1
  selectedCashback.value = undefined
  detailActivity.value = undefined
  orderError.value = ''
  orderLoading.value = false
}

function goLeadDetail() {
  const leadId = selectedCashback.value?.leadId
  if (!leadId) return
  detailOpen.value = false
  router.push(`/lead/${leadId}`)
}

function withdrawFromDetail() {
  detailOpen.value = false
  goWithdraw()
}

const statusLabel: Record<string, string> = {
  pending_settlement: '待结算',
  available: '可提现',
  withdrawing: '提现中',
  withdrawn: '已提现',
  cancelled: '已取消'
}

const statusColor: Record<string, string> = {
  pending_settlement: 'var(--h5-warning)',
  available: 'var(--h5-success)',
  withdrawing: 'var(--h5-info)',
  withdrawn: 'var(--h5-text-secondary)',
  cancelled: 'var(--h5-danger)'
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="收益中心" />

    <section class="card page-hero earnings-hero">
      <div class="page-hero__head">
        <div>
          <div class="page-hero__title">收益中心</div>
          <div class="page-hero__subtitle">查看可提现金额、累计收益和返现明细。</div>
          <div class="page-hero__meta">
            <span class="page-chip">可提现 ¥{{ formatAmount(summary?.availableAmount) }}</span>
            <span class="page-chip page-chip--muted">{{ activeTabLabel }}</span>
          </div>
        </div>
        <div class="page-hero__aside">
          <van-button
            v-if="canWithdraw"
            type="primary"
            size="small"
            round
            class="earnings-hero__btn"
            @click="goWithdraw"
          >
            立即提现
          </van-button>
        </div>
      </div>

      <van-skeleton :loading="summaryLoading" :row="2">
        <van-empty v-if="summaryError" :description="summaryError" image="error">
          <van-button size="small" type="primary" @click="loadSummary">重新加载</van-button>
        </van-empty>
        <template v-else>
          <div class="earnings-hero__main">¥{{ formatAmount(summary?.availableAmount) }}</div>
          <div class="earnings-hero__grid">
            <div class="earnings-hero__item">
              <div class="earnings-hero__value">¥{{ formatAmount(summary?.totalAmount) }}</div>
              <div class="earnings-hero__label">累计收益</div>
            </div>
            <div class="earnings-hero__item">
              <div class="earnings-hero__value">¥{{ formatAmount(summary?.pendingAmount) }}</div>
              <div class="earnings-hero__label">待结算</div>
            </div>
            <div class="earnings-hero__item">
              <div class="earnings-hero__value">¥{{ formatAmount(summary?.withdrawingAmount) }}</div>
              <div class="earnings-hero__label">提现中</div>
            </div>
            <div class="earnings-hero__item">
              <div class="earnings-hero__value">¥{{ formatAmount(summary?.withdrawnAmount) }}</div>
              <div class="earnings-hero__label">已提现</div>
            </div>
          </div>
          <div v-if="summary?.counts" class="earnings-hero__counts">
            <span>待结算 {{ summary.counts.pending_settlement || 0 }} 笔</span>
            <span>可提现 {{ summary.counts.available || 0 }} 笔</span>
            <span>已提现 {{ summary.counts.withdrawn || 0 }} 笔</span>
          </div>
        </template>
      </van-skeleton>
    </section>

    <div class="earnings-tabs-wrap">
      <LiquidSegmentedControl
        class="earnings-tabs"
        :model-value="activeTab"
        :items="tabs"
        ariaLabel="返现类型"
        @change="onTabChange"
      />
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="loadMore"
      >
        <button v-for="item in list" :key="item.id" type="button" class="page-list-card earnings-card" @click="openDetail(item)">
          <div class="earnings-card__head">
            <div class="earnings-card__identity">
              <strong>{{ item.productNameSnapshot || '课程' }}</strong>
              <span>{{ item.type === 'valid' ? '有效返现' : '成交返现' }} · {{ formatLeadNo(item.leadNo) }}</span>
            </div>
            <span class="earnings-card__tag" :style="{ color: statusColor[item.status] }">
              {{ statusLabel[item.status] || item.status }}
            </span>
          </div>
          <div class="earnings-card__body">
            <div class="earnings-card__amount amount--primary">+¥{{ formatAmount(item.amount) }}</div>
            <div class="earnings-card__meta">
              <span v-if="item.cashbackNo">返现单：{{ item.cashbackNo }}</span>
              <span v-if="item.baseAmount != null">基数 ¥{{ formatAmount(item.baseAmount) }}</span>
              <span v-if="item.rateSnapshot != null">比例 {{ formatRate(item.rateSnapshot) }}</span>
              <span v-if="item.observationDaysSnapshot != null">观察期 {{ item.observationDaysSnapshot }} 天</span>
              <span v-if="item.availableAt">可用：{{ formatDate(item.availableAt) }}</span>
              <span v-if="item.settledAt">结算：{{ formatDate(item.settledAt) }}</span>
              <span v-if="item.cancelReason">取消原因：{{ item.cancelReason }}</span>
            </div>
          </div>
          <div class="earnings-card__footer">
            <span>{{ formatLeadNo(item.leadNo) }} · {{ formatDate(item.generatedAt) }}</span>
            <span class="page-list-card__footer-action">查看详情 <van-icon name="arrow" /></span>
          </div>
        </button>

        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button type="primary" round size="small" @click="refresh">重新加载</van-button>
        </van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无返现记录" />
      </van-list>
    </van-pull-refresh>

    <van-popup
      v-model:show="detailOpen"
      position="bottom"
      round
      safe-area-inset-bottom
      class="earnings-detail"
      @closed="clearDetail"
    >
      <template v-if="selectedCashback">
        <div class="earnings-detail__header">
          <strong>收益详情</strong>
          <button type="button" aria-label="关闭收益详情" @click="detailOpen = false"><van-icon name="cross" size="20" /></button>
        </div>

        <div class="earnings-detail__summary">
          <strong>+¥{{ formatAmount(selectedCashback.amount) }}</strong>
          <span>{{ selectedCashback.type === 'valid' ? '有效返现' : '成交返现' }} · {{ statusLabel[selectedCashback.status] || selectedCashback.status }}</span>
        </div>

        <section class="earnings-detail__section">
          <h3>客资信息</h3>
          <div class="earnings-detail__row"><span>客资编号</span><strong>{{ formatLeadNo(selectedCashback.leadNo) }}</strong></div>
          <div class="earnings-detail__row"><span>意向课程</span><strong>{{ selectedCashback.productNameSnapshot || '课程信息暂缺' }}</strong></div>
        </section>

        <section class="earnings-detail__section">
          <div class="earnings-detail__section-title">
            <h3>订单信息</h3>
            <span v-if="detailUsingMock" class="detail-mock-chip">开发环境演示数据</span>
          </div>
          <p v-if="selectedCashback.type !== 'deal'" class="earnings-detail__hint">本笔为有效返现，无需关联订单。</p>
          <p v-else-if="!selectedCashback.orderId" class="earnings-detail__hint">该成交返现暂未关联订单。</p>
          <van-skeleton v-else-if="orderLoading" :loading="true" :row="3" />
          <div v-else-if="orderError" class="earnings-detail__state">
            <span>{{ orderError }}</span>
            <van-button size="mini" type="primary" @click="loadOrderInfo">重试</van-button>
          </div>
          <template v-else-if="selectedOrder">
            <div class="earnings-detail__row"><span>订单编号</span><strong>{{ selectedOrder.orderNo }}</strong></div>
            <div class="earnings-detail__row"><span>订单状态</span><strong>{{ selectedOrder.statusText }}</strong></div>
            <div v-if="selectedOrder.purchaseTypeText" class="earnings-detail__row"><span>购买类型</span><strong>{{ selectedOrder.purchaseTypeText }}</strong></div>
            <div class="earnings-detail__row"><span>订单金额</span><strong>¥{{ formatAmount(selectedOrder.totalAmount) }}</strong></div>
            <div class="earnings-detail__row"><span>订单时间</span><strong>{{ formatDateTime(selectedOrder.createdAt) }}</strong></div>
          </template>
          <p v-else class="earnings-detail__hint">未找到当前返现对应的订单信息。</p>
        </section>

        <section class="earnings-detail__section">
          <h3>收益信息</h3>
          <div class="earnings-detail__row"><span>返现编号</span><strong>{{ selectedCashback.cashbackNo || '暂未生成' }}</strong></div>
          <div class="earnings-detail__row"><span>返现基数</span><strong>¥{{ formatAmount(selectedCashback.baseAmount) }}</strong></div>
          <div v-if="selectedCashback.rateSnapshot != null" class="earnings-detail__row"><span>返现比例</span><strong>{{ formatRate(selectedCashback.rateSnapshot) }}</strong></div>
          <div class="earnings-detail__row"><span>观察期</span><strong>{{ selectedCashback.observationDaysSnapshot ?? 0 }} 天</strong></div>
          <div class="earnings-detail__row"><span>收益时间</span><strong>{{ formatDateTime(selectedCashback.generatedAt) }}</strong></div>
          <div v-if="selectedCashback.availableAt" class="earnings-detail__row"><span>可提现时间</span><strong>{{ formatDateTime(selectedCashback.availableAt) }}</strong></div>
          <div v-if="selectedCashback.settledAt" class="earnings-detail__row"><span>结算时间</span><strong>{{ formatDateTime(selectedCashback.settledAt) }}</strong></div>
          <div v-if="selectedCashback.cancelReason" class="earnings-detail__row"><span>取消原因</span><strong>{{ selectedCashback.cancelReason }}</strong></div>
        </section>

        <div class="earnings-detail__actions">
          <van-button block round plain type="primary" @click="goLeadDetail">查看客资详情</van-button>
          <van-button v-if="canWithdraw && selectedCashback.status === 'available'" block round type="primary" @click="withdrawFromDetail">去提现</van-button>
        </div>
      </template>
    </van-popup>
  </div>
</template>

<style scoped>
.earnings-hero {
  margin-top: 12px;
  padding: 16px;
}

.earnings-hero__btn {
  height: 34px;
  padding: 0 14px;
}

.earnings-hero__main {
  margin-top: 16px;
  font-size: 34px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  color: var(--h5-primary);
}

.earnings-hero__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--h5-divider);
}

.earnings-hero__item {
  min-width: 0;
}

.earnings-hero__value {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--h5-text-primary);
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.earnings-hero__label {
  margin-top: 4px;
  font-size: 11px;
  color: var(--h5-text-secondary);
}

.earnings-hero__counts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 10px;
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.earnings-hero__counts span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--h5-bg);
}

.earnings-tabs-wrap {
  position: sticky;
  top: 46px;
  z-index: 9;
  padding: 0 16px 8px;
  background: var(--h5-card-bg);
}

.earnings-tabs {
  box-shadow: 0 6px 20px rgba(31, 35, 48, 0.05);
}

.earnings-card {
  display: block;
  width: calc(100% - 32px);
  margin: 8px 16px 0;
  padding: 14px 16px;
  font: inherit;
  text-align: left;
}

.earnings-card:active {
  transform: scale(0.99);
}

.earnings-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.earnings-card__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.earnings-card__identity strong {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.earnings-card__identity span {
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.earnings-card__tag {
  flex: 0 0 auto;
  max-width: 88px;
  padding: 3px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  font-size: 10px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.earnings-card__body {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--h5-divider);
}

.earnings-card__amount {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.earnings-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 8px;
  color: var(--h5-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.earnings-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  color: var(--h5-text-placeholder);
  font-size: 11px;
}

.earnings-card__footer span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.earnings-detail {
  right: auto;
  left: 50%;
  width: 100%;
  max-width: 10rem;
  max-height: 82vh;
  overflow-y: auto;
  background: var(--h5-card-bg);
  transform: translate3d(-50%, 0, 0);
}

.earnings-detail__header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--h5-divider);
  background: var(--h5-card-bg);
}

.earnings-detail__header > strong {
  font-size: 16px;
}

.earnings-detail__header button {
  display: flex;
  padding: 4px;
  border: 0;
  background: transparent;
  color: var(--h5-text-secondary);
}

.earnings-detail__summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  margin: 16px;
  padding: 18px;
  border-radius: 16px;
  background: var(--h5-primary-opacity);
}

.earnings-detail__summary strong {
  color: var(--h5-primary);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
}

.earnings-detail__summary span {
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.earnings-detail__section {
  margin: 0 16px 14px;
  padding-top: 14px;
  border-top: 1px solid var(--h5-divider);
}

.earnings-detail__section h3 {
  margin: 0 0 8px;
  font-size: 14px;
}

.earnings-detail__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.earnings-detail__section-title h3 {
  margin-bottom: 8px;
}

.detail-mock-chip {
  padding: 2px 6px;
  border-radius: 4px;
  background: #fff8e6;
  color: var(--h5-warning);
  font-size: 10px;
}

.earnings-detail__row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
  line-height: 1.45;
}

.earnings-detail__row > span {
  color: var(--h5-text-secondary);
}

.earnings-detail__row > strong {
  overflow-wrap: anywhere;
  text-align: right;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.earnings-detail__hint {
  padding: 10px 0;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.earnings-detail__state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.earnings-detail__actions {
  display: flex;
  gap: 10px;
  padding: 8px 16px 18px;
}

.earnings-detail__actions .van-button {
  min-width: 0;
}
</style>
