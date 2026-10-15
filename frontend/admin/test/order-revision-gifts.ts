import { createApp, h } from 'vue'
import { createRouter, createWebHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import OrderManagement from '../src/views/zsjos/mySalesOrder/index.vue'
const router = createRouter({ history: createWebHistory(), routes: [{ path: '/test/order-revision-gifts.html', component: OrderManagement }] })
const app = createApp({ render: () => h(RouterView) })
app.directive('hasPermi', { mounted(el) {
  if (new URLSearchParams(location.search).get('scenario') === 'denied') el.remove()
} })
app.use(ElementPlus).use(router)
await router.isReady()
app.mount('#app')
