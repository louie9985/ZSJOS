<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getWithdrawalSummary, getMyCards, applyWithdrawal, addCard, type WithdrawalSummary, type BankCard } from '@/api/withdrawal'
import { getCashbackPage, type CashbackItem } from '@/api/cashback'
import { formatAmount } from '@/utils/format'

defineOptions({ name: 'WithdrawalApply' })

const router = useRouter()
const loading = ref(true)
const submitting = ref(false)

// Data
const summary = ref<WithdrawalSummary>()
const availableItems = ref<CashbackItem[]>([])
const bankCards = ref<BankCard[]>([])

// Form
const selectedIds = ref<Set<number>>(new Set())
const selectedCardId = ref<number>()
const showNewCard = ref(false)
const newCard = ref({ accountName: '', cardNumber: '', bankName: '', branchName: '' })
const saveNewCard = ref(true)

onMounted(async () => {
  try {
    const [summaryData, cashbackData, cardsData] = await Promise.all([
      getWithdrawalSummary(),
      getCashbackPage({ pageNo: 1, pageSize: 200, status: 'available' }),
      getMyCards()
    ])
    summary.value = summaryData
    availableItems.value = cashbackData.list
    bankCards.value = cardsData
    // 默认选中默认卡
    const defaultCard = cardsData.find(c => c.defaultCard)
    if (defaultCard) selectedCardId.value = defaultCard.id
  } finally {
    loading.value = false
  }
})

// 全选/反选
const allSelected = computed(() =>
  availableItems.value.length > 0 && availableItems.value.every(item => selectedIds.value.has(item.id))
)

function toggleAll() {
  if (allSelected.value) {
    selectedIds.value.clear()
  } else {
    availableItems.value.forEach(item => selectedIds.value.add(item.id))
  }
}

function toggleItem(id: number) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

const selectedAmount = computed(() => {
  return availableItems.value
    .filter(item => selectedIds.value.has(item.id))
    .reduce((sum, item) => sum + item.amount, 0)
})

const canSubmit = computed(() => {
  if (selectedIds.value.size === 0) return false
  if (summary.value && selectedAmount.value < summary.value.minimumAmount) return false
  if (!selectedCardId.value && !showNewCard.value) return false
  if (showNewCard.value && (!newCard.value.accountName || !newCard.value.cardNumber || !newCard.value.bankName)) return false
  return true
})

