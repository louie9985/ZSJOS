<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getWithdrawalDetail, cancelWithdrawal, type WithdrawalItem } from '@/api/withdrawal'
import { formatAmount, formatDateTime } from '@/utils/format'

defineOptions({ name: 'WithdrawalDetail' })

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const detail = ref<WithdrawalItem>()
const loading = ref(true)
const cancelling = ref(false)

onMounted(async () => {
  try {
    detail.value = await getWithdrawalDetail(id)
  } finally {
    loading.value = false
  }
})

async function handleCancel() {
  try {
    await showConfirmDialog({ title: '确认取消', message: '确定要取消此提现申请吗？', confirmButtonText: '确认取消' })
    cancelling.value = true
    await cancelWithdrawal(id)
    showSuccessToast('已取消')
    router.back()
  } catch {
    // 取消或失败
  } finally {
    cancelling.value = false
  }
}

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
    <van-nav-bar title="提现详情" left-arrow @click-left="$router.back()" />

    <van-skeleton :loading="loading" :row="6" style="padding: 16px;">
      <template v-if="detail">
        <div class="card detail-amount-card">
          <div class="detail-amount">¥{{ formatAmount(detail.amount) }}</div>
          <div class="detail-status" :style="{ color: statusMap[detail.status]?.color }">
            {{ statusMap[detail.status]?.text || detail.status }}
          </div>
        </div>

        <div class="card">
          <van-cell-group :border="false">
            <van-cell title="收款银行" :value="detail.bankName" />
            <van-cell title="卡号" :value="detail.maskedCardNumber" />
            <van-cell title="申请时间" :value="formatDateTime(detail.createdAt)" />
          </van-cell-group>
        </div>

        <div v-if="detail.status === 'pending_review'" style="padding: 24px 16px;">
          <van-button block round plain type="danger" :loading="cancelling" @click="handleCancel">
            取消提现
          </van-button>
        </div>
      </template>
    </van-skeleton>
  </div>
</template>

<style scoped>
.detail-amount-card {
  text-align: center;
  padding: 32px 16px;
}
.detail-amount {
  font-size: 36px;
  font-weight: 700;
  color: var(--h5-text-primary);
}
.detail-status {
  font-size: 14px;
  margin-top: 8px;
}
</style>
