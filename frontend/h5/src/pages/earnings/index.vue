<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCashbackSummary, getCashbackPage, type CashbackSummary, type CashbackItem } from '@/api/cashback'
import { usePageList } from '@/composables/usePageList'
import { formatAmount, formatDate } from '@/utils/format'

defineOptions({ name: 'Earnings' })

const router = useRouter()
const summary = ref<CashbackSummary>()
const summaryLoading = ref(true)
const activeTab = ref('all')

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'valid', label: '有效返现' },
  { key: 'deal', label: '成交返现' }
]

onMounted(async () => {
  try {
    summary.value = await getCashbackSummary()
  } finally {
    summaryLoading.value = false
  }
})

const filterParams = () => {
  const params: Record<string, unknown> = {}
  if (activeTab.value !== 'all') {
    params.type = activeTab.value
  }
  return params
}

const { list, loading, refreshing, finished, loadMore, refresh } = usePageList(
  (params) => getCashbackPage(params as Parameters<typeof getCashbackPage>[0]),
  filterParams
)

function onTabChange() {
  refresh()
}

function goWithdraw() {
  router.push('/withdrawal/apply')
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

    <!-- 汇总卡片 -->
    <div class="earnings-summary">
      <van-skeleton :loading="summaryLoading" :row="2">
        <div class="earnings-summary__grid">
          <div class="earnings-summary__item">
            <div class="earnings-summary__value">¥{{ formatAmount(summary?.totalAmount) }}</div>
            <div class="earnings-summary__label">累计收益</div>
          </div>
          <div class="earnings-summary__item">
            <div class="earnings-summary__value amount--primary">¥{{ formatAmount(summary?.availableAmount) }}</div>
            <div class="earnings-summary__label">可提现</div>
          </div>
          <div class="earnings-summary__item">
            <div class="earnings-summary__value">¥{{ formatAmount(summary?.pendingAmount) }}</div>
            <div class="earnings-summary__label">待结算</div>
          </div>
        </div>
        <van-button
          v-if="summary && summary.availableAmount > 0"
          type="primary"
          size="small"
          round
          class="earnings-summary__btn"
          @click="goWithdraw"
        >
          去提现
        </van-button>
      </van-skeleton>
    </div>

    <!-- 筛选 Tab -->
    <van-tabs v-model:active="activeTab" @change="onTabChange" shrink sticky offset-top="46">
      <van-tab v-for="tab in tabs" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>

    <!-- 列表 -->
    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="loadMore"
      >
        <div v-for="item in list" :key="item.id" class="card earnings-item">
          <div class="earnings-item__header">
            <span class="earnings-item__product">{{ item.productNameSnapshot || '课程' }}</span>
            <span class="earnings-item__type">{{ item.type === 'valid' ? '有效返现' : '成交返现' }}</span>
          </div>
          <div class="earnings-item__body">
            <span class="earnings-item__amount amount--primary">+¥{{ formatAmount(item.amount) }}</span>
            <span class="earnings-item__status" :style="{ color: statusColor[item.status] }">
              {{ statusLabel[item.status] || item.status }}
            </span>
          </div>
          <div class="earnings-item__footer">
            {{ formatDate(item.generatedAt) }}
          </div>
        </div>

        <van-empty v-if="!loading && list.length === 0" description="暂无返现记录" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.earnings-summary {
  background: var(--h5-gradient);
  margin: 0 16px 12px;
  border-radius: 12px;
  padding: 20px 16px;
  color: #fff;
  margin-top: 12px;
}
.earnings-summary__grid {
  display: flex;
  justify-content: space-around;
  text-align: center;
}
.earnings-summary__value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #fff;
}
.earnings-summary__value.amount--primary {
  color: #fff;
  text-decoration: underline;
  text-underline-offset: 4px;
}
.earnings-summary__label {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}
.earnings-summary__btn {
  display: block;
  margin: 16px auto 0;
  background: rgba(255, 255, 255, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.6);
  color: #fff;
}

.earnings-item {
  margin: 8px 16px;
  padding: 14px 16px;
}
.earnings-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.earnings-item__product {
  font-size: 14px;
  font-weight: 500;
  color: var(--h5-text-primary);
}
.earnings-item__type {
  font-size: 11px;
  color: var(--h5-text-secondary);
  background: var(--h5-primary-opacity);
  padding: 2px 8px;
  border-radius: 10px;
}
.earnings-item__body {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.earnings-item__amount {
  font-size: 18px;
  font-weight: 600;
}
.earnings-item__status {
  font-size: 12px;
}
.earnings-item__footer {
  font-size: 11px;
  color: var(--h5-text-placeholder);
  margin-top: 6px;
}
</style>
