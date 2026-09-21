// UTF-8. Isolated component rendering; no business requests are permitted.
import { createApp, h, ref, onMounted } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createRouter, createMemoryHistory } from 'vue-router'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { service } from '../src/config/axios/service'
service.defaults.adapter = async () => { throw new Error('Fixture prohibits business requests') }
const bootstrap = createApp({}); setupStore(bootstrap)
const { default: Actions } = await import('../src/views/bpm/processInstance/detail/ProcessInstanceOperationButton.vue')
const Page = { setup() {
 const review = ref(), other = ref()
 onMounted(() => { for (const target of [review, other]) target.value.loadTodoTask({ id: 'fixture', status: 1, children: [{ id: 'child' }] }) })
 const props = (key: string) => ({ processInstance: { id: 'fixture', status: 1, businessKey: key }, processDefinition: {}, userOptions: [], normalForm: {}, normalFormApi: {}, writableFields: [] })
 return () => h('main', [h('section', { 'aria-label': '内容审核动作' }, [h('h2', '内容审核'), h(Actions, { ...props('content-review-batch:1'), ref: review })]), h('section', { 'aria-label': '其他审批动作' }, [h('h2', '其他审批'), h(Actions, { ...props('other:1'), ref: other })])])
} }
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Page }] })
const app = createApp(Page); setupStore(app); await setupI18n(app); app.use(ElementPlus); app.use(router)
app.component('Icon', { render: () => h('span') })
await router.push('/'); await router.isReady(); app.mount('#app')
