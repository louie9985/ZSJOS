<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { createAlipayOrder, getPublicPayment, wechatOrderAction, type PublicPaymentDetail } from '@/api/payment'

const route = useRoute()
const router = useRouter()
const no = String(route.params.paymentIntentNo || '')
const token = String(route.query.token || '')
const detail = ref<PublicPaymentDetail>()
const loading = ref(true)
const paying = ref(false)
const error = ref('')
const inWechat = /MicroMessenger/i.test(navigator.userAgent)
const expired = computed(() => ['expired', 'closed'].includes(detail.value?.status || ''))

onMounted(async () => {
  if (!no || !token) { error.value = '支付链接无效'; loading.value = false; return }
  try {
    detail.value = await getPublicPayment(no, token)
    sessionStorage.setItem('zsjos-payment-session', JSON.stringify({ no, token }))
    if (detail.value.status === 'paid') void router.replace('/payment-result')
  } catch (e) { error.value = e instanceof Error ? e.message : '支付信息加载失败' }
  finally { loading.value = false }
})

const payAlipay = async () => {
  if (inWechat) { showFailToast('请在浏览器中打开当前链接后使用支付宝'); return }
  paying.value = true
  try { window.location.assign(await createAlipayOrder(no, token)) }
  catch (e) { showFailToast(e instanceof Error ? e.message : '支付宝唤起失败') }
  finally { paying.value = false }
}
</script>

<template>
  <main class="payment-page">
    <van-nav-bar title="订单支付" />
    <van-loading v-if="loading" class="center-state" vertical>正在加载</van-loading>
    <van-empty v-else-if="error" image="error" :description="error" />
    <section v-else-if="detail" class="payment-content">
      <div class="merchant">中世健</div>
      <div class="amount"><span>¥</span>{{ Number(detail.amount).toFixed(2) }}</div>
      <div class="description">{{ detail.description }}</div>
      <van-notice-bar v-if="expired" color="#8a3d12" background="#fff2e8" text="该支付链接已失效，请联系销售重新生成" />
      <div v-else class="payment-actions">
        <form v-if="inWechat" :action="wechatOrderAction(no)" method="post">
          <input type="hidden" name="token" :value="token" />
          <input type="hidden" name="channel" value="wechat" />
          <van-button native-type="submit" block type="primary" icon="wechat">微信支付</van-button>
        </form>
        <van-button block color="#1677ff" icon="alipay" :loading="paying" @click="payAlipay">支付宝支付</van-button>
        <p v-if="inWechat" class="hint">支付宝支付请在外部浏览器打开当前链接</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.payment-page{min-height:100vh;background:#f6f7f9;color:#1f2329}.payment-content{padding:48px 20px 24px;text-align:center}.merchant{font-size:18px;font-weight:600}.amount{margin-top:26px;font-size:44px;font-weight:700}.amount span{font-size:22px;margin-right:4px}.description{margin:10px 0 36px;color:#646a73}.payment-actions{display:grid;gap:12px;max-width:420px;margin:0 auto}.hint{margin:2px 0;color:#8f959e;font-size:13px}.center-state{padding-top:120px}
</style>
