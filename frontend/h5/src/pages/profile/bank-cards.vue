<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { getMyCards, addCard, deleteCard, setDefaultCard, type BankCard } from '@/api/withdrawal'
import { applyDevBankCardOverrides } from '@/api/mock'
import { maskCardNumber } from '@/utils/format'

defineOptions({ name: 'BankCards' })

const loading = ref(true)
const loadError = ref('')
const cards = ref<BankCard[]>([])
const showAdd = ref(false)
const submitting = ref(false)
const newCard = ref({ accountName: '', cardNumber: '', bankName: '', branchName: '' })

onMounted(loadCards)

async function loadCards() {
  loading.value = true
  loadError.value = ''
  try {
    cards.value = applyDevBankCardOverrides(await getMyCards())
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '银行卡加载失败'
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  if (!newCard.value.accountName.trim()) { showToast('请输入户名'); return }
  if (!newCard.value.cardNumber.trim()) { showToast('请输入卡号'); return }
  if (!newCard.value.bankName.trim()) { showToast('请输入银行名称'); return }

  submitting.value = true
  try {
    await addCard({
      accountName: newCard.value.accountName.trim(),
      cardNumber: newCard.value.cardNumber.trim(),
      bankName: newCard.value.bankName.trim(),
      branchName: newCard.value.branchName.trim()
    })
    showSuccessToast('添加成功')
    showAdd.value = false
    newCard.value = { accountName: '', cardNumber: '', bankName: '', branchName: '' }
    await loadCards()
  } catch { /* */ } finally { submitting.value = false }
}

async function handleDelete(card: BankCard) {
  try {
    await showConfirmDialog({ title: '删除银行卡', message: `确定删除 ${card.bankName} ${card.maskedCardNumber}？` })
    await deleteCard(card.id)
    showSuccessToast('已删除')
    await loadCards()
  } catch { /* */ }
}

async function handleSetDefault(card: BankCard) {
  if (card.defaultCard) return
  try {
    await setDefaultCard(card.id)
    showSuccessToast('已设为默认')
    await loadCards()
  } catch { /* */ }
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="银行卡管理" left-arrow @click-left="$router.back()">
      <template #right>
        <van-icon name="plus" size="20" @click="showAdd = true" />
      </template>
    </van-nav-bar>

    <van-skeleton :loading="loading" :row="4" style="padding: 16px;">
      <van-empty v-if="loadError" :description="loadError" image="error"><van-button size="small" type="primary" @click="loadCards">重新加载</van-button></van-empty>
      <div v-else-if="cards.length === 0 && !loading" style="text-align: center; padding: 60px 20px;">
        <van-empty description="暂无银行卡">
          <van-button type="primary" round size="small" @click="showAdd = true">添加银行卡</van-button>
        </van-empty>
      </div>

      <div v-for="card in loadError ? [] : cards" :key="card.id" class="card bank-card-item">
        <div class="bank-card-item__header">
          <span class="bank-card-item__bank">{{ card.bankName }}</span>
          <van-tag v-if="card.defaultCard" type="primary" size="medium">默认</van-tag>
        </div>
        <div class="bank-card-item__number">{{ card.maskedCardNumber }}</div>
        <div class="bank-card-item__name">{{ card.accountName }}</div>
        <div class="bank-card-item__actions">
          <van-button size="mini" plain icon="edit" @click="$router.push(`/profile/bank-cards/${card.id}/edit`)">编辑</van-button>
          <van-button v-if="!card.defaultCard" size="mini" plain @click="handleSetDefault(card)">设为默认</van-button>
          <van-button size="mini" plain type="danger" @click="handleDelete(card)">删除</van-button>
        </div>
      </div>
    </van-skeleton>

    <!-- 添加银行卡弹窗 -->
    <van-popup v-model:show="showAdd" position="bottom" round :style="{ padding: '16px' }">
      <div style="font-size: 16px; font-weight: 500; margin-bottom: 16px;">添加银行卡</div>
      <van-field v-model="newCard.accountName" label="户名" placeholder="持卡人姓名" required />
      <van-field v-model="newCard.cardNumber" label="卡号" type="digit" placeholder="银行卡号" required />
      <van-field v-model="newCard.bankName" label="银行" placeholder="开户银行" required />
      <van-field v-model="newCard.branchName" label="支行" placeholder="开户支行（选填）" />
      <van-button type="primary" block round :loading="submitting" style="margin-top: 16px;" @click="handleAdd">确认添加</van-button>
    </van-popup>
  </div>
</template>

<style scoped>
.bank-card-item {
  margin: 12px 16px;
  padding: 16px;
}
.bank-card-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.bank-card-item__bank {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
}
.bank-card-item__number {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--h5-text-primary);
  margin: 4px 0;
}
.bank-card-item__name {
  font-size: 13px;
  color: var(--h5-text-secondary);
}
.bank-card-item__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--h5-divider);
}
</style>
