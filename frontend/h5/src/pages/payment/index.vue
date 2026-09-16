<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { createAlipayOrder, getPublicPayment, wechatOrderAction, type PublicPaymentDetail } from '@/api/payment'
import type { ProductSpec } from '@/utils/productSpecs'

const route = useRoute()
const router = useRouter()
const no = String(route.params.paymentIntentNo || '')
const token = String(route.query.token || '')
const detail = ref<PublicPaymentDetail>()
const loading = ref(true)
const paying = ref(false)
const error = ref('')
const logoFailed = ref(false)
const inWechat = /MicroMessenger/i.test(navigator.userAgent)
const expired = computed(() => ['expired', 'closed'].includes(detail.value?.status || ''))
// 销售主动取消后旧链接作废，客户再打开时给出明确指引，避免反复尝试支付
const invalidNotice = computed(() => detail.value?.status === 'closed'
  ? '该支付链接已取消，请联系销售获取新链接'
  : '该支付链接已失效，请联系销售重新生成')
const items = computed(() => detail.value?.items || [])
const formatAmount = (value: number) => Number(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2, maximumFractionDigits: 2,
})
const specificationText = (specs?: ProductSpec[] | null) => (specs || [])
  .map(spec => `${spec.label}${spec.labelMissing ? '（历史标签缺失）' : ''}`).join(' · ')

const loadPayment = async () => {
  loading.value = true
  error.value = ''
  if (!no || !token) { error.value = '支付链接无效'; loading.value = false; return }
  try {
    detail.value = await getPublicPayment(no, token)
    sessionStorage.setItem('zsjos-payment-session', JSON.stringify({ no, token }))
    if (detail.value.status === 'paid') void router.replace('/payment-result')
  } catch (e) { error.value = e instanceof Error ? e.message : '支付信息加载失败' }
  finally { loading.value = false }
}

onMounted(loadPayment)

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
    <div class="payment-content">
      <header class="brand">
        <img v-if="!logoFailed" class="brand-logo" src="https://www.zsjedc.com/logo.png"
          alt="中世健" referrerpolicy="no-referrer" @error="logoFailed = true" />
        <span v-else class="brand-name">中世健</span>
      </header>
      <van-loading v-if="loading" class="center-state" vertical>正在加载支付信息</van-loading>
      <van-empty v-else-if="error" image="error" :description="error">
        <van-button v-if="no && token" type="primary" size="small" @click="loadPayment">重新加载</van-button>
      </van-empty>
      <section v-else-if="detail" aria-label="支付订单详情">
        <div class="payment-total">
          <p class="amount-label">本次应付</p>
          <div class="amount"><span>¥</span>{{ formatAmount(detail.amount) }}</div>
        </div>
        <section class="products" aria-labelledby="products-title">
          <h2 id="products-title">成交产品</h2>
          <ul v-if="items.length" class="product-list">
            <li v-for="(item, index) in items" :key="index" class="product-item">
              <h3>{{ item.productName || '产品名称未记录' }}</h3>
              <p v-if="item.skuName" class="product-sku">{{ item.skuName }}</p>
              <p v-if="specificationText(item.specs) && specificationText(item.specs) !== item.skuName"
                class="product-specs">{{ specificationText(item.specs) }}</p>
              <p v-if="!item.skuName && !item.specs?.length" class="product-specs">规格信息未记录</p>
              <div class="product-price"><span>成交价格</span><strong>¥{{ formatAmount(item.actualAmount) }}</strong></div>
            </li>
          </ul>
          <p v-else class="missing-products">该支付单未记录商品明细，请联系销售确认</p>
        </section>
        <van-notice-bar v-if="expired" class="expired-notice" wrapable :scrollable="false"
          :text="invalidNotice" />
        <div v-else class="payment-actions">
          <form v-if="inWechat" :action="wechatOrderAction(no)" method="post">
            <input type="hidden" name="token" :value="token" />
            <input type="hidden" name="channel" value="wechat" />
            <van-button native-type="submit" block color="#07a85a" icon="wechat">微信支付</van-button>
          </form>
          <van-button block class="alipay-button" icon="alipay" :loading="paying" @click="payAlipay">支付宝支付</van-button>
          <p v-if="inWechat" class="hint">支付宝支付请在外部浏览器打开当前链接</p>
        </div>
      </section>
      <van-empty v-else description="暂无支付信息，请重新打开支付链接" />
    </div>
  </main>
</template>

<style scoped>
.payment-page {
  /* Public checkout keeps the confirmed brand colors independent of a partner's personal theme. */
  --h5-primary-light: #eaf2ff;
  --h5-primary-dark: #1765c1;
  --h5-text-primary: #17263a;
  --h5-text-secondary: #657386;
  --h5-glass-surface-strong: #fff;
  --h5-glass-border: #eef2f8;
  --h5-glass-divider: #edf0f4;
  min-height: 100vh;
  color: var(--h5-text-primary);
  background: linear-gradient(180deg, var(--h5-primary-light), #f6f8fb 440px);
}
.payment-content { max-width: 480px; margin: 0 auto; padding: 32px 20px calc(32px + env(safe-area-inset-bottom)); }
.brand { display: flex; justify-content: center; align-items: center; min-height: 52px; margin-bottom: 28px; }
.brand-logo { width: 156px; height: 52px; object-fit: contain; }
.brand-name { font-size: 24px; font-weight: 600; color: var(--h5-primary-dark); }
.payment-total { text-align: center; margin-bottom: 30px; }
.amount-label { margin: 0 0 10px; font-size: 14px; color: var(--h5-text-secondary); }
.amount { font-size: 42px; font-weight: 700; line-height: 1.25; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.amount span { font-size: 24px; margin-right: 5px; font-weight: 500; }
.products { padding: 22px 20px; border: 1px solid var(--h5-glass-border); border-radius: 16px; background: var(--h5-glass-surface-strong); box-shadow: var(--h5-glass-shadow); }
.products h2 { margin: 0 0 16px; font-size: 13px; font-weight: 400; color: var(--h5-text-secondary); }
.product-list { margin: 0; padding: 0; list-style: none; }
.product-item + .product-item { margin-top: 22px; padding-top: 22px; border-top: 1px solid var(--h5-glass-divider); }
.product-item h3 { margin: 0; font-size: 17px; font-weight: 600; line-height: 1.6; overflow-wrap: anywhere; }
.product-sku, .product-specs { margin: 8px 0 0; font-size: 13px; line-height: 1.8; color: var(--h5-text-secondary); overflow-wrap: anywhere; }
.product-price { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 8px; margin-top: 18px; font-size: 13px; color: var(--h5-text-secondary); }
.product-price strong { color: var(--h5-text-primary); font-size: 15px; font-weight: 500; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.missing-products { margin: 0; font-size: 14px; line-height: 1.8; color: var(--h5-text-secondary); }
.payment-actions { display: grid; gap: 12px; margin-top: 24px; }
.payment-actions :deep(.van-button) { height: 48px; border-radius: 10px; font-size: 15px; }
.alipay-button { --van-button-default-background: var(--h5-primary-light); --van-button-default-color: var(--h5-primary-dark); --van-button-default-border-color: transparent; }
.hint { margin: 0; text-align: center; color: var(--h5-text-secondary); font-size: 12px; line-height: 1.8; }
.expired-notice { margin-top: 24px; border-radius: 10px; }
.center-state { padding: 64px 0; }
</style>
