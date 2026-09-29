import 'amfe-flexible'
// UTF-8. Isolated Partner earnings page with synthetic API data.
import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import 'vant/lib/index.css'
import '../src/styles/base.css'
import '../src/styles/themes/sky.css'
import '../src/styles/vant-overrides.css'
const cap = () => { document.documentElement.style.fontSize = `${Math.min(innerWidth / 10, 54)}px` }; cap(); window.addEventListener('resize', cap)
const pinia = createPinia(); setActivePinia(pinia)
async function mount() {
 const { default: request } = await import('../src/api/request')
 request.defaults.adapter = async config => {
  const row = { id: 1, leadId: 1, leadNo: 'L-TEST-01', version: 1, cashbackNo: 'CB-H5-TEST', type: 'valid', status: 'blocked', amount: 20, observationDaysSnapshot: 7, productNameSnapshot: '测试课程', blockReason: '核实后暂不可提现', generatedAt: '2026-09-27T09:00:00' }
  const data = config.url?.includes('my-summary') ? { totalAmount: 20, availableAmount: 0, pendingAmount: 0, withdrawingAmount: 0, withdrawnAmount: 0, counts: { blocked: 1 } } : { list: [row], total: 1 }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
 }
 const { useUserStore } = await import('../src/stores/user'); useUserStore().setUserInfo({ userId: 999, nickname: '验收兼职', permissions: ['zsjos:withdrawal:apply','zsjos:cashback:my-query'] })
 const { default: Page } = await import('../src/pages/earnings/index.vue')
 createApp(Page).use(pinia).use(createRouter({ history: createMemoryHistory(), routes: [] })).mount('#app')
}
void mount()
