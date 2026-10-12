import { createApp } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import 'amfe-flexible'
import '../src/styles/base.css'
import '../src/styles/themes/coral.css'
import '../src/styles/vant-overrides.css'
import Appeal from '../src/pages/lead/appeal.vue'
import request from '../src/api/request'

const mode = new URLSearchParams(location.search).get('mode') ?? 'new'
request.defaults.adapter = async config => {
  let data: unknown = null
  if (config.url?.endsWith('/context')) data = { invalidEventId: 20, nextRoundNo: mode === 'new' ? 1 : 2,
    canSubmit: mode === 'new' || mode === 'next', records: [{ id: 1, leadId: 1, invalidEventId: 10,
      currentCycle: false, roundNo: 3, reviewStage: 'chairman', status: 'overturned', reason: '历史申诉',
      submittedAt: Date.now(), canSubmitNextRound: false }] }
  if (config.url?.endsWith('/submit')) (window as unknown as { submitted: unknown }).submitted = JSON.parse(config.data)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/lead/:id/appeal', component: Appeal }] })
await router.push('/lead/1/appeal')
createApp(Appeal).use(createPinia()).use(router).use(Vant).mount('#app')
