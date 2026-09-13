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
const savingCard = ref(false)

// Data
const summary = ref<WithdrawalSummary>()
const availableItems = ref<CashbackItem[]>([])
const bankCards = ref<BankCard[]>([])

// Form
const selectedIds = ref<Set<number>>(new Set())
const selectedCardId = ref<number>()
const showNewCard = ref(false)
const newCard = ref({ accountName: '', cardNumber: '', bankName: '', branchName: '' })

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
    showNewCard.value = cardsData.length === 0
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
const partiallySelected = computed(() =>
  !allSelected.value && availableItems.value.some(item => selectedIds.value.has(item.id))
)

function setAllSelected(checked: boolean) {
  selectedIds.value = checked
    ? new Set(availableItems.value.map(item => item.id))
    : new Set()
}

function setItemSelected(id: number, checked: boolean) {
  const nextSelectedIds = new Set(selectedIds.value)
  if (checked) {
    nextSelectedIds.add(id)
  } else {
    nextSelectedIds.delete(id)
  }
  selectedIds.value = nextSelectedIds
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

function validateNewCard() {
  if (!newCard.value.accountName.trim()) { showToast('请输入户名'); return false }
  if (!newCard.value.cardNumber.trim()) { showToast('请输入卡号'); return false }
  if (!newCard.value.bankName.trim()) { showToast('请输入银行名称'); return false }
  return true
}

async function handleSaveCard() {
  if (savingCard.value || !validateNewCard()) return
  savingCard.value = true
  try {
    const cardId = await addCard({
      accountName: newCard.value.accountName.trim(),
      cardNumber: newCard.value.cardNumber.trim(),
      bankName: newCard.value.bankName.trim(),
      branchName: newCard.value.branchName.trim()
    })
    bankCards.value = await getMyCards()
    selectedCardId.value = cardId
    showNewCard.value = false
    newCard.value = { accountName: '', cardNumber: '', bankName: '', branchName: '' }
    showSuccessToast('银行卡已保存')
  } catch {
    // 拦截器已处理
  } finally {
    savingCard.value = false
  }
}

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
      params.saveCard = false
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
  <div class="page-container withdrawal-apply-page">
    <van-nav-bar title="申请提现" left-arrow @click-left="$router.back()" />

    <van-skeleton :loading="loading" :row="8" style="padding: 16px;">
      <!-- 汇总 -->
      <div class="card apply-summary">
        <div class="apply-summary__label">可提现金额</div>
        <div class="apply-summary__amount">¥{{ formatAmount(summary?.availableAmount) }}</div>
        <div class="apply-summary__min">最低提现：¥{{ formatAmount(summary?.minimumAmount) }}</div>
      </div>

      <!-- 可提现条目 -->
      <div class="card selection-card">
        <div class="section-header">
          <span class="section-title">选择提现项目</span>
          <van-checkbox
            class="select-all"
            :model-value="allSelected"
            :indeterminate="partiallySelected"
            :disabled="availableItems.length === 0"
            icon-size="18"
            @update:model-value="setAllSelected"
          >
            全选
          </van-checkbox>
        </div>
        <div v-if="availableItems.length === 0" style="text-align: center; padding: 20px; color: var(--h5-text-placeholder);">
          暂无可提现项目
        </div>
        <van-checkbox
          v-for="item in availableItems"
          :key="item.id"
          class="apply-item"
          :class="{ 'apply-item--selected': selectedIds.has(item.id) }"
          :model-value="selectedIds.has(item.id)"
          icon-size="18"
          @update:model-value="setItemSelected(item.id, $event)"
        >
          <div class="apply-item__info">
            <div class="apply-item__name">{{ item.productNameSnapshot }}</div>
            <div class="apply-item__type">{{ item.type === 'valid' ? '有效返现' : '成交返现' }}</div>
          </div>
          <div class="apply-item__amount">+¥{{ formatAmount(item.amount) }}</div>
        </van-checkbox>
      </div>

      <!-- 银行卡选择 -->
      <div class="card bank-card-section">
        <div class="section-header">
          <span class="section-title">收款银行卡</span>
          <van-button
            v-if="!showNewCard && bankCards.length > 0"
            class="section-action"
            size="small"
            type="primary"
            plain
            round
            icon="plus"
            @click="showNewCard = true"
          >
            添加新卡
          </van-button>
        </div>

        <van-radio-group v-if="!showNewCard" v-model="selectedCardId" class="bank-card-options">
          <van-radio
            v-for="card in bankCards"
            :key="card.id"
            :name="card.id"
            class="bank-card-option"
            :class="{ 'bank-card-option--selected': selectedCardId === card.id }"
            icon-size="18"
          >
            <div class="bank-card-option__content">
              <div class="bank-card-option__head">
                <span class="bank-card-option__bank">{{ card.bankName }}</span>
                <van-tag v-if="card.defaultCard" type="primary" plain>默认</van-tag>
              </div>
              <div class="bank-card-option__number">{{ card.maskedCardNumber }}</div>
            </div>
          </van-radio>
        </van-radio-group>

        <!-- 新银行卡表单 -->
        <div v-if="showNewCard" class="new-card-form">
          <div class="new-card-fields">
            <van-field v-model="newCard.accountName" label="开户名" placeholder="请输入持卡人姓名" required />
            <van-field v-model="newCard.cardNumber" label="银行卡号" type="digit" placeholder="请输入银行卡号" required />
            <van-field v-model="newCard.bankName" label="开户银行" placeholder="请输入开户银行" required />
            <van-field v-model="newCard.branchName" label="开户支行" placeholder="选填" />
          </div>
          <van-button
            class="save-card-button"
            type="primary"
            block
            round
            :loading="savingCard"
            @click="handleSaveCard"
          >
            保存并使用此卡
          </van-button>
          <van-button
            v-if="bankCards.length > 0"
            class="bank-card-back"
            size="small"
            plain
            round
            icon="arrow-left"
            @click="showNewCard = false"
          >
            返回已保存银行卡
          </van-button>
        </div>
      </div>

      <!-- 底部提交 -->
      <div class="apply-bottom safe-area-bottom">
        <div class="apply-bottom__info">
          <span>已选 {{ selectedIds.size }} 项</span>
          <span class="apply-bottom__total">合计：<b>¥{{ formatAmount(selectedAmount) }}</b></span>
        </div>
        <van-button type="primary" round :disabled="!canSubmit" :loading="submitting" @click="handleSubmit">
          立即申请
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
  min-height: 30px;
  margin-bottom: 8px;
  gap: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 500;
  line-height: 21px;
  color: var(--h5-text-primary);
}
.select-all {
  flex: 0 0 auto;
  min-height: 32px;
  padding-left: 10px;
}
.select-all :deep(.van-checkbox__label) {
  margin-left: 7px;
  font-size: 14px;
  line-height: 20px;
}

