<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { getMyCards, updateCard, type BankCard } from '@/api/withdrawal'

defineOptions({ name: 'BankCardEdit' })
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const card = ref<BankCard>()
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const changeNumber = ref(false)
const form = reactive({ accountName: '', bankName: '', branchName: '', cardNumber: '' })

async function loadCard() {
  loading.value = true; loadError.value = ''
  try {
    const cards = await getMyCards()
    const current = cards.find(item => item.id === id)
    if (!current) throw new Error('未找到该银行卡')
    card.value = current
    Object.assign(form, { accountName: current.accountName, bankName: current.bankName, branchName: current.branchName || '', cardNumber: '' })
  } catch (cause) { loadError.value = cause instanceof Error ? cause.message : '银行卡加载失败' }
  finally { loading.value = false }
}

async function submit() {
  if (!form.accountName.trim()) { showToast('请输入开户名'); return }
  if (!form.bankName.trim()) { showToast('请输入开户银行'); return }
  if (changeNumber.value && !/^\d+$/.test(form.cardNumber)) { showToast('请输入完整银行卡号'); return }
  if (changeNumber.value) {
    try { await showConfirmDialog({ title: '确认更换卡号', message: `新卡号尾号 ${form.cardNumber.slice(-4)}，历史提现记录中的银行卡信息不会改变。` }) } catch { return }
  }
  submitting.value = true
  try {
    await updateCard(id, { accountName: form.accountName.trim(), bankName: form.bankName.trim(), branchName: form.branchName.trim(), ...(changeNumber.value ? { cardNumber: form.cardNumber } : {}) })
    showSuccessToast('保存成功')
    router.back()
  } finally { submitting.value = false }
}

onMounted(loadCard)
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="编辑银行卡" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="6" style="padding:16px">
      <van-empty v-if="loadError" :description="loadError" image="error"><van-button size="small" type="primary" @click="loadCard">重新加载</van-button></van-empty>
      <van-form v-else-if="card" @submit="submit">
        <van-cell-group inset>
          <van-field v-model="form.accountName" label="开户名" required maxlength="64" placeholder="持卡人姓名" />
          <van-field label="当前卡号" :model-value="card.maskedCardNumber" readonly />
          <van-cell title="更换银行卡号"><template #value><van-switch v-model="changeNumber" size="22" @change="form.cardNumber = ''" /></template></van-cell>
          <van-field v-if="changeNumber" v-model="form.cardNumber" label="新卡号" type="digit" required placeholder="请输入完整银行卡号" />
          <van-field v-model="form.bankName" label="开户银行" required maxlength="128" placeholder="银行名称" />
          <van-field v-model="form.branchName" label="开户支行" maxlength="128" placeholder="开户支行（选填）" />
        </van-cell-group>
        <div class="bank-tip"><van-icon name="shield-o" /> 平台不会要求填写银行卡密码、验证码或 CVV</div>
        <div class="submit-wrap"><van-button block round type="primary" native-type="submit" :loading="submitting">保存修改</van-button></div>
      </van-form>
    </van-skeleton>
  </div>
</template>

<style scoped>
.bank-tip{display:flex;align-items:center;gap:6px;padding:14px 24px;color:var(--h5-text-secondary);font-size:12px}.submit-wrap{padding:16px}
</style>
