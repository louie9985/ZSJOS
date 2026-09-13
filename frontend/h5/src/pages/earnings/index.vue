<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import { getCashbackSummary, getCashbackPage, type CashbackSummary, type CashbackItem } from '@/api/cashback'
import { getPartnerLeadActivity, type PartnerLeadActivity } from '@/api/lead'
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

const selectedOrder = computed(() => {
  const item = selectedCashback.value
  if (!item?.orderId) return undefined
  const orders = detailActivity.value?.orders || []
  return orders.find(order => order.id === item.orderId)
})

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'valid', label: '有效返现' },
  { key: 'deal', label: '成交返现' }
]
const earningsSummaryHelp = [
  '累计收益：累计生成的返现金额。',
  '待结算：已生成，未转为可提现的金额。',
  '提现中：处于审核中的金额。',
  '已提现：提现成功的金额。'
].join('\n')

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
  orderLoading.value = true
  orderError.value = ''
  detailActivity.value = undefined
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
    <section class="card page-hero earnings-hero hero-surface">
      <div class="page-hero__head">
        <div class="earnings-hero__title-wrap">
          <div class="earnings-hero__title-row">
            <div class="page-hero__title">收益中心</div>
            <HelpPopover
              :text="earningsSummaryHelp"
              theme-tint
              aria-label="查看收益金额说明"
            />
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
          <div class="earnings-hero__main-label">可提现金额</div>
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
        </template>
      </van-skeleton>
    </section>

    <div class="earnings-tabs-wrap">
      <LiquidSegmentedControl
        class="earnings-tabs hero-surface"
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
            <div class="earnings-card__amount-row">
              <span class="earnings-card__amount-label">返现收益</span>
              <div class="earnings-card__amount amount--primary">+¥{{ formatAmount(item.amount) }}</div>
            </div>
            <div class="earnings-card__meta-grid">
              <div v-if="item.availableAt" class="earnings-card__meta-item">
                <span>可提现时间</span>
                <strong>{{ formatDate(item.availableAt) }}</strong>
              </div>
              <div v-if="item.settledAt" class="earnings-card__meta-item">
                <span>结算时间</span>
                <strong>{{ formatDate(item.settledAt) }}</strong>
              </div>
              <div v-if="item.cancelReason" class="earnings-card__meta-item earnings-card__meta-item--cancel">
                <span>取消原因</span>
                <strong>{{ item.cancelReason }}</strong>
              </div>
            </div>
          </div>
          <div class="earnings-card__footer">
            <span>收益生成：{{ formatDate(item.generatedAt) }}</span>
            <span class="page-list-card__footer-action">查看详情 <van-icon name="arrow" /></span>
          </div>
        </button>

        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button type="primary" round size="small" @click="refresh">重新加载</van-button>
        </van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无返现记录" />
      </van-list>
    </van-pull-refresh>

    <button v-if="canWithdraw" type="button" class="earnings-fab" aria-label="申请提现" @click="goWithdraw">
      <van-icon name="gold-coin-o" size="25" color="#fff" />
    </button>

    <van-popup
      v-model:show="detailOpen"
      position="bottom"
      round
      teleport="body"
      safe-area-inset-bottom
      class="earnings-detail norem"
      overlay-class="h5-glass-overlay"
      @closed="clearDetail"
    >
      <template v-if="selectedCashback">
        <div class="earnings-detail__grip" aria-hidden="true" />
        <div class="earnings-detail__header">
          <strong>收益详情</strong>
          <button type="button" aria-label="关闭收益详情" title="关闭" @click="detailOpen = false"><van-icon name="cross" size="20" /></button>
        </div>

        <div class="earnings-detail__body">
          <div class="earnings-detail__summary">
            <span class="earnings-detail__summary-label">返现收益</span>
            <strong>+¥{{ formatAmount(selectedCashback.amount) }}</strong>
            <div class="earnings-detail__summary-meta">
              <span>{{ selectedCashback.type === 'valid' ? '有效返现' : '成交返现' }}</span>
              <span class="earnings-detail__status" :style="{ color: statusColor[selectedCashback.status] }">
                {{ statusLabel[selectedCashback.status] || selectedCashback.status }}
              </span>
            </div>
          </div>

          <section class="earnings-detail__section">
            <h3>客资信息</h3>
            <div class="earnings-detail__row"><span>客资编号</span><strong>{{ formatLeadNo(selectedCashback.leadNo) }}</strong></div>
            <div class="earnings-detail__row"><span>意向课程</span><strong>{{ selectedCashback.productNameSnapshot || '课程信息暂缺' }}</strong></div>
          </section>

          <section class="earnings-detail__section">
            <div class="earnings-detail__section-title">
              <h3>订单信息</h3>
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
        </div>

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
  margin-top: 20PX;
  padding: 14px 16px 8px;
}

.earnings-hero > .page-hero__head {
  align-items: center;
}

.earnings-hero__title-wrap {
  min-width: 0;
  flex: 1;
}

.earnings-hero__title-row {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
}

.earnings-hero__btn {
  height: 34px;
  padding: 0 14px;
}

.earnings-hero__main {
  margin-top: 2px;
  font-size: 32px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
  color: var(--h5-primary);
  overflow-wrap: anywhere;
}