async function handleSubmit() {
  if (submitting.value || !canSubmit.value) return
  submitting.value = true
  try {
    const params: Parameters<typeof applyWithdrawal>[0] = {
      cashbackIds: Array.from(selectedIds.value)
    }

    if (showNewCard.value) {
      params.accountName = newCard.value.accountName
      params.cardNumber = newCard.value.cardNumber
      params.bankName = newCard.value.bankName
      params.branchName = newCard.value.branchName
      params.saveCard = saveNewCard.value
    } else {
      params.bankCardId = selectedCardId.value
    }

    await applyWithdrawal(params)
    showSuccessToast('提现申请已提交')
    router.back()
  } catch {
    // 拦截器已处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="申请提现" left-arrow @click-left="$router.back()" />

    <van-skeleton :loading="loading" :row="8" style="padding: 16px;">
      <!-- 汇总 -->
      <div class="card apply-summary">
        <div class="apply-summary__label">可提现金额</div>
        <div class="apply-summary__amount">¥{{ formatAmount(summary?.availableAmount) }}</div>
        <div class="apply-summary__min">最低提现：¥{{ formatAmount(summary?.minimumAmount) }}</div>
      </div>

      <!-- 可提现条目 -->
      <div class="card">
        <div class="section-header">
          <span class="section-title">选择提现项目</span>
          <van-checkbox :model-value="allSelected" @click="toggleAll" icon-size="16">全选</van-checkbox>
        </div>
        <div v-if="availableItems.length === 0" style="text-align: center; padding: 20px; color: var(--h5-text-placeholder);">
          暂无可提现项目
        </div>
        <div v-for="item in availableItems" :key="item.id" class="apply-item" @click="toggleItem(item.id)">
          <van-checkbox :model-value="selectedIds.has(item.id)" icon-size="18" />
          <div class="apply-item__info">
            <div class="apply-item__name">{{ item.productNameSnapshot }}</div>
            <div class="apply-item__type">{{ item.type === 'valid' ? '有效返现' : '成交返现' }}</div>
          </div>
          <div class="apply-item__amount">+¥{{ formatAmount(item.amount) }}</div>
        </div>
      </div>

      <!-- 银行卡选择 -->
      <div class="card">
        <div class="section-title" style="margin-bottom: 12px;">收款银行卡</div>

        <van-radio-group v-if="!showNewCard" v-model="selectedCardId">
          <div v-for="card in bankCards" :key="card.id" class="card-item">
            <van-radio :name="card.id" icon-size="18">
              <div>
                <span class="card-item__bank">{{ card.bankName }}</span>
                <span class="card-item__number">{{ card.maskedCardNumber }}</span>
              </div>
            </van-radio>
          </div>
        </van-radio-group>

        <van-button v-if="!showNewCard" size="small" plain round icon="plus" @click="showNewCard = true" style="margin-top: 8px;">
          使用新银行卡
        </van-button>

        <!-- 新银行卡表单 -->
        <div v-if="showNewCard" class="new-card-form">
          <van-field v-model="newCard.accountName" label="户名" placeholder="持卡人姓名" required />
          <van-field v-model="newCard.cardNumber" label="卡号" type="digit" placeholder="银行卡号" required />
          <van-field v-model="newCard.bankName" label="银行" placeholder="开户银行" required />
          <van-field v-model="newCard.branchName" label="支行" placeholder="开户支行（选填）" />
          <van-checkbox v-model="saveNewCard" icon-size="16" style="padding: 8px 16px;">保存此银行卡</van-checkbox>
          <van-button size="small" plain @click="showNewCard = false" style="margin: 8px 16px;">取消，使用已保存的卡</van-button>
        </div>
      </div>

      <!-- 底部提交 -->
      <div class="apply-bottom safe-area-bottom">
        <div class="apply-bottom__info">
          <span>已选 {{ selectedIds.size }} 项</span>
          <span class="apply-bottom__total">合计：<b>¥{{ formatAmount(selectedAmount) }}</b></span>
        </div>
        <van-button type="primary" round :disabled="!canSubmit" :loading="submitting" @click="handleSubmit">
          确认提现
        </van-button>
      </div>
    </van-skeleton>
  </div>
</template>

<style scoped>
.apply-summary {
  text-align: center;
  background: var(--h5-gradient);
  color: #fff;
}
.apply-summary__label { font-size: 13px; opacity: 0.85; }
.apply-summary__amount { font-size: 32px; font-weight: 700; margin: 8px 0; }
.apply-summary__min { font-size: 12px; opacity: 0.7; }

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
}

.apply-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--h5-divider);
}
.apply-item:last-child { border-bottom: none; }
.apply-item__info { flex: 1; min-width: 0; }
.apply-item__name { font-size: 14px; color: var(--h5-text-primary); }
.apply-item__type { font-size: 11px; color: var(--h5-text-secondary); margin-top: 2px; }
.apply-item__amount { font-size: 15px; font-weight: 600; color: var(--h5-primary); white-space: nowrap; }

.card-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--h5-divider);
}
.card-item:last-child { border-bottom: none; }
.card-item__bank { font-size: 14px; margin-right: 8px; }
.card-item__number { font-size: 13px; color: var(--h5-text-secondary); }

.new-card-form {
  border: 1px solid var(--h5-border);
  border-radius: 8px;
  overflow: hidden;
}

.apply-bottom {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--h5-card-bg);
  box-shadow: 0 -2px 8px rgba(0,0,0,0.04);
  z-index: 10;
}
.apply-bottom__info { font-size: 13px; color: var(--h5-text-secondary); }
.apply-bottom__total { margin-left: 12px; color: var(--h5-text-primary); }
.apply-bottom__total b { color: var(--h5-primary); font-size: 16px; }
</style>
