<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { queryPublicPayment } from '@/api/payment'

const state = ref<'checking' | 'paid' | 'unknown'>('checking')
let timer: number | undefined
let attempts = 0

const poll = async () => {
  const raw = sessionStorage.getItem('zsjos-payment-session')
  if (!raw) { state.value = 'unknown'; return }
  try {
    const session = JSON.parse(raw) as { no: string; token: string }
    if (await queryPublicPayment(session.no, session.token)) { state.value = 'paid'; return }
  } catch { /* 查询失败继续等待，不能据此认定支付失败。 */ }
  attempts += 1
  if (attempts >= 20) { state.value = 'unknown'; return }
  timer = window.setTimeout(poll, 3000)
}

onMounted(poll)
onBeforeUnmount(() => timer && window.clearTimeout(timer))
</script>

<template>
  <main class="result-page">
    <section v-if="state === 'paid'" class="result-state"><van-icon name="checked" color="#07c160" size="64"/><h1>支付已确认</h1><p>销售稍后会继续为您处理订单</p></section>
    <van-loading v-else-if="state === 'checking'" class="checking" vertical>正在确认支付结果</van-loading>
    <section v-else class="result-state"><van-icon name="clock-o" color="#ff976a" size="64"/><h1>结果待确认</h1><p>请勿重复支付，可稍后重新打开支付链接查看</p></section>
  </main>
</template>

<style scoped>
.result-page{min-height:100vh;background:transparent;padding-top:72px}.checking{padding-top:96px}.result-state{margin:0 16px;padding:72px 24px;border:1px solid var(--h5-glass-border);border-radius:16px;background:var(--h5-glass-surface-strong);box-shadow:var(--h5-glass-shadow);backdrop-filter:saturate(160%) blur(var(--h5-glass-blur));-webkit-backdrop-filter:saturate(160%) blur(var(--h5-glass-blur));text-align:center}.result-state h1{font-size:22px;margin:20px 0 8px}.result-state p{color:var(--h5-text-secondary);margin:0}
</style>
