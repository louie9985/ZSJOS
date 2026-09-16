import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import { parse, compileScript } from 'vue/compiler-sfc'
import { renderToString } from 'vue/server-renderer'
import * as vue from 'vue'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const source = readFileSync(new URL('../src/pages/payment/index.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = compileScript(descriptor, { id: 'payment-page-test', inlineTemplate: true }).content
const code = ts.transpileModule(compiled, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const item = { productName: '测试课程', skuName: '高级班', actualAmount: 680,
  specs: [{ attrKey: 'mode', attrName: '授课方式', value: 'online', label: '线上授课' }] }

async function render({ detail = { amount: 1234.50, status: 'created', items: [item] }, error,
  token = 'fixture-token', wechat = false } = {}) {
  const mounted = [], redirects = [], requests = []
  const exports = {}
  const dependencies = {
    vue: { ...vue, onMounted: callback => mounted.push(callback) },
    'vue-router': { useRoute: () => ({ params: { paymentIntentNo: 'fixture' }, query: { token } }),
      useRouter: () => ({ replace: path => redirects.push(path) }) },
    vant: { showFailToast() {} },
    '@/api/payment': { getPublicPayment: async (...args) => { requests.push(args); if (error) throw error; return detail },
      wechatOrderAction: no => `/public-api/zsjos/payment/${no}/order`, createAlipayOrder() { throw new Error('Unexpected payment') } },
  }
  vm.runInNewContext(code, { exports, Error, require: name => dependencies[name] || require(name),
    navigator: { userAgent: wechat ? 'MicroMessenger' : 'Browser' }, sessionStorage: { setItem() {} } })
  const component = exports.default
  const setup = component.setup
  component.setup = async (...args) => {
    const result = setup(...args)
    await Promise.all(mounted.map(callback => callback()))
    return result
  }
  const app = vue.createSSRApp(component)
  for (const name of ['van-nav-bar', 'van-loading', 'van-empty', 'van-button', 'van-notice-bar']) {
    app.component(name, { props: ['description', 'text', 'title'], setup: (props, { slots }) =>
      () => vue.h(name === 'van-button' ? 'button' : 'div', {}, [props.description, props.text, props.title, slots.default?.()]) })
  }
  return { html: await renderToString(app), redirects, requests }
}

test('shows server payable amount, all transaction prices and snapshot labels without SKU prefix', async () => {
  const { html } = await render({ detail: { amount: 1234.50, status: 'created', items: [item, { ...item, productName: '第二课程' }] } })
  assert.match(html, /1,234\.50/)
  assert.match(html, /¥680\.00/)
  assert.match(html, /测试课程/)
  assert.match(html, /第二课程/)
  assert.match(html, /高级班/)
  assert.match(html, /线上授课/)
  assert.doesNotMatch(html, /SKU[：:]/)
  assert.match(html, /https:\/\/www\.zsjedc\.com\/logo\.png/)
})

test('renders legacy missing labels honestly and never renders an internal reference from description', async () => {
  const { html } = await render({ detail: { amount: 680, status: 'created', description: 'INTERNAL-SKU-ID', items: [{ actualAmount: 680 }] } })
  assert.match(html, /产品名称未记录/)
  assert.match(html, /规格信息未记录/)
  assert.doesNotMatch(html, /INTERNAL-SKU-ID/)
  const empty = await render({ detail: { amount: 680, status: 'created' } })
  assert.match(empty.html, /未记录商品明细/)
})

test('expired and closed links show notice without payment actions', async () => {
  for (const [status, notice] of [['expired', '支付链接已失效'], ['closed', '支付链接已取消']]) {
    const { html } = await render({ detail: { amount: 680, status, items: [item] }, wechat: true })
    assert.match(html, new RegExp(notice))
    assert.doesNotMatch(html, /微信支付|支付宝支付|<form/)
  }
})

test('retains backend failure message and provides retry, but invalid links never request data', async () => {
  const failed = await render({ error: new Error('支付渠道暂不可用') })
  assert.match(failed.html, /支付渠道暂不可用/)
  assert.match(failed.html, /重新加载/)
  const invalid = await render({ token: '' })
  assert.match(invalid.html, /支付链接无效/)
  assert.equal(invalid.requests.length, 0)
  assert.doesNotMatch(invalid.html, /重新加载/)
})

test('keeps existing browser-specific payment entries and paid redirect', async () => {
  const wechat = await render({ wechat: true })
  assert.match(wechat.html, /微信支付/)
  assert.match(wechat.html, /method="post"/)
  const browser = await render()
  assert.doesNotMatch(browser.html, /微信支付/)
  assert.match(browser.html, /支付宝支付/)
  const paid = await render({ detail: { amount: 680, status: 'paid', items: [item] } })
  assert.deepEqual(paid.redirects, ['/payment-result'])
})

test('escapes product text and marks missing historical specification labels', async () => {
  const { html } = await render({ detail: { amount: 680, status: 'created', items: [{ ...item,
    productName: '<script>alert(1)</script>', specs: [{ label: 'old-value', labelMissing: true }] }] } })
  assert.match(html, /&lt;script&gt;/)
  assert.doesNotMatch(html, /<script>/)
  assert.match(html, /old-value（历史标签缺失）/)
})