.apply-item {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 64px;
  padding: 10px 8px;
  border-bottom: 1px solid var(--h5-divider);
  border-radius: 8px;
  transition: background-color 0.15s ease;
}
.apply-item:last-child { border-bottom: none; }
.apply-item--selected {
  background: color-mix(in srgb, var(--h5-primary) 8%, transparent);
}
.apply-item :deep(.van-checkbox__label) {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}
.apply-item__info { flex: 1; min-width: 0; }
.apply-item__name {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 14px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.apply-item__type { font-size: 11px; color: var(--h5-text-secondary); margin-top: 2px; }
.apply-item__amount { margin-left: 12px; font-size: 15px; font-weight: 600; color: var(--h5-primary); white-space: nowrap; }

.section-action {
  flex: 0 0 auto;
  min-height: 38px;
  padding: 0 14px;
  border-color: color-mix(in srgb, var(--h5-primary) 48%, var(--h5-border));
  background: color-mix(in srgb, var(--h5-primary-light) 78%, transparent);
  color: var(--h5-primary);
  font-size: 13px;
}
.bank-card-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bank-card-option {
  width: 100%;
  min-height: 64px;
  padding: 11px 12px;
  border: 1px solid var(--h5-border);
  border-radius: 12px;
  background: var(--h5-glass-sunken);
}
.bank-card-option--selected {
  border-color: color-mix(in srgb, var(--h5-primary) 45%, var(--h5-border));
  background: color-mix(in srgb, var(--h5-primary) 9%, var(--h5-glass-surface-strong));
}
.bank-card-option :deep(.van-radio__label) {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}
.bank-card-option__content { min-width: 0; }
.bank-card-option__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bank-card-option__bank {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bank-card-option__number {
  margin-top: 4px;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 13px;
  line-height: 18px;
  letter-spacing: 1px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-card-form {
  margin-top: 2px;
}
.new-card-fields :deep(.van-cell) {
  padding: 13px 4px;
  background: transparent;
}
.new-card-fields :deep(.van-cell::after) {
  right: 4px;
  left: 4px;
  border-color: var(--h5-divider);
}
.new-card-fields :deep(.van-field__label) {
  width: 82px;
  color: var(--h5-text-primary);
  font-size: 14px;
}
.new-card-fields :deep(.van-field__control) {
  font-size: 14px;
}
.save-card-button {
  margin-top: 12px;
}
.bank-card-back {
  display: block;
  min-height: 40px;
  margin-right: auto;
  margin-left: 0;
  padding: 0 14px;
  margin-top: 4px;
  border-color: var(--h5-border);
  background: transparent;
  color: var(--h5-text-secondary);
  font-size: 13px;
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
  border-top: 1px solid color-mix(in srgb, var(--h5-text-secondary) 12%, transparent);
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  z-index: 10;
}
.withdrawal-apply-page :deep(.van-nav-bar) {
  border-bottom-color: color-mix(in srgb, var(--h5-text-secondary) 12%, transparent);
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.apply-bottom__info { font-size: 13px; color: var(--h5-text-secondary); }
.apply-bottom__total { margin-left: 12px; color: var(--h5-text-primary); }
.apply-bottom__total b { color: var(--h5-primary); font-size: 16px; }
</style>
