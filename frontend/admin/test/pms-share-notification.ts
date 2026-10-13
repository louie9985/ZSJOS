import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Links from '../src/views/system/notify/my/PmsShareMessageLinks.vue'
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })
createApp({ render: () => h(Links, { params: { publicUrl: 'https://public.example.test/public-api/pms/kb/document-share/page?token=fixture', documentRoute: '/pms/kb/library/1/document/2' } }) }).use(router).use(ElementPlus).mount('#app')