.earnings-hero__main-label {
  margin-top: 12px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.earnings-hero__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--h5-divider);
}

.earnings-hero__item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.earnings-hero__value {
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--h5-text-primary);
  line-height: 1.3;
}

.earnings-hero__label {
  margin-top: 1px;
  font-size: 11px;
  color: var(--h5-text-secondary);
}

.earnings-tabs-wrap {
  position: sticky;
  top: 46px;
  z-index: 9;
  padding: 4px 16px;
  border-bottom: 0;
  background: transparent;
  box-shadow: none;
}

.earnings-tabs :deep(.liquid-segmented__indicator) {
  border: 1px solid color-mix(in srgb, var(--h5-primary) 42%, var(--h5-glass-border));
  background: color-mix(in srgb, var(--h5-primary) 25%, var(--h5-card-bg));
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 62%, transparent), 0 4px 12px color-mix(in srgb, var(--h5-primary) 18%, transparent);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .earnings-tabs :deep(.liquid-segmented__indicator) {
    background: color-mix(in srgb, var(--h5-primary) 22%, var(--h5-card-bg));
  }
}

@media (prefers-reduced-transparency: reduce) {
  .earnings-tabs :deep(.liquid-segmented__indicator) {
    background: color-mix(in srgb, var(--h5-primary) 22%, var(--h5-card-bg));
  }
}

.earnings-card {
  display: block;
  width: calc(100% - 32px);
  margin: 4px 16px 0;
  padding: 14px 16px;
  background: var(--h5-content-surface);
  box-shadow: var(--h5-glass-shadow);
  font: inherit;
  text-align: left;
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
}

.earnings-card + .earnings-card {
  margin-top: 12px;
}

.earnings-card:active {
  transform: scale(0.99);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .earnings-card { background: var(--h5-content-surface); }
}

@media (prefers-reduced-transparency: reduce) {
  .earnings-card {
    background: var(--h5-content-surface);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
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

.earnings-card__amount-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.earnings-card__amount-label {
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.earnings-card__amount {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.earnings-card__meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px 14px;
  margin-top: 8px;
}

.earnings-card__meta-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  color: var(--h5-text-secondary);
  font-size: 10px;
  line-height: 1.4;
}

.earnings-card__meta-item strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--h5-text-primary);
  font-size: 11px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.earnings-card__meta-item--cancel {
  grid-column: 1 / -1;
  padding: 8px 10px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--h5-danger) 8%, transparent);
}

.earnings-card__meta-item--cancel strong {
  color: var(--h5-danger);
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

.earnings-fab {
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

.earnings-fab:active {
  transform: scale(0.96);
}

.earnings-detail {
  right: 0;
  left: 0;
  width: min(100%, 10rem);
  height: min(82dvh, 720px);
  max-height: calc(100dvh - 48px);
  display: flex;
  flex-direction: column;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--h5-glass-border);
  border-bottom: 0;
  border-radius: 24px 24px 0 0;
  background: color-mix(in srgb, var(--h5-card-bg) 74%, transparent);
  box-shadow: var(--h5-glass-shadow-floating);
  backdrop-filter: saturate(170%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(170%) blur(var(--h5-glass-blur-strong));
}

.earnings-detail.norem {
  border-radius: 24px 24px 0 0;
}

.earnings-detail__grip {
  flex: 0 0 auto;
  width: 36px;
  height: 3px;
  margin: 8px auto 0;
  border-radius: 999px;
  background: var(--h5-glass-divider);
}

.earnings-detail__header {
  display: flex;
  min-height: 58px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 8px 20px;
  border-bottom: 1px solid var(--h5-glass-divider);
  background: transparent;
}

.earnings-detail__header > strong {
  color: var(--h5-text-primary);
  font-size: 17px;
  font-weight: 600;
}

.earnings-detail__header button {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
}

.earnings-detail__header button:active {
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
}

.earnings-detail__summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  margin: 16px;
  padding: 18px 16px;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 18%, var(--h5-glass-border));
  border-radius: 14px;
  background: color-mix(in srgb, var(--h5-primary-light) 64%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.66);
}

.earnings-detail__summary-label {
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.earnings-detail__summary strong {
  max-width: 100%;
  overflow-wrap: anywhere;
  color: var(--h5-primary);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.earnings-detail__summary-meta {
  display: flex;
  max-width: 100%;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.earnings-detail__status {
  max-width: 96px;
  overflow: hidden;
  padding: 2px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  font-size: 10px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.earnings-detail__section {
  margin: 0 16px 14px;
  padding-top: 14px;
  border-top: 1px solid var(--h5-glass-divider);
}

.earnings-detail__body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
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

.earnings-detail__row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
  line-height: 1.45;
  border-bottom: 1px solid var(--h5-glass-divider);
}

.earnings-detail__row:last-child {
  border-bottom: 0;
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
  flex: 0 0 auto;
  display: flex;
  gap: 10px;
  padding: 10px 16px 14px;
  border-top: 1px solid var(--h5-glass-divider);
  background: transparent;
}

.earnings-detail__actions .van-button {
  min-width: 0;
  height: 44px;
  flex: 1 1 0;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .earnings-detail {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .earnings-detail {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (min-width:700px) {
  .earnings-fab {
    right: calc((100vw - 10rem) / 2 + 20px);
  }
}
</style>
