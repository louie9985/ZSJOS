<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePageList } from '@/composables/usePageList'
import { getWithdrawalPage, type WithdrawalItem } from '@/api/withdrawal'
import { formatAmount, formatDateTime } from '@/utils/format'

defineOptions({ name: 'Withdrawal' })

const router = useRouter()
const activeTab = ref('all')

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending_review', label: '待审核' },
  { key: 'approved', label: '已批准' },
  { key: 'paid', label: '已打款' }
]

const filterParams = () => {
  const params: Record<string, unknown> = {}
  if (activeTab.value !== 'all') params.status = activeTab.value
  return params
}

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getWithdrawalPage(params as Parameters<typeof getWithdrawalPage>[0]),
  filterParams
)

function onTabChange() { refresh() }

function goDetail(id: number) { router.push(`/withdrawal/${id}`) }
function goApply() { router.push('/withdrawal/apply') }

const statusMap: Record<string, { text: string; color: string }> = {
  pending_review: { text: '待审核', color: 'var(--h5-warning)' },
  approved: { text: '已批准', color: 'var(--h5-info)' },
  rejected: { text: '已拒绝', color: 'var(--h5-danger)' },
  paid: { text: '已打款', color: 'var(--h5-success)' },
  cancelled: { text: '已取消', color: 'var(--h5-text-secondary)' }
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="提现记录" left-arrow @click-left="$router.back()">
      <template #right>
        <van-icon name="plus" size="20" @click="goApply" />
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeTab" @change="onTabChange" shrink sticky>
      <van-tab v-for="tab in tabs" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="item in list" :key="item.id" class="card withdrawal-item" @click="goDetail(item.id)">
          <div class="withdrawal-item__header">
            <span class="withdrawal-item__amount">¥{{ formatAmount(item.applicationAmount) }}</span>
            <span class="withdrawal-item__status" :style="{ color: statusMap[item.status]?.color }">
              {{ statusMap[item.status]?.text || item.status }}
            </span>
          </div>
          <div class="withdrawal-item__footer">
            <span>{{ item.bankNameSnapshot }} · {{ item.maskedCardNumber }}</span>
            <span>{{ formatDateTime(item.submittedAt) }}</span>
          </div>
        </div>

        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button type="primary" round size="small" @click="refresh">重新加载</van-button>
        </van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无提现记录" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.withdrawal-item {
  margin: 8px 16px;
  padding: 14px 16px;
  cursor: pointer;
}
.withdrawal-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.withdrawal-item__amount {
  font-size: 18px;
  font-weight: 600;
  color: var(--h5-text-primary);
}
.withdrawal-item__status {
  font-size: 12px;
}
.withdrawal-item__footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--h5-text-secondary);
}
</style>
